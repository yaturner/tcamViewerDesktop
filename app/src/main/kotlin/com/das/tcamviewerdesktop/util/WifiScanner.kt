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
 *  equivalent, so this shells out to NetworkManager's `nmcli` (the standard tool on Ubuntu and
 *  most NetworkManager-managed Linux desktops) rather than talking to a WiFi radio directly. */
suspend fun scanWifiNetworks(): List<String> = withContext(Dispatchers.IO) {
    try {
        val process = ProcessBuilder("nmcli", "-t", "-f", "SSID", "dev", "wifi", "list", "--rescan", "yes")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val finished = process.waitFor(15, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return@withContext emptyList()
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
