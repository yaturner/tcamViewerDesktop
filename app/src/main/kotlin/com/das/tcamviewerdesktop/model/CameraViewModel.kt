package com.das.tcamviewerdesktop.model

import com.das.tcamviewerdesktop.cameraService
import com.das.tcamviewerdesktop.cameraUtils
import com.das.tcamviewerdesktop.constants.Constants
import com.das.tcamviewerdesktop.paletteFactory
import com.das.tcamviewerdesktop.settingsManager
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.awt.Toolkit
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.coroutines.coroutineContext

/** Ported from tcamViewer2's Android CameraViewModel. Differences from the Android version:
 *  - No androidx.lifecycle.ViewModel base; owns a plain CoroutineScope, torn down via [close].
 *  - Bitmap -> BufferedImage, android.graphics.Rect -> [Rect].
 *  - Shutter "sound" is a system beep (Toolkit.beep()) instead of a bundled WAV played through
 *    MediaPlayer — no audio asset to bundle for a first desktop port.
 *  - mDNS auto-discovery fallback (Android NsdManager-based) is NOT YET PORTED — auto-reconnect
 *    only retries the last-known IP, it doesn't fall back to rediscovering the camera if its
 *    DHCP lease changed while disconnected.
 */
class CameraViewModel {
    private val log = Logger.getLogger(CameraViewModel::class.java.name)
    private val vmScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _spotmeterTemp = MutableStateFlow("--")
    val spotmeterTemp: StateFlow<String> = _spotmeterTemp.asStateFlow()

    private val _maxTemp = MutableStateFlow("--")
    val maxTemp: StateFlow<String> = _maxTemp.asStateFlow()

    private val _minTemp = MutableStateFlow("--")
    val minTemp: StateFlow<String> = _minTemp.asStateFlow()

    private val _spotmeterTempValue = MutableStateFlow<Float?>(null)
    val spotmeterTempValue: StateFlow<Float?> = _spotmeterTempValue.asStateFlow()

    private val _maxTempValue = MutableStateFlow<Float?>(null)
    val maxTempValue: StateFlow<Float?> = _maxTempValue.asStateFlow()

    private val _minTempValue = MutableStateFlow<Float?>(null)
    val minTempValue: StateFlow<Float?> = _minTempValue.asStateFlow()

    private val _spotmeterEnabled = MutableStateFlow(true)
    val spotmeterEnabled: StateFlow<Boolean> = _spotmeterEnabled.asStateFlow()

    private val _fpsCounter = MutableStateFlow("-- fps")
    val fpsCounter: StateFlow<String> = _fpsCounter.asStateFlow()

    private val _showConnectError = MutableStateFlow(false)
    val showConnectError: StateFlow<Boolean> = _showConnectError.asStateFlow()

    private val _cameraConfig = MutableStateFlow<CameraConfig?>(null)
    val cameraConfig: StateFlow<CameraConfig?> = _cameraConfig.asStateFlow()

    private val _wifiInfo = MutableStateFlow<Map<String, String>?>(null)
    val wifiInfo: StateFlow<Map<String, String>?> = _wifiInfo.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    @Volatile private var recordingStream: FileOutputStream? = null

    @Volatile private var recordingFile: File? = null

    @Volatile private var recordingFrameCount: Int = 0
    private var recordingStartMs: Long = 0L
    private var startedStreamingForRecord = false

    private val _isTimeLapsing = MutableStateFlow(false)
    val isTimeLapsing: StateFlow<Boolean> = _isTimeLapsing.asStateFlow()

    private val _isTimeLapseCapturing = MutableStateFlow(false)
    val isTimeLapseCapturing: StateFlow<Boolean> = _isTimeLapseCapturing.asStateFlow()

    private val _timeLapseMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val timeLapseMessage: SharedFlow<String> = _timeLapseMessage.asSharedFlow()

    private val _alertMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val alertMessage: SharedFlow<String> = _alertMessage.asSharedFlow()

    @Volatile private var alertEnabled = false

    @Volatile private var alertMetric = "Spot" // "Spot" | "Max" | "Min"

    @Volatile private var alertComparison = "Above" // "Above" | "Below"

    @Volatile private var alertThreshold = 100f

    // Edge-triggered: fires once when the condition first becomes true, not on every frame
    // while it remains true, and rearms once the value crosses back.
    @Volatile private var alertCurrentlyTriggered = false

    private var timeLapseJob: Job? = null

    @Volatile private var discardTimeLapse = false

    private val _currentBitmap = MutableStateFlow<BufferedImage?>(null)
    val currentBitmap: StateFlow<BufferedImage?> = _currentBitmap.asStateFlow()

    private val _currentPalette = MutableStateFlow("Rainbow")
    val currentPalette: StateFlow<String> = _currentPalette.asStateFlow()

    private val _histogram = MutableStateFlow<IntArray?>(null)
    val histogram: StateFlow<IntArray?> = _histogram.asStateFlow()

    private val _currentImageDto = MutableStateFlow<ImageDto?>(null)
    val currentImageDto: StateFlow<ImageDto?> = _currentImageDto.asStateFlow()

    private val _spotmeterRect = MutableStateFlow<Rect?>(null)
    val spotmeterRect: StateFlow<Rect?> = _spotmeterRect.asStateFlow()

    private val _measurementMode = MutableStateFlow(MeasurementMode.POINT)
    val measurementMode: StateFlow<MeasurementMode> = _measurementMode.asStateFlow()

    private val _measurementRegion = MutableStateFlow<Rect?>(null)
    val measurementRegion: StateFlow<Rect?> = _measurementRegion.asStateFlow()

    private val _regionAvgTemp = MutableStateFlow("--")
    val regionAvgTemp: StateFlow<String> = _regionAvgTemp.asStateFlow()

    private val _regionMinTemp = MutableStateFlow("--")
    val regionMinTemp: StateFlow<String> = _regionMinTemp.asStateFlow()

    private val _regionMaxTemp = MutableStateFlow("--")
    val regionMaxTemp: StateFlow<String> = _regionMaxTemp.asStateFlow()

    private val _isCelsius = MutableStateFlow(true)
    val isCelsius: StateFlow<Boolean> = _isCelsius.asStateFlow()

    private val tempHistoryBuffer = ArrayDeque<TempSample>()
    private val _tempHistory = MutableStateFlow<List<TempSample>>(emptyList())
    val tempHistory: StateFlow<List<TempSample>> = _tempHistory.asStateFlow()

    @Volatile private var userMovedSpotmeter = false

    private var connectJob: Job? = null

    private val frameChannel = Channel<JSONObject>(Channel.CONFLATED)
    private var frameDisposable: Disposable? = null
    private var connectionLostDisposable: Disposable? = null

    @Volatile private var selectedPalette = "Rainbow"

    private var frameCount = 0
    private var fpsWindowStart = -1L

    private val tempHistoryWindowMs = 5 * 60_000L // keep the last 5 minutes of samples

    @Volatile private var tempHistoryWindowOverrideMs: Long? = null

    @Volatile private var shutterSoundEnabled = true

    @Volatile private var manualGetPending = false

    private fun playShutterSound() {
        try {
            Toolkit.getDefaultToolkit().beep()
        } catch (e: Exception) {
            log.log(Level.WARNING, "Shutter sound playback failed", e)
        }
    }

    init {
        observeSettings()
        frameDisposable =
            cameraService
                .getImageChannel()
                .subscribe(
                    { json -> frameChannel.trySend(json) },
                    { error -> log.log(Level.WARNING, "Frame stream error", error) },
                )
        connectionLostDisposable =
            cameraService
                .getConnectionLostSignal()
                .subscribe(
                    { onConnectionLost() },
                    { error -> log.log(Level.WARNING, "Connection-lost signal error", error) },
                )
        vmScope.launch(Dispatchers.Default) {
            for (json in frameChannel) processFrame(json)
        }
    }

    private fun observeSettings() {
        vmScope.launch {
            settingsManager.selectedPaletteFlow.collect { palette ->
                selectedPalette = palette
                _currentPalette.value = palette
                remapCurrentFrame(palette)
            }
        }
        vmScope.launch {
            settingsManager.manualRangeFlow.collect { v -> cameraUtils.settingIsManualRange = v }
        }
        vmScope.launch {
            settingsManager.minValueFlow.collect { v -> cameraUtils.settingManualMin = v.toFloatOrNull() ?: 0f }
        }
        vmScope.launch {
            settingsManager.maxValueFlow.collect { v -> cameraUtils.settingManualMax = v.toFloatOrNull() ?: 100f }
        }
        vmScope.launch {
            settingsManager.temperatureUnitFlow.collect { v ->
                _isCelsius.value = (v == "Celsius")
                cameraUtils.settingIsCelsius = (v == "Celsius")
                refreshTempDisplays()
                remapCurrentFrame(selectedPalette)
            }
        }
        vmScope.launch {
            settingsManager.spotmeterFlow.collect { v -> _spotmeterEnabled.value = v }
        }
        vmScope.launch {
            settingsManager.regionMeasurementFlow.collect { enabled ->
                _measurementMode.value = if (enabled) MeasurementMode.REGION else MeasurementMode.POINT
                seedDefaultRegionIfNeeded()
                clearTempHistory()
                refreshTempDisplays()
            }
        }
        vmScope.launch {
            settingsManager.alertEnabledFlow.collect { enabled ->
                alertEnabled = enabled
                alertCurrentlyTriggered = false
            }
        }
        vmScope.launch { settingsManager.alertMetricFlow.collect { alertMetric = it } }
        vmScope.launch { settingsManager.alertComparisonFlow.collect { alertComparison = it } }
        vmScope.launch {
            settingsManager.alertThresholdFlow.collect { v -> alertThreshold = v.toFloatOrNull() ?: 100f }
        }
    }

    private fun seedDefaultRegionIfNeeded() {
        if (_measurementMode.value != MeasurementMode.REGION || _measurementRegion.value != null) return
        val w = Constants.IMAGE_WIDTH / 4
        val h = Constants.IMAGE_HEIGHT / 4
        val cx = Constants.IMAGE_WIDTH / 2
        val cy = Constants.IMAGE_HEIGHT / 2
        _measurementRegion.value = Rect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
    }

    private suspend fun remapCurrentFrame(paletteName: String) {
        val dto = _currentImageDto.value ?: return
        val palette = paletteFactory.getPaletteByName(paletteName)
        val isManualRange = cameraUtils.settingIsManualRange
        val manualMin = if (isManualRange) cameraUtils.settingManualMin else 0f
        val manualMax = if (isManualRange) cameraUtils.settingManualMax else 0f
        val isCelsius = cameraUtils.settingIsCelsius
        val bmp =
            withContext(Dispatchers.Default) {
                cameraUtils.remapWithPalette(dto, palette, isManualRange, manualMin, manualMax, isCelsius)
            }
        if (bmp != null) {
            dto.paletteName = paletteName
            _currentBitmap.value = bmp
        }
    }

    private fun refreshTempDisplays() {
        val dto = _currentImageDto.value ?: return
        if (dto.tLinearEnabled == 0) return
        val celsius = cameraUtils.settingIsCelsius
        val scale = if (dto.tLinearResolution == 0) 10f else 100f
        val rect = _spotmeterRect.value
        val (spotValue, spotText) =
            if (rect != null && dto.imageData != null) {
                val cx = (rect.left + rect.right) / 2
                val cy = (rect.top + rect.bottom) / 2
                calcSpotTemp(dto.imageData!!, cx, cy, scale, celsius)
            } else {
                formatTemp(dto.spotmeterMean, scale, celsius)
            }
        val (maxValue, maxText) = formatTemp(dto.maxTemperature, scale, celsius)
        val (minValue, minText) = formatTemp(dto.minTemperature, scale, celsius)
        _spotmeterTemp.value = spotText
        _maxTemp.value = maxText
        _minTemp.value = minText
        _spotmeterTempValue.value = spotValue
        _maxTempValue.value = maxValue
        _minTempValue.value = minValue
        checkTemperatureAlert(spotValue, maxValue, minValue, celsius)

        val region = _measurementRegion.value
        if (_measurementMode.value == MeasurementMode.REGION && region != null && dto.imageData != null) {
            val (avg, regionMin, regionMax) = calcRegionStats(dto.imageData!!, region, scale, celsius)
            _regionAvgTemp.value = avg.second
            _regionMinTemp.value = regionMin.second
            _regionMaxTemp.value = regionMax.second
            recordTempSample(avg.first, regionMax.first, regionMin.first)
        } else {
            recordTempSample(spotValue, maxValue, minValue)
        }
    }

    private fun checkTemperatureAlert(
        spotValue: Float,
        maxValue: Float,
        minValue: Float,
        isCelsius: Boolean,
    ) {
        val spotVisible = spotmeterEnabled.value && measurementMode.value == MeasurementMode.POINT
        if (!alertEnabled || (alertMetric == "Spot" && !spotVisible)) {
            alertCurrentlyTriggered = false
            return
        }
        val value = when (alertMetric) {
            "Max" -> maxValue
            "Min" -> minValue
            else -> spotValue
        }
        val crossed = if (alertComparison == "Above") value > alertThreshold else value < alertThreshold
        if (crossed && !alertCurrentlyTriggered) {
            alertCurrentlyTriggered = true
            val unit = if (isCelsius) "°C" else "°F"
            val comparisonWord = if (alertComparison == "Above") "above" else "below"
            _alertMessage.tryEmit(
                "$alertMetric temperature $comparisonWord threshold: %.1f%s".format(value, unit),
            )
        } else if (!crossed) {
            alertCurrentlyTriggered = false
        }
    }

    fun setMeasurementRegion(rect: Rect) {
        val left = rect.left.coerceIn(0, Constants.IMAGE_WIDTH - 4)
        val top = rect.top.coerceIn(0, Constants.IMAGE_HEIGHT - 4)
        val right = rect.right.coerceIn(left + 4, Constants.IMAGE_WIDTH)
        val bottom = rect.bottom.coerceIn(top + 4, Constants.IMAGE_HEIGHT)
        _measurementRegion.value = Rect(left, top, right, bottom)
        refreshTempDisplays()
    }

    private fun recordTempSample(spot: Float, max: Float, min: Float) {
        val now = System.currentTimeMillis()
        val snapshot = synchronized(tempHistoryBuffer) {
            tempHistoryBuffer.addLast(TempSample(now, spot, max, min))
            val cutoff = now - (tempHistoryWindowOverrideMs ?: tempHistoryWindowMs)
            while (tempHistoryBuffer.isNotEmpty() && tempHistoryBuffer.first().timestampMs < cutoff) {
                tempHistoryBuffer.removeFirst()
            }
            tempHistoryBuffer.toList()
        }
        _tempHistory.value = snapshot
    }

    private fun clearTempHistory() {
        synchronized(tempHistoryBuffer) { tempHistoryBuffer.clear() }
        _tempHistory.value = emptyList()
    }

    fun clearChartHistory() = clearTempHistory()

    private suspend fun connectToCamera(ip: String, showErrorOnFailure: Boolean = true) {
        log.fine("connectToCamera ip=$ip")
        _isConnecting.value = true
        try {
            cameraService.disconnect()
            cameraService.setIpAddress(ip)
            val connected = cameraService.connect()
            log.fine("connectToCamera result=$connected")
            if (!coroutineContext.isActive) {
                if (connected) cameraService.disconnect()
                return
            }
            _isConnected.value = connected
            _isStreaming.value = false
            if (connected) {
                cameraService.getImage()
                loadCameraConfig()
                seedDefaultRegionIfNeeded()
            } else if (showErrorOnFailure) {
                _showConnectError.value = true
            }
        } finally {
            _isConnecting.value = false
        }
    }

    private fun onConnectionLost() {
        if (!_isConnected.value) return
        _isConnected.value = false
        _isStreaming.value = false
        startAutoReconnect()
    }

    /** Retries the last-known address a few times — most drops are transient (WiFi hiccup,
     *  camera modem-sleep) and clear up without the address changing.
     *
     *  NOTE: unlike the Android app, this does NOT yet fall back to mDNS rediscovery if the
     *  camera's DHCP lease changed while disconnected — that needs a JVM mDNS library (e.g.
     *  JmDNS), not yet wired up in this port. */
    private fun startAutoReconnect() {
        connectJob?.cancel()
        connectJob = vmScope.launch(Dispatchers.IO) {
            val lastIp = settingsManager.getCameraIp()
            repeat(3) { attempt ->
                delay(3_000L * (attempt + 1))
                if (!isActive) return@launch
                connectToCamera(lastIp, showErrorOnFailure = false)
                if (_isConnected.value) return@launch
            }
            if (!_isConnected.value && isActive) _showConnectError.value = true
        }
    }

    // --- Public actions called from the UI ---

    fun toggleConnection() {
        if (_isConnected.value || _isConnecting.value) {
            connectJob?.cancel()
            connectJob = null
            cameraService.disconnect()
            _isConnected.value = false
            _isConnecting.value = false
            _isStreaming.value = false
            _spotmeterRect.value = null
            _cameraConfig.value = null
            userMovedSpotmeter = false
            clearTempHistory()
            _measurementRegion.value = null
            alertCurrentlyTriggered = false
        } else {
            connectJob?.cancel()
            connectJob = vmScope.launch(Dispatchers.IO) {
                connectToCamera(settingsManager.getCameraIp())
            }
        }
    }

    fun dismissConnectError() {
        _showConnectError.value = false
    }

    fun reconnectAfterWifiChange(newIp: String?) {
        connectJob?.cancel()
        cameraService.disconnect()
        _isConnected.value = false
        _isStreaming.value = false
        _spotmeterRect.value = null
        _cameraConfig.value = null
        userMovedSpotmeter = false
        connectJob = vmScope.launch(Dispatchers.IO) {
            val ip =
                if (newIp != null) {
                    settingsManager.saveCameraIp(newIp)
                    newIp
                } else {
                    settingsManager.getCameraIp()
                }
            delay(8_000L)
            connectToCamera(ip)
        }
    }

    private suspend fun loadCameraConfig() {
        try {
            val response = cameraService.getConfig()
            val config = response.optJSONObject("config") ?: return
            val agcEnabled = config.optInt("agc_enabled") != 0
            // set_config/get_config takes emissivity as a plain 1-100 percentage — distinct
            // from the 0-8192 scale used by Lepton telemetry elsewhere.
            val emissivity = config.optInt("emissivity", 90).coerceIn(1, 100)
            val gainMode = config.optInt("gain_mode", Constants.GAIN_MODE_HIGH)
            _cameraConfig.value = CameraConfig(agcEnabled, emissivity, gainMode)

            settingsManager.saveCameraAgc(agcEnabled)
            settingsManager.saveCameraEmissivity(emissivity.toString())
            settingsManager.saveCameraGainMode(gainMode)
        } catch (e: Exception) {
            log.log(Level.WARNING, "loadCameraConfig failed", e)
        }
    }

    fun sendCameraConfig(agcEnabled: Boolean, emissivity: Int, gainMode: Int) {
        cameraService.setConfig(agcEnabled, emissivity, gainMode)
    }

    fun sendWifiConfig(
        isAccessPoint: Boolean,
        ssid: String,
        password: String,
        useStaticIp: Boolean,
        staticIp: String,
        staticNetmask: String,
    ) {
        val args =
            when {
                isAccessPoint -> String.format(Constants.ARGS_SET_WIFI_AP, ssid, password)
                useStaticIp ->
                    String.format(
                        Constants.ARGS_SET_WIFI_STATIC,
                        ssid,
                        password,
                        staticIp,
                        staticNetmask,
                    )
                else -> String.format(Constants.ARGS_SET_WIFI_NOT_STATIC, ssid, password)
            }
        cameraService.setWifi(args)
    }

    fun fetchWifiInfo() {
        _wifiInfo.value = null
        vmScope.launch(Dispatchers.IO) {
            try {
                val response = cameraService.getWifi()
                val wifi =
                    response.optJSONObject("wifi") ?: run {
                        _wifiInfo.value = emptyMap()
                        return@launch
                    }
                _wifiInfo.value = wifi.keys().asSequence().associateWith { wifi.optString(it) }
            } catch (e: Exception) {
                log.log(Level.WARNING, "fetchWifiInfo failed", e)
                _wifiInfo.value = emptyMap()
            }
        }
    }

    fun getImage() {
        if (!_isConnected.value) return
        manualGetPending = true
        cameraService.getImage()
    }

    fun startTimeLapse(intervalSec: Int, durationSec: Int) {
        if (!_isConnected.value || _isTimeLapsing.value) return
        val intervalMs = intervalSec * 1000L
        val durationMs = durationSec * 1000L
        _isTimeLapsing.value = true
        tempHistoryWindowOverrideMs = durationMs
        timeLapseJob =
            vmScope.launch(Dispatchers.IO) {
                var stream: FileOutputStream? = null
                var frameCount = 0
                var naturalCompletion = false
                val startMs = System.currentTimeMillis()
                var file: File? = null
                try {
                    val handle = cameraUtils.openTimeLapseFile()
                    stream = handle.stream
                    file = handle.file
                    val endTime = startMs + durationMs
                    while (isActive && System.currentTimeMillis() < endTime) {
                        val frameStart = System.currentTimeMillis()
                        _isTimeLapseCapturing.value = true
                        val json = cameraService.getImageOnce() ?: break
                        _isTimeLapseCapturing.value = false
                        stream.write(json.toString().toByteArray(Charsets.US_ASCII))
                        stream.write(0x03)
                        frameCount++
                        val remaining = intervalMs - (System.currentTimeMillis() - frameStart)
                        if (remaining > 0 && isActive) delay(remaining)
                    }
                    naturalCompletion = isActive
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.log(Level.WARNING, "Time lapse error", e)
                } finally {
                    val endMs = System.currentTimeMillis()
                    if (discardTimeLapse) {
                        runCatching { stream?.close() }
                        file?.delete()
                    } else {
                        runCatching {
                            stream?.write(buildFooterJson(startMs, endMs, frameCount).toByteArray(Charsets.US_ASCII))
                            stream?.close()
                        }
                    }
                    discardTimeLapse = false
                    _isTimeLapseCapturing.value = false
                    _isTimeLapsing.value = false
                    if (naturalCompletion) {
                        val samples = _tempHistory.value
                        val primaryLabel = if (_measurementMode.value == MeasurementMode.REGION) "Avg" else "Spot"
                        val chartSaved =
                            samples.size >= 2 &&
                                cameraUtils.saveTempChart(samples, cameraUtils.settingIsCelsius, primaryLabel)
                        val suffix = if (chartSaved) ", chart saved" else ""
                        _timeLapseMessage.tryEmit("Time lapse complete — $frameCount frames captured$suffix")
                    }
                    tempHistoryWindowOverrideMs = null
                }
            }
    }

    fun stopTimeLapse(save: Boolean) {
        if (!save) discardTimeLapse = true
        timeLapseJob?.cancel()
    }

    fun toggleStreaming() {
        if (_isStreaming.value) {
            if (_isRecording.value) finishRecording(stopStreamIfAutoStarted = false)
            cameraService.stopStreaming()
            _isStreaming.value = false
            frameCount = 0
            fpsWindowStart = -1L
            _fpsCounter.value = "-- fps"
        } else {
            frameCount = 0
            fpsWindowStart = -1L
            cameraService.startStreaming()
            _isStreaming.value = true
        }
    }

    fun toggleRecording() {
        if (_isRecording.value) {
            finishRecording()
            return
        }
        if (!_isConnected.value) return
        if (!_isStreaming.value) {
            startedStreamingForRecord = true
            frameCount = 0
            fpsWindowStart = -1L
            cameraService.startStreaming()
            _isStreaming.value = true
        } else {
            startedStreamingForRecord = false
        }
        vmScope.launch(Dispatchers.IO) {
            try {
                val handle = cameraUtils.openRecordingFile()
                recordingStream = handle.stream
                recordingFile = handle.file
                recordingStartMs = System.currentTimeMillis()
                recordingFrameCount = 0
                _isRecording.value = true
            } catch (e: Exception) {
                log.log(Level.WARNING, "Failed to open recording file", e)
                if (startedStreamingForRecord) {
                    cameraService.stopStreaming()
                    _isStreaming.value = false
                    startedStreamingForRecord = false
                }
            }
        }
    }

    fun stopRecording(save: Boolean) {
        finishRecording(save = save, stopStreamIfAutoStarted = false)
        toggleStreaming()
    }

    private fun finishRecording(save: Boolean = true, stopStreamIfAutoStarted: Boolean = true) {
        val stream = recordingStream
        val file = recordingFile
        recordingStream = null
        recordingFile = null
        val count = recordingFrameCount
        val endMs = System.currentTimeMillis()
        _isRecording.value = false
        vmScope.launch(Dispatchers.IO) {
            if (stream != null) {
                try {
                    if (save) {
                        stream.write(buildFooterJson(recordingStartMs, endMs, count).toByteArray(Charsets.US_ASCII))
                        stream.close()
                    } else {
                        stream.close()
                        file?.delete()
                    }
                } catch (e: Exception) {
                    log.log(Level.WARNING, "Failed to write recording footer", e)
                }
            }
            if (stopStreamIfAutoStarted && startedStreamingForRecord) {
                startedStreamingForRecord = false
                cameraService.stopStreaming()
                _isStreaming.value = false
                frameCount = 0
                fpsWindowStart = -1L
                _fpsCounter.value = "-- fps"
            }
        }
    }

    private fun buildFooterJson(startMs: Long, endMs: Long, numFrames: Int): String {
        val timeFmt = SimpleDateFormat("H:mm:ss.SSS", Locale.US)
        val dateFmt = SimpleDateFormat("M/d/yy", Locale.US)
        val start = Date(startMs)
        val end = Date(endMs)
        return """{"video_info":{"start_time":"${timeFmt.format(start)}","start_date":"${dateFmt.format(start)}",""" +
            """"end_time":"${timeFmt.format(end)}","end_date":"${dateFmt.format(end)}",""" +
            """"num_frames":$numFrames,"version":1}}"""
    }

    fun setPalette(name: String) {
        settingsManager.saveSelectedPalette(name)
    }

    fun setSpotmeter(camX: Int, camY: Int) {
        val c1 = (camX - 2).coerceAtLeast(0)
        val c2 = (camX + 2).coerceAtMost(Constants.IMAGE_WIDTH - 1)
        val r1 = (camY - 2).coerceAtLeast(0)
        val r2 = (camY + 2).coerceAtMost(Constants.IMAGE_HEIGHT - 1)

        userMovedSpotmeter = true
        _spotmeterRect.value = Rect(c1, r1, c2, r2)

        val dto = _currentImageDto.value
        if (dto?.imageData != null && dto.tLinearEnabled != 0) {
            val scale = if (dto.tLinearResolution == 0) 10f else 100f
            val (spotValue, spotText) =
                calcSpotTemp(dto.imageData!!, camX, camY, scale, settingsManager.isUnitsCelsius())
            _spotmeterTemp.value = spotText
            _spotmeterTempValue.value = spotValue
        }

        cameraService.setSpotmeter(c1, c2, r1, r2)
        if (!_isStreaming.value && _isConnected.value) cameraService.getImage()
    }

    // --- Frame processing ---

    private suspend fun processFrame(json: JSONObject) {
        if (!json.has("radiometric")) return
        if (manualGetPending) {
            manualGetPending = false
            if (shutterSoundEnabled) playShutterSound()
        }
        try {
            val stream = recordingStream
            if (stream != null) {
                withContext(Dispatchers.IO) {
                    stream.write(json.toString().toByteArray(Charsets.US_ASCII))
                    stream.write(0x03)
                }
                recordingFrameCount++
            }
            val dto = ImageDto.create(json, selectedPalette)
            _currentImageDto.value = dto
            _currentBitmap.value = dto.bitmap
            _histogram.value = dto.histogram
            if (!userMovedSpotmeter) dto.spotmeterLocation?.let { _spotmeterRect.value = it }
            if (dto.tLinearEnabled != 0) {
                refreshTempDisplays()
            } else {
                _spotmeterTemp.value = "--"
                _maxTemp.value = "--"
                _minTemp.value = "--"
                _spotmeterTempValue.value = null
                _maxTempValue.value = null
                _minTempValue.value = null
            }
            updateFps()
        } catch (e: Exception) {
            log.log(Level.WARNING, "Frame processing error", e)
        }
    }

    private fun formatTemp(rawValue: Int, scale: Float, isCelsius: Boolean): Pair<Float, String> {
        val tempC = rawValue / scale - 273.15f
        val value = if (isCelsius) tempC else tempC * 9f / 5f + 32f
        val text = if (isCelsius) "%.1f°C".format(value) else "%.1f°F".format(value)
        return value to text
    }

    private fun calcSpotTemp(imageData: IntArray, cx: Int, cy: Int, scale: Float, isCelsius: Boolean): Pair<Float, String> {
        val c1 = cx.coerceIn(0, Constants.IMAGE_WIDTH - 1)
        val c2 = (cx + 1).coerceAtMost(Constants.IMAGE_WIDTH - 1)
        val r1 = cy.coerceIn(0, Constants.IMAGE_HEIGHT - 1)
        val r2 = (cy + 1).coerceAtMost(Constants.IMAGE_HEIGHT - 1)
        var sum = 0L
        var count = 0
        for (row in r1..r2) {
            for (col in c1..c2) {
                sum += imageData[row * Constants.IMAGE_WIDTH + col]
                count++
            }
        }
        return formatTemp(if (count > 0) (sum / count).toInt() else 0, scale, isCelsius)
    }

    private fun calcRegionStats(
        imageData: IntArray,
        rect: Rect,
        scale: Float,
        isCelsius: Boolean,
    ): Triple<Pair<Float, String>, Pair<Float, String>, Pair<Float, String>> {
        val c1 = rect.left.coerceIn(0, Constants.IMAGE_WIDTH - 1)
        val c2 = rect.right.coerceIn(c1, Constants.IMAGE_WIDTH - 1)
        val r1 = rect.top.coerceIn(0, Constants.IMAGE_HEIGHT - 1)
        val r2 = rect.bottom.coerceIn(r1, Constants.IMAGE_HEIGHT - 1)
        var sum = 0L
        var count = 0
        var minRaw = Int.MAX_VALUE
        var maxRaw = Int.MIN_VALUE
        for (row in r1..r2) {
            for (col in c1..c2) {
                val v = imageData[row * Constants.IMAGE_WIDTH + col]
                sum += v
                count++
                if (v < minRaw) minRaw = v
                if (v > maxRaw) maxRaw = v
            }
        }
        if (count == 0) return Triple(0f to "--", 0f to "--", 0f to "--")
        return Triple(
            formatTemp((sum / count).toInt(), scale, isCelsius),
            formatTemp(minRaw, scale, isCelsius),
            formatTemp(maxRaw, scale, isCelsius),
        )
    }

    private fun updateFps() {
        val now = System.nanoTime() / 1_000_000L
        if (fpsWindowStart < 0L) {
            fpsWindowStart = now
            frameCount = 0
            return
        }
        frameCount++
        val elapsed = now - fpsWindowStart
        if (elapsed >= 1000L) {
            _fpsCounter.value = "${(frameCount * 1000f / elapsed).toInt()} fps"
            frameCount = 0
            fpsWindowStart = now
        }
    }

    /** Tears down the ViewModel — call once on application exit. */
    fun close() {
        frameChannel.close()
        frameDisposable?.dispose()
        connectionLostDisposable?.dispose()
        cameraService.disconnect()
        vmScope.cancel()
    }
}
