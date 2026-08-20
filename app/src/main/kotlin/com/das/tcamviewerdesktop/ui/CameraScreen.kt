package com.das.tcamviewerdesktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
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
import com.das.tcamviewerdesktop.model.TempSample

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

    Column(modifier = modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
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
                Text("Save Frame")
            }
        }
        Spacer(Modifier.height(12.dp))
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
                    .pointerInput(measurementMode, spotmeterEnabled) {
                        detectTapGestures { offset ->
                            if (measurementMode == MeasurementMode.POINT && spotmeterEnabled) {
                                val camX = (offset.x / DISPLAY_SCALE).toInt().coerceIn(0, Constants.IMAGE_WIDTH - 1)
                                val camY = (offset.y / DISPLAY_SCALE).toInt().coerceIn(0, Constants.IMAGE_HEIGHT - 1)
                                viewModel.setSpotmeter(camX, camY)
                            }
                        }
                    },
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
        TempHistoryChart(tempHistory, modifier = Modifier.fillMaxWidth().height(120.dp))
    }
}

private fun saveCurrentFrame(viewModel: CameraViewModel) {
    val dto = viewModel.currentImageDto.value ?: return
    runCatching { cameraUtils.saveTjsn(dto) }
}

@Composable
private fun TempHistoryChart(samples: List<TempSample>, modifier: Modifier = Modifier) {
    Box(modifier = modifier.border(1.dp, Color.Gray).padding(4.dp)) {
        if (samples.size < 2) {
            Text("Collecting data...", modifier = Modifier.align(Alignment.Center))
            return@Box
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val minV = samples.minOf { it.min }
            val maxV = samples.maxOf { it.max }
            val range = (maxV - minV).let { if (it <= 0f) 1f else it }
            val minT = samples.first().timestampMs
            val maxT = samples.last().timestampMs
            val timeRange = (maxT - minT).let { if (it <= 0L) 1L else it }

            fun point(sample: TempSample): Offset {
                val x = (sample.timestampMs - minT).toFloat() / timeRange * size.width
                val y = size.height - ((sample.spot - minV) / range * size.height)
                return Offset(x, y)
            }

            for (i in 0 until samples.size - 1) {
                drawLine(Color(0xFFE07A3F), point(samples[i]), point(samples[i + 1]), strokeWidth = 2f)
            }
        }
    }
}
