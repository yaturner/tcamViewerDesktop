package com.das.tcamviewerdesktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.das.tcamviewerdesktop.constants.Constants
import com.das.tcamviewerdesktop.model.CameraViewModel
import com.das.tcamviewerdesktop.model.MeasurementMode
import com.das.tcamviewerdesktop.paletteFactory
import com.das.tcamviewerdesktop.settingsManager
import kotlinx.coroutines.flow.collectLatest

private const val DISPLAY_SCALE = 4

@Composable
fun App(viewModel: CameraViewModel) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxSize()) {
                LiveView(viewModel, modifier = Modifier.weight(1f))
                ControlPanel(viewModel, modifier = Modifier.width(360.dp).fillMaxSize().verticalScroll(rememberScrollState()))
            }
        }

        val showConnectError by viewModel.showConnectError.collectAsState()
        if (showConnectError) {
            AlertDialog(
                onDismissRequest = viewModel::dismissConnectError,
                confirmButton = { TextButton(onClick = viewModel::dismissConnectError) { Text("OK") } },
                title = { Text("Connection Failed") },
                text = { Text("Could not connect to the camera. Check the IP address and that it's powered on.") },
            )
        }

        var alertText by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) {
            viewModel.alertMessage.collectLatest { alertText = it }
        }
        alertText?.let { msg ->
            AlertDialog(
                onDismissRequest = { alertText = null },
                confirmButton = { TextButton(onClick = { alertText = null }) { Text("OK") } },
                title = { Text("Temperature Alert") },
                text = { Text(msg) },
            )
        }

        var timeLapseText by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) {
            viewModel.timeLapseMessage.collectLatest { timeLapseText = it }
        }
        timeLapseText?.let { msg ->
            AlertDialog(
                onDismissRequest = { timeLapseText = null },
                confirmButton = { TextButton(onClick = { timeLapseText = null }) { Text("OK") } },
                title = { Text("Time Lapse") },
                text = { Text(msg) },
            )
        }
    }
}

@Composable
private fun LiveView(viewModel: CameraViewModel, modifier: Modifier = Modifier) {
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
                            size = androidx.compose.ui.geometry.Size(half * 2, half * 2),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f),
                        )
                        drawRect(
                            color = Color.White,
                            topLeft = Offset(cx - half, cy - half),
                            size = androidx.compose.ui.geometry.Size(half * 2, half * 2),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f),
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
                                androidx.compose.ui.geometry.Size(
                                    rect.width * DISPLAY_SCALE.toFloat(),
                                    rect.height * DISPLAY_SCALE.toFloat(),
                                ),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
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
        TempHistoryChart(tempHistory, modifier = Modifier.fillMaxWidth().height(120.dp))
    }
}

@Composable
private fun TempHistoryChart(samples: List<com.das.tcamviewerdesktop.model.TempSample>, modifier: Modifier = Modifier) {
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

            fun point(sample: com.das.tcamviewerdesktop.model.TempSample): Offset {
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

@Composable
private fun ControlPanel(viewModel: CameraViewModel, modifier: Modifier = Modifier) {
    val isConnected by viewModel.isConnected.collectAsState()
    val isConnecting by viewModel.isConnecting.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val currentPalette by viewModel.currentPalette.collectAsState()
    val cameraConfig by viewModel.cameraConfig.collectAsState()
    val isCelsius by viewModel.isCelsius.collectAsState()
    val spotmeterEnabled by viewModel.spotmeterEnabled.collectAsState()
    val measurementMode by viewModel.measurementMode.collectAsState()

    var ip by remember { mutableStateOf(settingsManager.getCameraIp()) }

    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Connection", style = MaterialTheme.typography.titleMedium)
        TextField(value = ip, onValueChange = { ip = it }, label = { Text("Camera IP") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = {
                settingsManager.saveCameraIp(ip)
                viewModel.toggleConnection()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when {
                    isConnecting -> "Connecting..."
                    isConnected -> "Disconnect"
                    else -> "Connect"
                },
            )
        }

        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.getImage() }, enabled = isConnected) {
                Icon(Icons.Filled.Camera, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Get")
            }
            Button(onClick = { viewModel.toggleStreaming() }, enabled = isConnected) {
                Text(if (isStreaming) "Stop Stream" else "Stream")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.toggleRecording() }, enabled = isConnected) {
                Text(if (isRecording) "Stop Recording" else "Record")
            }
            Button(
                onClick = { saveCurrentFrame(viewModel) },
                enabled = isConnected,
            ) {
                Text("Save Frame")
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("Palette", style = MaterialTheme.typography.titleMedium)
        PaletteDropdown(currentPalette) { viewModel.setPalette(it) }

        Spacer(Modifier.height(8.dp))
        Text("Units", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("°C")
            Checkbox(
                checked = !isCelsius,
                onCheckedChange = { settingsManager.saveTemperatureUnit(if (it) "Fahrenheit" else "Celsius") },
            )
            Text("°F")
        }

        Spacer(Modifier.height(8.dp))
        Text("Measurement", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = spotmeterEnabled, onCheckedChange = { settingsManager.saveSpotmeter(it) })
            Text("Spotmeter enabled")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = measurementMode == MeasurementMode.REGION,
                onCheckedChange = { settingsManager.saveRegionMeasurement(it) },
            )
            Text("Region measurement")
        }

        Spacer(Modifier.height(8.dp))
        Text("Manual Range", style = MaterialTheme.typography.titleMedium)
        ManualRangeControls()

        Spacer(Modifier.height(8.dp))
        Text("Camera Config", style = MaterialTheme.typography.titleMedium)
        CameraConfigControls(viewModel, cameraConfig)

        Spacer(Modifier.height(8.dp))
        Row {
            IconButton(onClick = { viewModel.clearChartHistory() }) {
                Icon(Icons.Filled.Clear, contentDescription = "Clear temperature history")
            }
            Text("Clear temperature history", modifier = Modifier.align(Alignment.CenterVertically))
        }
    }
}

private fun saveCurrentFrame(viewModel: CameraViewModel) {
    val dto = viewModel.currentImageDto.value ?: return
    runCatching { com.das.tcamviewerdesktop.cameraUtils.saveTjsn(dto) }
}

@Composable
private fun ManualRangeControls() {
    var enabled by remember { mutableStateOf(settingsManager.manualRangeFlow.value) }
    var min by remember { mutableStateOf(settingsManager.minValueFlow.value) }
    var max by remember { mutableStateOf(settingsManager.maxValueFlow.value) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = enabled,
            onCheckedChange = {
                enabled = it
                settingsManager.saveManualRange(it)
            },
        )
        Text("Manual range")
    }
    if (enabled) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(
                value = min,
                onValueChange = {
                    min = it
                    settingsManager.saveMinValue(it)
                },
                label = { Text("Min") },
                modifier = Modifier.weight(1f),
            )
            TextField(
                value = max,
                onValueChange = {
                    max = it
                    settingsManager.saveMaxValue(it)
                },
                label = { Text("Max") },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CameraConfigControls(viewModel: CameraViewModel, config: com.das.tcamviewerdesktop.model.CameraConfig?) {
    var agc by remember(config) { mutableStateOf(config?.agcEnabled ?: false) }
    var emissivity by remember(config) { mutableStateOf((config?.emissivity ?: 90).toString()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = agc, onCheckedChange = { agc = it })
        Text("AGC")
    }
    TextField(
        value = emissivity,
        onValueChange = { emissivity = it },
        label = { Text("Emissivity %") },
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = {
            val em = emissivity.toIntOrNull()?.coerceIn(1, 100) ?: 90
            viewModel.sendCameraConfig(agc, em, config?.gainMode ?: 0)
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Send Config")
    }
}

@Composable
private fun PaletteDropdown(current: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val names = remember { paletteFactory.paletteNames.filterNotNull() }
    Box {
        TextField(
            value = current,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            names.forEach { name ->
                DropdownMenuItem(text = { Text(name) }, onClick = {
                    onSelect(name)
                    expanded = false
                })
            }
        }
    }
}
