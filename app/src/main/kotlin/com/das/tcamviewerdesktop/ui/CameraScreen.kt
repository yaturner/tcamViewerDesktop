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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
fun CameraScreen(viewModel: CameraViewModel, modifier: Modifier = Modifier) {
    val isConnected by viewModel.isConnected.collectAsState()
    val isConnecting by viewModel.isConnecting.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()

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
            Button(onClick = { viewModel.getImage() }, enabled = isConnected) {
                Icon(Icons.Filled.Camera, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Get")
            }
            Button(onClick = { viewModel.toggleStreaming() }, enabled = isConnected) {
                Text(if (isStreaming) "Stop Stream" else "Stream")
            }
            Button(onClick = { viewModel.toggleRecording() }, enabled = isConnected) {
                Text(if (isRecording) "Stop Recording" else "Record")
            }
            Button(onClick = { saveCurrentFrame(viewModel) }, enabled = isConnected) {
                Text("Save")
            }
        }
    }
}

private fun saveCurrentFrame(viewModel: CameraViewModel) {
    val dto = viewModel.currentImageDto.value ?: return
    runCatching { cameraUtils.saveTjsn(dto) }
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
