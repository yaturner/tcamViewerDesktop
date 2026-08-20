package com.das.tcamviewerdesktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.das.tcamviewerdesktop.constants.Constants
import com.das.tcamviewerdesktop.model.ImageDto
import com.das.tcamviewerdesktop.settingsManager
import com.das.tcamviewerdesktop.util.buildCompositeImage
import com.das.tcamviewerdesktop.util.saveExportedPng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class VideoFrame(val bitmap: ImageBitmap, val dto: ImageDto, val timestampMs: Long)

private val TIME_LAPSE_SPEEDS = listOf(0.1f, 0.25f, 0.5f, 1f, 2f, 4f, 8f)
private const val TIME_LAPSE_DEFAULT_SPEED_INDEX = 3
private const val SKIP_FRAMES = 5

private fun formatSpeed(v: Float): String = if (v == v.toInt().toFloat()) v.toInt().toString() else v.toString()

/** Plays back a `.mtjsn` recording or `.tltjsn` time lapse frame-by-frame, mirroring the
 *  Android app's VideoPlayerWindow (skip ±5 frames, play/pause, speed control for time lapses).
 *  No MP4 export/share here — no portable desktop video encoder wired up. */
@Composable
fun VideoPlayerWindow(file: File, onDismiss: () -> Unit, onDelete: () -> Unit) {
    var videoFrames by remember { mutableStateOf<List<VideoFrame>>(emptyList()) }
    var frameIntervals by remember { mutableStateOf<List<Long>>(emptyList()) }
    var fallbackIntervalMs by remember { mutableStateOf(125L) }
    var isLoading by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var isExporting by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var speedIndex by remember { mutableIntStateOf(TIME_LAPSE_DEFAULT_SPEED_INDEX) }
    var speedMenuExpanded by remember { mutableStateOf(false) }
    val tempUnit by settingsManager.temperatureUnitFlow.collectAsState()
    val isCelsius = tempUnit == "Celsius"
    val spotmeterEnabled by settingsManager.spotmeterFlow.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val isTimeLapse = file.extension == "tltjsn"

    LaunchedEffect(file) {
        isLoading = true
        isPlaying = false
        currentIndex = 0
        speedIndex = TIME_LAPSE_DEFAULT_SPEED_INDEX
        val content = withContext(Dispatchers.IO) { readMtjsnContent(file) }
        fallbackIntervalMs = calculateFrameInterval(content.videoInfo, content.frames.size)
        val loaded = ArrayList<VideoFrame>(content.frames.size)
        withContext(Dispatchers.Default) {
            for (json in content.frames) {
                val dto = runCatching { ImageDto.create(json, null) }.getOrNull() ?: continue
                val bmp = dto.bitmap?.toComposeImageBitmap() ?: continue
                loaded.add(VideoFrame(bmp, dto, parseFrameTimestampMs(json)))
            }
        }
        val intervals = ArrayList<Long>(loaded.size)
        if (isTimeLapse) {
            repeat(loaded.size) { intervals.add(125L) }
        } else {
            val fb = fallbackIntervalMs
            for (i in 0 until loaded.size - 1) {
                val dt = loaded[i + 1].timestampMs - loaded[i].timestampMs
                intervals.add(if (dt in 10L..5_000L) dt else fb)
            }
            intervals.add(intervals.lastOrNull() ?: fb)
        }
        videoFrames = loaded
        frameIntervals = intervals
        isLoading = false
    }

    LaunchedEffect(isPlaying, speedIndex) {
        if (!isPlaying || videoFrames.isEmpty()) return@LaunchedEffect
        while (isPlaying) {
            val baseInterval = frameIntervals.getOrElse(currentIndex) { fallbackIntervalMs }
            val speed = if (isTimeLapse) TIME_LAPSE_SPEEDS[speedIndex] else 1f
            delay((baseInterval / speed).toLong().coerceAtLeast(1L))
            val next = currentIndex + 1
            if (next >= videoFrames.size) {
                isPlaying = false
            } else {
                currentIndex = next
            }
        }
    }

    val currentFrame = videoFrames.getOrNull(currentIndex)
    val hasThermal = currentFrame?.dto?.tLinearEnabled != 0
    val tempScale = if (currentFrame?.dto?.tLinearResolution == 0) 10f else 100f

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White) }
                Column(modifier = Modifier.weight(1f)) {
                    Text(formatFilename(file.name), color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (isLoading) "Loading…" else "${videoFrames.size} frames",
                        color = Color(0xFFAEAEB2),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (isExporting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    IconButton(
                        enabled = currentFrame != null,
                        onClick = {
                            val frame = currentFrame ?: return@IconButton
                            coroutineScope.launch {
                                isExporting = true
                                val composite = withContext(Dispatchers.Default) {
                                    buildCompositeImage(frame.dto, isCelsius, spotmeterEnabled)
                                }
                                val name = "${file.nameWithoutExtension}_frame${currentIndex + 1}"
                                val saved = withContext(Dispatchers.IO) { saveExportedPng(composite, name) }
                                isExporting = false
                                exportMessage = if (saved != null) "Exported to ${saved.path}" else "Export failed"
                            }
                        },
                    ) { Icon(Icons.Default.SaveAlt, contentDescription = "Export current frame", tint = Color.White) }
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    isLoading -> CircularProgressIndicator()
                    videoFrames.isEmpty() -> Text("No frames to display", color = Color.White)
                    currentFrame != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (hasThermal) {
                            Text(
                                formatTemp(currentFrame.dto.spotmeterMean, tempScale, isCelsius),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                        Image(
                            bitmap = currentFrame.bitmap,
                            contentDescription = null,
                            modifier = Modifier.size((Constants.IMAGE_WIDTH * 4).dp, (Constants.IMAGE_HEIGHT * 4).dp),
                            contentScale = ContentScale.FillBounds,
                        )
                        if (hasThermal) {
                            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                Text("Max: ${formatTemp(currentFrame.dto.maxTemperature, tempScale, isCelsius)}", color = Color.White)
                                Text("Min: ${formatTemp(currentFrame.dto.minTemperature, tempScale, isCelsius)}", color = Color.White)
                            }
                        }
                    }
                }
            }

            if (videoFrames.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Slider(
                        value = currentIndex.toFloat(),
                        onValueChange = {
                            isPlaying = false
                            currentIndex = it.toInt().coerceIn(0, videoFrames.size - 1)
                        },
                        valueRange = 0f..(videoFrames.size - 1).coerceAtLeast(1).toFloat(),
                        steps = (videoFrames.size - 2).coerceAtLeast(0),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = {
                            isPlaying = false
                            currentIndex = (currentIndex - SKIP_FRAMES).coerceAtLeast(0)
                        }) { Icon(Icons.Default.FastRewind, contentDescription = "Back $SKIP_FRAMES frames", tint = Color.White) }
                        IconButton(onClick = { isPlaying = !isPlaying }) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                            )
                        }
                        IconButton(onClick = {
                            isPlaying = false
                            currentIndex = (currentIndex + SKIP_FRAMES).coerceAtMost(videoFrames.size - 1)
                        }) { Icon(Icons.Default.FastForward, contentDescription = "Forward $SKIP_FRAMES frames", tint = Color.White) }
                        Text("${currentIndex + 1} / ${videoFrames.size}", color = Color.White, modifier = Modifier.padding(start = 8.dp))
                        if (isTimeLapse) {
                            Box(modifier = Modifier.padding(start = 16.dp)) {
                                TextButton(onClick = { speedMenuExpanded = true }) {
                                    Text("${formatSpeed(TIME_LAPSE_SPEEDS[speedIndex])}x", color = Color.White)
                                }
                                DropdownMenu(expanded = speedMenuExpanded, onDismissRequest = { speedMenuExpanded = false }) {
                                    TIME_LAPSE_SPEEDS.forEachIndexed { index, speed ->
                                        DropdownMenuItem(
                                            text = { Text("${formatSpeed(speed)}x") },
                                            onClick = { speedIndex = index; speedMenuExpanded = false },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Delete this ${if (isTimeLapse) "time lapse" else "recording"}?") },
                text = { Text("This cannot be undone.") },
                confirmButton = { TextButton(onClick = { showDeleteConfirm = false; onDelete() }) { Text("Delete") } },
                dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
            )
        }
        exportMessage?.let { msg ->
            AlertDialog(
                onDismissRequest = { exportMessage = null },
                confirmButton = { TextButton(onClick = { exportMessage = null }) { Text("OK") } },
                title = { Text("Export") },
                text = { Text(msg) },
            )
        }
    }
}
