package com.das.tcamviewerdesktop.util

import com.das.tcamviewerdesktop.constants.Constants
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream

/** The latest-release descriptor Dan Julio publishes for each camera family. */
data class FirmwareInfo(val version: String, val location: String, val description: String)

/** A validated ESP-IDF application image, ready to send with fw_update_request/fw_segment. */
class FirmwareImage(val bytes: ByteArray, val version: String, val projectName: String)

/**
 * Helpers for tCam OTA firmware updates — checking danjuliodesigns.com for the latest release,
 * downloading it, and validating a `.bin` before it is sent to a camera.
 *
 * Plain [HttpURLConnection] rather than java.net.http.HttpClient on purpose: the latter lives in
 * the `java.net.http` module, which the jpackage'd runtime doesn't include by default.
 */
object Firmware {
    private const val HTTP_TIMEOUT_MS = 15_000

    // ESP-IDF image layout: 24-byte image header + 8-byte first-segment header, then
    // esp_app_desc_t — magic word, secure_version, 2 reserved words, version[32],
    // project_name[32], ...
    private const val APP_DESC_OFFSET = 32
    private const val APP_DESC_MAGIC = 0xABCD5432.toInt()
    private const val APP_DESC_VERSION_OFFSET = APP_DESC_OFFSET + 16
    private const val APP_DESC_PROJECT_OFFSET = APP_DESC_VERSION_OFFSET + 32
    private const val APP_DESC_STRING_LEN = 32
    private const val ESP_IMAGE_MAGIC: Byte = 0xE9.toByte()

    val downloadDir: File = File(CameraUtils.dataRoot, "Firmware")

    fun isFullTcam(model: Int): Boolean = (model and Constants.MODEL_MASK_NUMBER) == Constants.MODEL_NUM_TCAM

    fun supportsOta(model: Int): Boolean = (model and Constants.MODEL_MASK_OTA) != 0

    /** The project name built into the matching `.bin` — also its file name in the release zip. */
    fun expectedProjectName(model: Int): String = if (isFullTcam(model)) "tCam" else "tCamMini"

    fun cameraTypeName(model: Int): String = if (isFullTcam(model)) "tCam" else "tCam-Mini"

    /** Blocking — call from Dispatchers.IO. */
    @Throws(IOException::class)
    fun fetchLatestInfo(model: Int): FirmwareInfo {
        val url = if (isFullTcam(model)) Constants.FIRMWARE_INFO_URL_TCAM else Constants.FIRMWARE_INFO_URL_TCAM_MINI
        val json = JSONObject(String(httpGet(url), Charsets.UTF_8))
        return FirmwareInfo(
            version = json.getString("version"),
            location = json.getString("location"),
            description = json.optString("description"),
        )
    }

    /**
     * Downloads the release zip at [info]'s location into [downloadDir] (kept, so the user has
     * it for a manual/serial flash too) and pulls out the `<projectName>.bin` application image.
     * Blocking — call from Dispatchers.IO.
     */
    @Throws(IOException::class)
    fun downloadLatest(info: FirmwareInfo, model: Int): FirmwareImage {
        val zipBytes = httpGet(info.location)
        val zipName = info.location.substringAfterLast('/').takeIf { DeviceFiles.isSafeName(it) } ?: "firmware.zip"
        downloadDir.mkdirs()
        File(downloadDir, zipName).writeBytes(zipBytes)

        val binName = expectedProjectName(model) + ".bin"
        ZipInputStream(zipBytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name.substringAfterLast('/') == binName) {
                    return parseImage(zip.readBytes())
                        ?: throw IOException("$binName in $zipName is not a valid firmware image")
                }
            }
        }
        throw IOException("$binName not found in $zipName")
    }

    /** Validates [bytes] as an ESP-IDF application image and reads its embedded version and
     *  project name. Returns null if it isn't one (e.g. a bootloader or partition-table .bin). */
    fun parseImage(bytes: ByteArray): FirmwareImage? {
        if (bytes.size < APP_DESC_PROJECT_OFFSET + APP_DESC_STRING_LEN) return null
        if (bytes[0] != ESP_IMAGE_MAGIC) return null
        val magic = ByteBuffer.wrap(bytes, APP_DESC_OFFSET, 4).order(ByteOrder.LITTLE_ENDIAN).int
        if (magic != APP_DESC_MAGIC) return null
        val version = cString(bytes, APP_DESC_VERSION_OFFSET)
        val project = cString(bytes, APP_DESC_PROJECT_OFFSET)
        if (version.isEmpty()) return null
        return FirmwareImage(bytes, version, project)
    }

    /** True if dotted version [candidate] (e.g. "3.3") is newer than [current]. */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.split('.').map { it.trim().toIntOrNull() ?: 0 }
        val b = current.split('.').map { it.trim().toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun cString(bytes: ByteArray, offset: Int): String {
        var end = offset
        while (end < offset + APP_DESC_STRING_LEN && bytes[end] != 0.toByte()) end++
        return String(bytes, offset, end - offset, Charsets.US_ASCII)
    }

    private fun httpGet(url: String, redirectsLeft: Int = 5): ByteArray {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = HTTP_TIMEOUT_MS
            conn.readTimeout = HTTP_TIMEOUT_MS
            conn.instanceFollowRedirects = true
            val code = conn.responseCode
            // HttpURLConnection won't follow a redirect that switches protocol (http <-> https)
            if (code in 300..399 && redirectsLeft > 0) {
                val next = conn.getHeaderField("Location") ?: throw IOException("HTTP $code without Location from $url")
                return httpGet(URI(url).resolve(next).toString(), redirectsLeft - 1)
            }
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code from $url")
            return conn.inputStream.use { input ->
                ByteArrayOutputStream().also { input.copyTo(it) }.toByteArray()
            }
        } finally {
            conn.disconnect()
        }
    }
}
