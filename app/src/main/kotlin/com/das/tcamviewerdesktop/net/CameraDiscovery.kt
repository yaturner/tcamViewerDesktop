package com.das.tcamviewerdesktop.net

import com.das.tcamviewerdesktop.constants.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.logging.Level
import java.util.logging.Logger
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener

data class DiscoveredCamera(val name: String, val ip: String)

private val log = Logger.getLogger("CameraDiscovery")

/** mDNS discovery of tCam cameras on the local network — the desktop equivalent of the Android
 *  app's NsdManager-based discoverTcamCameras(). java.net has no built-in mDNS/DNS-SD client, so
 *  this uses JmDNS instead.
 *
 *  `JmDNS.create()` (no bind address) silently picks ONE local interface, and on a multi-homed
 *  desktop (wired + WiFi both up) that's frequently the wrong one — it found zero devices bound
 *  to this dev machine's ethernet interface even though the camera was reachable over WiFi.
 *  Fixed by binding a separate JmDNS instance to every active, non-loopback IPv4 interface
 *  address and merging whatever each one finds. */
suspend fun discoverTcamCameras(timeoutMs: Long = 8_000L): List<DiscoveredCamera> = withContext(Dispatchers.IO) {
    // JmDNS service types live under the "local." domain — Constants.SERVICE_TYPE already ends
    // in a dot (Android NsdManager's convention), so this just appends the domain label.
    val serviceType = Constants.SERVICE_TYPE + "local."
    val addresses = localIpv4Addresses()
    if (addresses.isEmpty()) return@withContext emptyList()

    val found = java.util.Collections.synchronizedMap(LinkedHashMap<String, DiscoveredCamera>())

    coroutineScope {
        addresses
            .map { addr -> async { discoverOn(addr, serviceType, timeoutMs, found) } }
            .awaitAll()
    }

    found.values.toList()
}

private suspend fun discoverOn(
    bindAddress: InetAddress,
    serviceType: String,
    timeoutMs: Long,
    found: MutableMap<String, DiscoveredCamera>,
) {
    val jmdns = try {
        JmDNS.create(bindAddress)
    } catch (e: Exception) {
        log.log(Level.FINE, "JmDNS.create($bindAddress) failed", e)
        return
    }

    val listener = object : ServiceListener {
        override fun serviceAdded(event: ServiceEvent) {
            jmdns.requestServiceInfo(event.type, event.name, 3_000)
        }

        override fun serviceRemoved(event: ServiceEvent) {}

        override fun serviceResolved(event: ServiceEvent) {
            val info = event.info
            val ip = info.hostAddresses.firstOrNull() ?: return
            found[info.name] = DiscoveredCamera(info.name, ip)
        }
    }

    try {
        jmdns.addServiceListener(serviceType, listener)
        delay(timeoutMs)
    } finally {
        runCatching { jmdns.removeServiceListener(serviceType, listener) }
        runCatching { jmdns.close() }
    }
}

/** Every active, non-loopback, non-virtual interface's IPv4 address — candidates to bind a
 *  JmDNS instance to, since multicast discovery only sees traffic on the interface it's bound to. */
private fun localIpv4Addresses(): List<InetAddress> = try {
    NetworkInterface.getNetworkInterfaces().asSequence()
        .filter { it.isUp && !it.isLoopback && !it.isVirtual }
        .flatMap { it.inetAddresses.asSequence() }
        .filterIsInstance<Inet4Address>()
        .toList()
} catch (e: Exception) {
    log.log(Level.WARNING, "Failed to enumerate network interfaces", e)
    emptyList()
}
