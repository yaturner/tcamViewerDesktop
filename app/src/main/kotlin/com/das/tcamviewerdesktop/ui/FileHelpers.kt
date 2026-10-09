package com.das.tcamviewerdesktop.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** Shared file/date/recording helpers used by [LibraryScreen], [ChartsScreen], and
 *  [VideoPlayerWindow] — ported from tcamViewer2's Android LibraryScreen.kt. */

/** MM_dd_yyyy folder name → local midnight epoch millis, or null if it doesn't match that format
 *  (defensively lets any unexpected folder name pass every filter rather than being hidden). */
internal fun parseFolderDateMillis(folderName: String): Long? = runCatching {
    java.text.SimpleDateFormat("MM_dd_yyyy", java.util.Locale.getDefault())
        .apply { isLenient = false }
        .parse(folderName)
        ?.time
}.getOrNull()

/** MM_dd_yyyy → "June 25, 2026" */
internal fun formatDateFolder(name: String): String {
    val parts = name.split("_")
    if (parts.size != 3) return name
    val monthNames = arrayOf(
        "", "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
    val month = parts[0].toIntOrNull()?.let { monthNames.getOrNull(it) } ?: return name
    return "$month ${parts[1]}, ${parts[2]}"
}

/**
 * img_HH_mm_ss.tjsn / vid_HH_mm_ss.tmjsn / tl_HH_mm_ss.tltjsn / chart_HH_mm_ss.tchart → "HH:mm:ss"
 * ".mtjsn" is the legacy video extension (renamed to ".tmjsn"); still handled here so recordings
 * saved before the rename keep displaying correctly.
 */
internal fun formatFilename(name: String): String {
    val base = when {
        name.endsWith(".tmjsn") -> name.removeSuffix(".tmjsn").removePrefix("vid_")
        name.endsWith(".mtjsn") -> name.removeSuffix(".mtjsn").removePrefix("vid_")
        name.endsWith(".tltjsn") -> name.removeSuffix(".tltjsn").removePrefix("tl_")
        name.endsWith(".tchart") -> name.removeSuffix(".tchart").removePrefix("chart_")
        else -> name.removeSuffix(".tjsn").removePrefix("img_")
    }
    val parts = base.split("_")
    return if (parts.size == 3) "${parts[0]}:${parts[1]}:${parts[2]}" else name
}

internal fun formatTemp(rawValue: Int, scale: Float, isCelsius: Boolean): String {
    val tempC = rawValue / scale - 273.15f
    return if (isCelsius) "%.1f°C".format(tempC) else "%.1f°F".format(tempC * 9f / 5f + 32f)
}

internal data class MtjsnContent(val frames: List<JSONObject>, val videoInfo: JSONObject?)

/** Reads just the first radiometric frame of a `.tmjsn` (or legacy `.mtjsn`)/`.tltjsn` recording — used for thumbnails
 *  where decoding every frame would be wasteful. */
internal suspend fun readFirstMtjsnFrame(file: File): JSONObject? = withContext(Dispatchers.IO) {
    runCatching {
        val sb = StringBuilder()
        file.inputStream().use { stream ->
            val buf = ByteArray(8192)
            var done = false
            while (!done) {
                val n = stream.read(buf)
                if (n < 0) break
                for (i in 0 until n) {
                    val b = buf[i].toInt() and 0xFF
                    if (b == 0x03) {
                        done = true
                        break
                    }
                    sb.append(b.toChar())
                }
            }
        }
        if (sb.isEmpty()) null else JSONObject(sb.toString())
    }.getOrNull()
}

/** Reads every frame plus the trailing footer (video_info) of a `.tmjsn` (or legacy `.mtjsn`)/`.tltjsn` recording. */
internal suspend fun readMtjsnContent(file: File): MtjsnContent = withContext(Dispatchers.IO) {
    val frames = mutableListOf<JSONObject>()
    val sb = StringBuilder()
    file.inputStream().use { stream ->
        val buf = ByteArray(8192)
        while (true) {
            val n = stream.read(buf)
            if (n < 0) break
            for (i in 0 until n) {
                val b = buf[i].toInt() and 0xFF
                if (b == 0x03) {
                    if (sb.isNotEmpty()) {
                        runCatching {
                            val json = JSONObject(sb.toString())
                            if (json.has("radiometric")) frames.add(json)
                        }
                        sb.clear()
                    }
                } else {
                    sb.append(b.toChar())
                }
            }
        }
    }
    // Content remaining after the last ETX (no trailing ETX) is the footer JSON
    val footer = if (sb.isNotEmpty()) {
        runCatching { JSONObject(sb.toString()).optJSONObject("video_info") }.getOrNull()
    } else {
        null
    }
    MtjsnContent(frames, footer)
}

internal fun parseVideoTimeMs(t: String): Long? = runCatching {
    val p = t.split(":")
    val ms = p[2].split(".").let { it.getOrNull(1)?.padEnd(3, '0')?.take(3)?.toLong() ?: 0L }
    p[0].toLong() * 3_600_000L + p[1].toLong() * 60_000L + p[2].split(".")[0].toLong() * 1_000L + ms
}.getOrNull()

internal fun calculateFrameInterval(videoInfo: JSONObject?, numFrames: Int): Long {
    if (videoInfo == null || numFrames <= 1) return 125L
    return runCatching {
        val startMs = parseVideoTimeMs(videoInfo.getString("start_time")) ?: return@runCatching 125L
        val endMs = parseVideoTimeMs(videoInfo.getString("end_time")) ?: return@runCatching 125L
        if (endMs > startMs) (endMs - startMs) / numFrames else 125L
    }.getOrElse { 125L }
}

internal fun parseFrameTimestampMs(json: JSONObject): Long = runCatching {
    val timeStr = json.getJSONObject("metadata").optString("Time", "")
    parseVideoTimeMs(timeStr) ?: 0L
}.getOrElse { 0L }
