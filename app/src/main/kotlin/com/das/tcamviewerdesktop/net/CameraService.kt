package com.das.tcamviewerdesktop.net

import com.das.tcamviewerdesktop.constants.Constants
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.PublishSubject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger

/** Owns the TCP socket to the tCam and all I/O — ported from tcamViewer2's Android
 *  CameraService, minus the android.app.Service wrapper (no Binder/Intent needed on desktop). */
class CameraService {
    companion object {
        // Bounds how long a read() call blocks so the listening loop stays responsive to
        // disconnect()/stopListening() instead of sitting in a blocking syscall indefinitely —
        // important on the flaky WiFi links this app talks to, where the camera can go quiet
        // for a while (modem-sleep) without that meaning the connection actually died.
        private const val SOCKET_READ_TIMEOUT_MS = 12_000

        // While actively streaming, frames should arrive far more often than the read timeout
        // above — total silence for this many consecutive cycles means the camera vanished
        // without a graceful close, which a plain socket-error/EOF check alone won't detect: a
        // dead peer doesn't send a FIN/RST, so the socket just keeps timing out forever and
        // looks identical to a legitimately idle (not streaming) link.
        private const val MAX_CONSECUTIVE_READ_TIMEOUTS_WHILE_STREAMING = 2

        // The streaming check above only covers connections actively producing frames. A
        // connected-but-idle link (just Get, or nothing at all) can go silently dead the same
        // way — read() alone can't tell, since a dead peer never sends a FIN/RST — so poll it
        // with a real request/response every interval while idle.
        private const val IDLE_HEALTH_CHECK_INTERVAL_MS = 60_000L
        private const val IDLE_HEALTH_CHECK_TIMEOUT_MS = 5_000L

        private val log = Logger.getLogger(CameraService::class.java.name)
    }

    private var cameraSocket: Socket? = null
    private var isStreaming = false
    private var ipAddress: String? = null

    // java.net.Socket's own isConnected()/isClosed() only reflect whether connect()/close()
    // were ever called — they don't track whether the link is actually still alive. Track our
    // own flag, flipped the moment any read/write actually fails.
    @Volatile private var connectedFlag = false

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var listeningJob: Job? = null
    private var idleHealthCheckJob: Job? = null

    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()

    // Resolved with the next radiometric frame; used by getImageOnce() for time lapse capture
    @Volatile private var singleImageDeferred: CompletableDeferred<JSONObject>? = null

    @Volatile private var running = false
    private var bytesRead = 0
    private var responsePos = 0

    private var inFromSocket: InputStream? = null
    private var outToSocket: OutputStream? = null

    private var readBuffer = ByteArray(Constants.BUFFER_LENGTH)
    private var response = ByteArray(Constants.BUFFER_LENGTH)
    private var startFound = false

    private val imageChannel = PublishSubject.create<JSONObject>()

    // Emits when a previously-good connection dies on its own, as opposed to a deliberate
    // disconnect() — lets the ViewModel distinguish "user meant to disconnect" from "should
    // try to reconnect automatically."
    private val connectionLostSubject = PublishSubject.create<Unit>()

    fun getConnectionLostSignal(): Observable<Unit> = connectionLostSubject.hide()

    fun getImageChannel(): Observable<JSONObject> = imageChannel.hide()

    fun setIpAddress(address: String) {
        if (isConnected) disconnect()
        ipAddress = address
    }

    fun getIpAddress(): String? = ipAddress

    fun connect(): Boolean = runBlocking {
        running = false
        teardownConnection()
        listeningJob?.cancelAndJoin()
        listeningJob = null
        idleHealthCheckJob?.cancelAndJoin()
        idleHealthCheckJob = null
        resetBuffers()
        running = true
        val connected =
            withContext(Dispatchers.IO) {
                try {
                    val socket = Socket()
                    socket.connect(java.net.InetSocketAddress(ipAddress, 5001), 5000)
                    socket.soTimeout = SOCKET_READ_TIMEOUT_MS
                    socket.keepAlive = true
                    cameraSocket = socket
                    inFromSocket = socket.getInputStream()
                    outToSocket = socket.getOutputStream()
                    true
                } catch (e: Exception) {
                    log.log(Level.WARNING, "connect failed", e)
                    teardownConnection()
                    false
                }
            }

        connectedFlag = connected
        if (connected) {
            startListening()
            startIdleHealthCheck()
            // tCam-Mini has no battery-backed RTC, so it powers up with whatever time it last
            // had (or none at all) — push this machine's clock to it on every fresh connection
            // so saved images/recordings get sane timestamps. tCam itself has a battery-backed
            // RTC and just re-accepts the same time, so this is harmless there too.
            setTime()
        }
        connected
    }

    fun stopListening() {
        running = false
        listeningJob?.cancel()
    }

    /** Polls a connected-but-idle link with a real request/response every interval so a camera
     *  that silently vanished (no FIN/RST) doesn't sit "connected" forever — the counterpart to
     *  the streaming-side check in [startListening]. No-ops while streaming, since frames
     *  arriving is itself a much faster liveness signal than this could ever be. */
    private fun startIdleHealthCheck() {
        idleHealthCheckJob =
            serviceScope.launch {
                while (isConnected && running) {
                    delay(IDLE_HEALTH_CHECK_INTERVAL_MS)
                    if (!isConnected || !running || isStreaming) continue
                    val response = sendCmd(Constants.CMD_GET_STATUS, expectedKey = "status", timeoutMillis = IDLE_HEALTH_CHECK_TIMEOUT_MS)
                    if (response.has("error") && isConnected && running && !isStreaming) {
                        log.warning("Idle health check got no response — treating camera as disconnected")
                        running = false
                        teardownConnection()
                        failPendingRequests("Idle health check failed")
                        listeningJob?.cancel()
                        connectionLostSubject.onNext(Unit)
                        break
                    }
                }
            }
    }

    fun disconnect() {
        stopStreaming()
        running = false
        idleHealthCheckJob?.cancel()
        teardownConnection()
        listeningJob?.cancel()
        failPendingRequests("Disconnected")
    }

    private fun teardownConnection() {
        connectedFlag = false
        try { cameraSocket?.shutdownInput() } catch (_: Exception) {}
        try { cameraSocket?.shutdownOutput() } catch (_: Exception) {}
        try { inFromSocket?.close() } catch (_: Exception) {}
        try { outToSocket?.close() } catch (_: Exception) {}
        try { cameraSocket?.close() } catch (_: Exception) {}
        cameraSocket = null
        inFromSocket = null
        outToSocket = null
    }

    private fun failPendingRequests(reason: String) {
        val error = parseResponse(String.format(Constants.ERROR_RESPONSE, reason))
        pendingRequests.values.forEach { it.complete(error) }
        pendingRequests.clear()
        singleImageDeferred?.let { if (!it.isCompleted) it.complete(error) }
        singleImageDeferred = null
    }

    private fun writeCommand(bytes: ByteArray): Boolean {
        val out = outToSocket
        if (out == null) {
            teardownConnection()
            failPendingRequests("Socket not connected")
            return false
        }
        return try {
            out.write(bytes)
            out.flush()
            true
        } catch (e: IOException) {
            log.log(Level.WARNING, "Command write failed — tearing down connection", e)
            val wasConnected = connectedFlag
            teardownConnection()
            failPendingRequests("Socket write failed: ${e.message}")
            if (wasConnected) connectionLostSubject.onNext(Unit)
            false
        }
    }

    suspend fun sendCmd(
        cmd: String,
        expectedKey: String,
        timeoutMillis: Long = 5000L,
    ): JSONObject {
        if (!isConnected) {
            return parseResponse(String.format(Constants.ERROR_RESPONSE, "Socket disconnected"))
        }

        val deferredResponse = CompletableDeferred<JSONObject>()
        pendingRequests[expectedKey] = deferredResponse

        return withContext(Dispatchers.IO) {
            try {
                writeCommand(cmd.toByteArray(StandardCharsets.UTF_8))
                withTimeout(timeoutMillis) {
                    deferredResponse.await()
                }
            } catch (e: TimeoutCancellationException) {
                parseResponse(String.format(Constants.ERROR_RESPONSE, "Request timed out matching key: $expectedKey"))
            } finally {
                pendingRequests.remove(expectedKey)
            }
        }
    }

    val isConnected: Boolean
        get() = connectedFlag

    fun startStreaming() {
        isStreaming = true
        val args = String.format(Constants.ARGS_SET_STREAM_ON, 0, 0)
        val command = String.format(Constants.CMD_SET_STREAM_ON, args)

        serviceScope.launch {
            val response = sendCmd(command, expectedKey = "stream_status")
            log.fine("Stream started response status: $response")
        }
    }

    fun stopStreaming() {
        isStreaming = false
        serviceScope.launch {
            sendCmd(Constants.CMD_SET_STREAM_OFF, expectedKey = "stream_status")
        }
    }

    fun getImage() {
        serviceScope.launch {
            writeCommand(Constants.CMD_GET_IMAGE.toByteArray(StandardCharsets.UTF_8))
        }
    }

    fun runFfc() {
        serviceScope.launch {
            writeCommand(Constants.CMD_RUN_FFC.toByteArray(StandardCharsets.UTF_8))
        }
    }

    suspend fun getImageOnce(timeoutMs: Long = 15_000L): JSONObject? {
        if (!isConnected) return null
        val deferred = CompletableDeferred<JSONObject>()
        singleImageDeferred = deferred
        val sent =
            withContext(Dispatchers.IO) {
                writeCommand(Constants.CMD_GET_IMAGE.toByteArray(StandardCharsets.UTF_8))
            }
        if (!sent) {
            singleImageDeferred = null
            return null
        }
        return try {
            withTimeoutOrNull(timeoutMs) { deferred.await() }
        } catch (e: Exception) {
            singleImageDeferred = null
            null
        }
    }

    fun setSpotmeter(
        c1: Int,
        c2: Int,
        r1: Int,
        r2: Int,
    ) {
        serviceScope.launch {
            val args = String.format(Constants.ARGS_SET_SPOTMETER, c1, c2, r1, r2)
            val cmd = String.format(Constants.CMD_SET_SPOTMETER, args)
            sendCmd(cmd, expectedKey = "set_spotmeter")
        }
    }

    suspend fun getConfig(): JSONObject = sendCmd(Constants.CMD_GET_CONFIG, expectedKey = "config")

    suspend fun getWifi(): JSONObject = sendCmd(Constants.CMD_GET_WIFI, expectedKey = "wifi")

    fun setConfig(
        agcEnabled: Boolean,
        emissivity: Int,
        gainMode: Int,
    ) {
        serviceScope.launch {
            val args = String.format(Constants.ARGS_SET_CONFIG, if (agcEnabled) 1 else 0, emissivity, gainMode)
            val cmd = String.format(Constants.CMD_SET_CONFIG, args)
            writeCommand(cmd.toByteArray(StandardCharsets.UTF_8))
        }
    }

    fun setWifi(argsJson: String) {
        serviceScope.launch {
            val cmd = String.format(Constants.CMD_SET_WIFI, argsJson)
            writeCommand(cmd.toByteArray(StandardCharsets.UTF_8))
        }
    }

    /** Push this machine's current time to the camera. tCam's set_time command replies through
     *  the generic cam_info ack/nack channel rather than a keyed JSON response, so — like
     *  setConfig/setWifi — this just fires the command and doesn't wait for a match in
     *  pendingRequests. */
    fun setTime() {
        serviceScope.launch {
            val now = Calendar.getInstance()
            val args =
                String.format(
                    Constants.ARGS_SET_TIME,
                    now.get(Calendar.SECOND),
                    now.get(Calendar.MINUTE),
                    now.get(Calendar.HOUR_OF_DAY),
                    now.get(Calendar.DAY_OF_WEEK), // matches camera's 1=Sunday..7=Saturday
                    now.get(Calendar.DAY_OF_MONTH),
                    now.get(Calendar.MONTH) + 1, // Calendar.MONTH is 0-based; camera wants 1-12
                    now.get(Calendar.YEAR) - 1970, // camera wants year as an offset from 1970
                )
            val cmd = String.format(Constants.CMD_SET_TIME, args)
            writeCommand(cmd.toByteArray(StandardCharsets.UTF_8))
        }
    }

    fun shutdown() {
        serviceScope.cancel()
        pendingRequests.values.forEach { it.cancel() }
        pendingRequests.clear()
        disconnect()
    }

    private fun startListening() {
        running = true
        bytesRead = 0

        listeningJob =
            serviceScope.launch {
                val input = inFromSocket ?: return@launch
                var consecutiveReadTimeouts = 0
                while (isConnected && running) {
                    try {
                        bytesRead = input.read(readBuffer)
                        consecutiveReadTimeouts = 0
                    } catch (e: java.net.SocketTimeoutException) {
                        consecutiveReadTimeouts++
                        if (isStreaming && consecutiveReadTimeouts >= MAX_CONSECUTIVE_READ_TIMEOUTS_WHILE_STREAMING) {
                            log.warning(
                                "No frames for ${consecutiveReadTimeouts * SOCKET_READ_TIMEOUT_MS}ms " +
                                    "while streaming — treating camera as disconnected",
                            )
                            val wasRunning = running
                            running = false
                            teardownConnection()
                            failPendingRequests("No data received while streaming")
                            if (wasRunning) connectionLostSubject.onNext(Unit)
                            break
                        }
                        // Just an idle link (e.g. camera modem-sleep) — not necessarily dead.
                        // Looping back re-checks isConnected/running so a concurrent disconnect()
                        // is noticed promptly instead of blocking another full read() cycle.
                        continue
                    } catch (e: java.io.IOException) {
                        log.log(Level.WARNING, "Socket read error — tearing down connection", e)
                        val wasRunning = running
                        running = false
                        teardownConnection()
                        failPendingRequests("Socket read error: ${e.message}")
                        if (wasRunning) connectionLostSubject.onNext(Unit)
                        break
                    }
                    when {
                        bytesRead < 0 -> {
                            log.warning("Socket read hit EOF — camera closed the connection")
                            val wasRunning = running
                            running = false
                            teardownConnection()
                            failPendingRequests("Camera closed the connection")
                            if (wasRunning) connectionLostSubject.onNext(Unit)
                            break
                        }

                        bytesRead == 0 -> {
                            delay(100)
                            continue
                        }
                    }
                    for (index in 0 until bytesRead) {
                        val b = readBuffer[index]
                        when {
                            b == 0x02.toByte() -> {
                                if (startFound) responsePos = 0 else startFound = true
                            }

                            startFound && b == 0x03.toByte() -> {
                                val parsedJson =
                                    parseResponse(
                                        String(response, 0, responsePos, StandardCharsets.UTF_8),
                                    )
                                if (!routeToPendingRequest(parsedJson)) {
                                    val deferred = singleImageDeferred
                                    if (deferred != null && !deferred.isCompleted && parsedJson.has("radiometric")) {
                                        singleImageDeferred = null
                                        deferred.complete(parsedJson)
                                    }
                                    imageChannel.onNext(parsedJson)
                                }
                                resetBuffers()
                            }

                            startFound -> {
                                if (responsePos < response.size) {
                                    response[responsePos++] = b
                                } else {
                                    resetBuffers()
                                }
                            }
                        }
                    }
                }
            }
    }

    private fun routeToPendingRequest(json: JSONObject): Boolean {
        val cmdType = json.optString("cmd", json.optString("type", ""))

        if (pendingRequests.containsKey(cmdType)) {
            pendingRequests[cmdType]?.complete(json)
            return true
        }

        for (key in pendingRequests.keys) {
            if (json.has(key)) {
                pendingRequests[key]?.complete(json)
                return true
            }
        }
        return false
    }

    private fun resetBuffers() {
        responsePos = 0
        startFound = false
    }

    private fun parseResponse(responseString: String?): JSONObject {
        if (responseString == null) return JSONObject()
        return try {
            JSONObject(responseString)
        } catch (e: JSONException) {
            log.log(Level.WARNING, "Failed to parse camera response", e)
            JSONObject()
        }
    }
}
