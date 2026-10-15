package com.das.tcamviewerdesktop.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger

private val log = Logger.getLogger("WifiScanner")

/** Lists nearby WiFi SSIDs visible to this machine's own WiFi radio — the desktop equivalent of
 *  the Android app's WifiManager.scanResults, used so the user can pick a network for the camera
 *  to join instead of typing its SSID by hand. Android's WifiManager has no desktop-JVM
 *  equivalent, so this shells out to a platform WiFi tool: NetworkManager's `nmcli` on Linux, or
 *  `netsh` on Windows. Any failure (tool missing, parse error, timeout) degrades to an empty list
 *  rather than crashing — the user can still type the SSID by hand. */
suspend fun scanWifiNetworks(): List<String> = withContext(Dispatchers.IO) {
    val isWindows = System.getProperty("os.name")?.lowercase()?.contains("windows") == true
    if (isWindows) scanWifiNetworksWindows() else scanWifiNetworksLinux()
}

private fun scanWifiNetworksLinux(): List<String> {
    return try {
        val process = ProcessBuilder("nmcli", "-t", "-f", "SSID", "dev", "wifi", "list", "--rescan", "yes")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val finished = process.waitFor(15, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return emptyList()
        }
        output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
            .toList()
    } catch (e: Exception) {
        log.log(Level.WARNING, "nmcli WiFi scan failed (is NetworkManager installed?)", e)
        emptyList()
    }
}

private val windowsSsidLine = Regex("""^SSID\s+\d+\s*:\s*(.*)$""")

private fun scanWifiNetworksWindows(): List<String> {
    return try {
        val process = ProcessBuilder("netsh", "wlan", "show", "networks")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val finished = process.waitFor(15, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return emptyList()
        }
        output.lineSequence()
            .mapNotNull { windowsSsidLine.find(it.trim())?.groupValues?.get(1) }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
            .toList()
    } catch (e: Exception) {
        log.log(Level.WARNING, "netsh WiFi scan failed (is WiFi enabled?)", e)
        emptyList()
    }
}
