package com.das.tcamviewerdesktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.das.tcamviewerdesktop.cameraUtils
import com.das.tcamviewerdesktop.constants.Constants
import com.das.tcamviewerdesktop.model.CameraViewModel
import com.das.tcamviewerdesktop.model.MeasurementMode
import com.das.tcamviewerdesktop.model.Rect
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val DISPLAY_SCALE = 4

/** Live-view tab: connect/stream/record controls, the thermal image with spotmeter/region
 *  overlay and tap-to-move, temperature readouts, and the rolling temperature-history chart.
 *  Palette/units/measurement-mode/camera-config settings live on [SettingsScreen] instead —
 *  mirrors the Android app's split between CameraScreen and SettingsScreen. */
@Composable
fun CameraScreen(viewModel: CameraViewModel, modifier: Modifier = Modifier, onShowMessage: (String) -> Unit = {}) {
    val isConnected by viewModel.isConnected.collectAsState()
    val isConnecting by viewModel.isConnecting.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val isTimeLapsing by viewModel.isTimeLapsing.collectAsState()
    val isTimeLapseCapturing by viewModel.isTimeLapseCapturing.collectAsState()
    val currentImageDto by viewModel.currentImageDto.collectAsState()

    var streamMenuExpanded by remember { mutableStateOf(false) }
    var showTimeLapseDialog by remember { mutableStateOf(false) }
    var showStopSaveDialog by remember { mutableStateOf(false) }

    val bitmap by viewModel.currentBitmap.collectAsState()
    val spotTemp by viewModel.spotmeterTemp.collectAsState()
    val maxTemp by viewModel.maxTemp.collectAsState()
    val minTemp by viewModel.minTemp.collectAsState()
    val fps by viewModel.fpsCounter.collectAsState()
    val spotmeterRect by viewModel.spotmeterRect.collectAsState()
    val spotmeterEnabled by viewModel.spotmeterEnabled.collectAsState()
    val measurementMode by viewModel.measurementMode.collectAsState()
    val measurementRegion by viewModel.measurementRegion.collectAsState()
    val tempHistory by viewModel.tempHistory.collectAsState()

    val displayW = Constants.IMAGE_WIDTH * DISPLAY_SCALE
    val displayH = Constants.IMAGE_HEIGHT * DISPLAY_SCALE

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Spot: $spotTemp", style = MaterialTheme.typography.bodyLarge)
            Text("Max: $maxTemp", style = MaterialTheme.typography.bodyLarge)
            Text("Min: $minTemp", style = MaterialTheme.typography.bodyLarge)
            Text(fps, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(12.dp))
        Box(
            modifier =
                Modifier
                    .size(displayW.dp, displayH.dp)
                    .background(Color.Black)
                    .border(1.dp, Color.Gray)
                    .then(
                        if (measurementMode == MeasurementMode.POINT) {
                            if (spotmeterEnabled) {
                                Modifier.pointerInput(isConnected) {
                                    if (!isConnected) return@pointerInput
                                    detectTapGestures { offset ->
                                        val camX = (offset.x / DISPLAY_SCALE).toInt().coerceIn(0, Constants.IMAGE_WIDTH - 1)
                                        val camY = (offset.y / DISPLAY_SCALE).toInt().coerceIn(0, Constants.IMAGE_HEIGHT - 1)
                                        viewModel.setSpotmeter(camX, camY)
                                    }
                                }
                            } else {
                                Modifier
                            }
                        } else {
                            // Keyed only on isConnected/mode (not the region itself, which changes
                            // every drag step) — always reads/writes the ViewModel's StateFlow
                            // directly so the gesture never restarts mid-drag.
                            Modifier.pointerInput(isConnected, measurementMode) {
                                if (!isConnected) return@pointerInput
                                var dragTarget = RegionDragTarget.NONE
                                detectDragGestures(
                                    onDragStart = { start ->
                                        val region = viewModel.measurementRegion.value ?: return@detectDragGestures
                                        val camX = start.x / DISPLAY_SCALE
                                        val camY = start.y / DISPLAY_SCALE
                                        dragTarget = resolveRegionDragTarget(region, camX, camY)
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        if (dragTarget == RegionDragTarget.NONE) return@detectDragGestures
                                        val region = viewModel.measurementRegion.value ?: return@detectDragGestures
                                        val dCamX = dragAmount.x / DISPLAY_SCALE
                                        val dCamY = dragAmount.y / DISPLAY_SCALE
                                        viewModel.setMeasurementRegion(applyRegionDrag(region, dragTarget, dCamX, dCamY))
                                    },
                                )
                            }
                        },
                    ),
        ) {
            bitmap?.let { bmp ->
                Image(
                    bitmap = bmp.toComposeImageBitmap(),
                    contentDescription = "Thermal image",
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (measurementMode == MeasurementMode.POINT && spotmeterEnabled) {
                spotmeterRect?.let { rect ->
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cx = (rect.left + rect.right) / 2f * DISPLAY_SCALE
                        val cy = (rect.top + rect.bottom) / 2f * DISPLAY_SCALE
                        val half = 6.dp.toPx()
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(cx - half, cy - half),
                            size = Size(half * 2, half * 2),
                            style = Stroke(width = 3f),
                        )
                        drawRect(
                            color = Color.White,
                            topLeft = Offset(cx - half, cy - half),
                            size = Size(half * 2, half * 2),
                            style = Stroke(width = 1f),
                        )
                    }
                }
            }
            if (measurementMode == MeasurementMode.REGION) {
                measurementRegion?.let { rect ->
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(
                            color = Color.Yellow,
                            topLeft = Offset(rect.left * DISPLAY_SCALE.toFloat(), rect.top * DISPLAY_SCALE.toFloat()),
                            size =
                                Size(
                                    rect.width * DISPLAY_SCALE.toFloat(),
                                    rect.height * DISPLAY_SCALE.toFloat(),
                                ),
                            style = Stroke(width = 2f),
                        )
                    }
                }
            }
        }
        if (measurementMode == MeasurementMode.REGION) {
            val avg by viewModel.regionAvgTemp.collectAsState()
            val rMin by viewModel.regionMinTemp.collectAsState()
            val rMax by viewModel.regionMaxTemp.collectAsState()
            Spacer(Modifier.height(8.dp))
            Text("Region — avg: $avg  min: $rMin  max: $rMax")
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Temperature History", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { viewModel.clearChartHistory() }) {
                Icon(Icons.Filled.Clear, contentDescription = "Clear temperature history")
            }
        }
        val isCelsius by viewModel.isCelsius.collectAsState()
        TemperatureHistoryChart(
            samples = tempHistory,
            isCelsius = isCelsius,
            primaryLabel = if (measurementMode == MeasurementMode.REGION) "Avg" else "Spot",
        )
        }
        HorizontalDivider()
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Button(
                onClick = { viewModel.toggleConnection() },
                enabled = !isConnecting,
            ) {
                Text(
                    when {
                        isConnecting -> "Connecting..."
                        isConnected -> "Disconnect"
                        else -> "Connect"
                    },
                )
            }
            Button(onClick = { viewModel.getImage() }, enabled = isConnected && !isStreaming) {
                Icon(Icons.Filled.Camera, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Get")
            }
            Button(
                onClick = {
                    val dto = currentImageDto ?: return@Button
                    if (runCatching { cameraUtils.saveTjsn(dto) }.getOrDefault(false)) {
                        onShowMessage("Image saved as ${dto.filename}")
                    } else {
                        onShowMessage("Save failed")
                    }
                },
                enabled = currentImageDto != null,
            ) {
                Text("Save")
            }

            // Stop button (active) or Stream dropdown (idle) — mirrors the Android app's
            // Stream/Record/Time Lapse choice menu.
            if (isStreaming || isRecording || isTimeLapsing) {
                Button(onClick = {
                    if (isTimeLapsing || isRecording) {
                        showStopSaveDialog = true
                    } else {
                        viewModel.toggleStreaming()
                    }
                }) {
                    Text(
                        when {
                            isTimeLapsing && isTimeLapseCapturing -> "Rec"
                            isTimeLapsing -> "Stream"
                            else -> "Stop"
                        },
                    )
                }
            } else {
                val canStream = isConnected && currentImageDto != null
                Box {
                    Button(onClick = { streamMenuExpanded = true }, enabled = canStream) {
                        Text("Stream")
                    }
                    DropdownMenu(expanded = streamMenuExpanded, onDismissRequest = { streamMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Start") },
                            enabled = canStream,
                            onClick = {
                                viewModel.toggleStreaming()
                                streamMenuExpanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Record") },
                            enabled = canStream,
                            onClick = {
                                viewModel.toggleRecording()
                                streamMenuExpanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Time Lapse") },
                            enabled = canStream,
                            onClick = {
                                streamMenuExpanded = false
                                showTimeLapseDialog = true
                            },
                        )
                    }
                }
            }
        }
    }

    if (showTimeLapseDialog) {
        TimeLapseDialog(
            onConfirm = { intervalSec, durationSec ->
                showTimeLapseDialog = false
                viewModel.startTimeLapse(intervalSec, durationSec)
            },
            onDismiss = { showTimeLapseDialog = false },
        )
    }

    if (showStopSaveDialog) {
        val label = if (isTimeLapsing) "time lapse" else "recording"
        AlertDialog(
            onDismissRequest = { showStopSaveDialog = false },
            title = { Text("Save $label?") },
            text = { Text("Do you want to save the $label, or discard it?") },
            confirmButton = {
                TextButton(onClick = {
                    showStopSaveDialog = false
                    if (isTimeLapsing) viewModel.stopTimeLapse(save = true) else viewModel.stopRecording(save = true)
                    onShowMessage(if (isTimeLapsing) "Time lapse saved" else "Recording saved")
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showStopSaveDialog = false
                    if (isTimeLapsing) viewModel.stopTimeLapse(save = false) else viewModel.stopRecording(save = false)
                }) { Text("Discard") }
            },
        )
    }
}

private val TIMELAPSE_INTERVALS = listOf(
    1 to "1 second",
    2 to "2 seconds",
    5 to "5 seconds",
    10 to "10 seconds",
    30 to "30 seconds",
    60 to "1 minute",
    120 to "2 minutes",
    300 to "5 minutes",
)

private val TIMELAPSE_DURATIONS = listOf(
    30 to "30 seconds",
    60 to "1 minute",
    120 to "2 minutes",
    300 to "5 minutes",
    600 to "10 minutes",
    1800 to "30 minutes",
    3600 to "1 hour",
    7200 to "2 hours",
    14400 to "4 hours",
    28800 to "8 hours",
    43200 to "12 hours",
    86400 to "24 hours",
)

@Composable
private fun TimeLapseDialog(onConfirm: (intervalSec: Int, durationSec: Int) -> Unit, onDismiss: () -> Unit) {
    var intervalIndex by remember { mutableIntStateOf(2) } // default: 5 seconds
    var durationIndex by remember { mutableIntStateOf(4) } // default: 10 minutes
    var intervalExpanded by remember { mutableStateOf(false) }
    var durationExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Time Lapse") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Capture one frame from the camera at the selected interval for the selected duration.")

                Box {
                    TextField(
                        value = TIMELAPSE_INTERVALS[intervalIndex].second,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Interval") },
                        modifier = Modifier.fillMaxWidth().clickable { intervalExpanded = true },
                    )
                    DropdownMenu(expanded = intervalExpanded, onDismissRequest = { intervalExpanded = false }) {
                        TIMELAPSE_INTERVALS.forEachIndexed { i, (_, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                intervalIndex = i
                                intervalExpanded = false
                            })
                        }
                    }
                }

                Box {
                    TextField(
                        value = TIMELAPSE_DURATIONS[durationIndex].second,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Duration") },
                        modifier = Modifier.fillMaxWidth().clickable { durationExpanded = true },
                    )
                    DropdownMenu(expanded = durationExpanded, onDismissRequest = { durationExpanded = false }) {
                        TIMELAPSE_DURATIONS.forEachIndexed { i, (_, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                durationIndex = i
                                durationExpanded = false
                            })
                        }
                    }
                }

                val frames = TIMELAPSE_DURATIONS[durationIndex].first / TIMELAPSE_INTERVALS[intervalIndex].first
                Text("$frames frames total", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(TIMELAPSE_INTERVALS[intervalIndex].first, TIMELAPSE_DURATIONS[durationIndex].first)
            }) { Text("Start") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Which part of the region box a drag gesture is manipulating. */
private enum class RegionDragTarget { NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

// Camera-pixel radius around each corner treated as a resize handle, rather than a plain move.
private const val REGION_HANDLE_HIT_PX = 10f

private fun resolveRegionDragTarget(region: Rect, camX: Float, camY: Float): RegionDragTarget {
    fun near(x: Int, y: Int) = hypot((camX - x).toDouble(), (camY - y).toDouble()) <= REGION_HANDLE_HIT_PX
    return when {
        near(region.left, region.top) -> RegionDragTarget.TOP_LEFT
        near(region.right, region.top) -> RegionDragTarget.TOP_RIGHT
        near(region.left, region.bottom) -> RegionDragTarget.BOTTOM_LEFT
        near(region.right, region.bottom) -> RegionDragTarget.BOTTOM_RIGHT
        camX >= region.left && camX <= region.right && camY >= region.top && camY <= region.bottom ->
            RegionDragTarget.MOVE
        else -> RegionDragTarget.NONE
    }
}

private fun applyRegionDrag(region: Rect, target: RegionDragTarget, dx: Float, dy: Float): Rect {
    var left = region.left
    var top = region.top
    var right = region.right
    var bottom = region.bottom
    when (target) {
        RegionDragTarget.MOVE -> {
            left += dx.roundToInt()
            right += dx.roundToInt()
            top += dy.roundToInt()
            bottom += dy.roundToInt()
        }
        RegionDragTarget.TOP_LEFT -> {
            left += dx.roundToInt()
            top += dy.roundToInt()
        }
        RegionDragTarget.TOP_RIGHT -> {
            right += dx.roundToInt()
            top += dy.roundToInt()
        }
        RegionDragTarget.BOTTOM_LEFT -> {
            left += dx.roundToInt()
            bottom += dy.roundToInt()
        }
        RegionDragTarget.BOTTOM_RIGHT -> {
            right += dx.roundToInt()
            bottom += dy.roundToInt()
        }
        RegionDragTarget.NONE -> {}
    }
    return Rect(left, top, right, bottom)
}
