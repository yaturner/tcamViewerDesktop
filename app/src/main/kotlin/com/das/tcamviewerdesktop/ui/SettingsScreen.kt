package com.das.tcamviewerdesktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.das.tcamviewerdesktop.constants.Constants
import com.das.tcamviewerdesktop.model.CameraViewModel
import com.das.tcamviewerdesktop.net.DiscoveredCamera
import com.das.tcamviewerdesktop.net.discoverTcamCameras
import com.das.tcamviewerdesktop.paletteFactory
import com.das.tcamviewerdesktop.settingsManager
import com.das.tcamviewerdesktop.util.scanWifiNetworks

/** Settings tab: staged edits (Save/Cancel) over every persisted preference, plus a "Camera
 *  Settings" section (AGC/emissivity/gain, WiFi) that's only meaningful while connected — mirrors
 *  the Android app's SettingsScreen. mDNS "Find tCam Devices" is ported (via JmDNS); WiFi SSID
 *  scanning uses `nmcli` (see [scanWifiNetworks]). */
@Composable
fun SettingsScreen(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
    onUnsavedChangesChanged: (Boolean) -> Unit = {},
) {
    val isConnected by viewModel.isConnected.collectAsState()
    val cameraConfig by viewModel.cameraConfig.collectAsState()

    val savedCameras by settingsManager.savedCamerasFlow.collectAsState()
    val savedAutoConnect by settingsManager.autoConnectFlow.collectAsState()
    val savedIp by settingsManager.cameraIpFlow.collectAsState()
    val savedManualRange by settingsManager.manualRangeFlow.collectAsState()
    val savedMin by settingsManager.minValueFlow.collectAsState()
    val savedMax by settingsManager.maxValueFlow.collectAsState()
    val savedPalette by settingsManager.selectedPaletteFlow.collectAsState()
    val savedShutter by settingsManager.shutterSoundFlow.collectAsState()
    val savedSpotmeter by settingsManager.spotmeterFlow.collectAsState()
    val savedRegion by settingsManager.regionMeasurementFlow.collectAsState()
    val savedAlertEnabled by settingsManager.alertEnabledFlow.collectAsState()
    val savedAlertMetric by settingsManager.alertMetricFlow.collectAsState()
    val savedAlertComparison by settingsManager.alertComparisonFlow.collectAsState()
    val savedAlertThreshold by settingsManager.alertThresholdFlow.collectAsState()
    val savedUnit by settingsManager.temperatureUnitFlow.collectAsState()
    val savedCameraAgc by settingsManager.cameraAgcFlow.collectAsState()
    val savedCameraEmissivity by settingsManager.cameraEmissivityFlow.collectAsState()
    val savedCameraGainMode by settingsManager.cameraGainModeFlow.collectAsState()

    // Incrementing this forces every local field to reinitialize from the saved values (Cancel).
    var resetKey by remember { mutableStateOf(0) }

    var localAutoConnect by remember(savedAutoConnect, resetKey) { mutableStateOf(savedAutoConnect) }
    var localIp by remember(savedIp, resetKey) { mutableStateOf(savedIp) }
    var localManualRange by remember(savedManualRange, resetKey) { mutableStateOf(savedManualRange) }
    var localMin by remember(savedMin, resetKey) { mutableStateOf(savedMin) }
    var localMax by remember(savedMax, resetKey) { mutableStateOf(savedMax) }
    var localPalette by remember(savedPalette, resetKey) { mutableStateOf(savedPalette) }
    var localShutter by remember(savedShutter, resetKey) { mutableStateOf(savedShutter) }
    var localSpotmeter by remember(savedSpotmeter, resetKey) { mutableStateOf(savedSpotmeter) }
    var localRegion by remember(savedRegion, resetKey) { mutableStateOf(savedRegion) }
    var localAlertEnabled by remember(savedAlertEnabled, resetKey) { mutableStateOf(savedAlertEnabled) }
    var localAlertMetric by remember(savedAlertMetric, resetKey) { mutableStateOf(savedAlertMetric) }
    var localAlertComparison by remember(savedAlertComparison, resetKey) { mutableStateOf(savedAlertComparison) }
    var localAlertThreshold by remember(savedAlertThreshold, resetKey) { mutableStateOf(savedAlertThreshold) }
    var localUnit by remember(savedUnit, resetKey) { mutableStateOf(savedUnit) }
    var localAgc by remember(savedCameraAgc, resetKey) { mutableStateOf(savedCameraAgc) }
    var localEmissivity by remember(savedCameraEmissivity, resetKey) { mutableStateOf(savedCameraEmissivity) }
    var localGainMode by remember(savedCameraGainMode, resetKey) { mutableStateOf(savedCameraGainMode) }

    val hasUnsavedChanges =
        localAutoConnect != savedAutoConnect ||
            localIp != savedIp ||
            localManualRange != savedManualRange ||
            localMin != savedMin ||
            localMax != savedMax ||
            localPalette != savedPalette ||
            localShutter != savedShutter ||
            localSpotmeter != savedSpotmeter ||
            localRegion != savedRegion ||
            localAlertEnabled != savedAlertEnabled ||
            localAlertMetric != savedAlertMetric ||
            localAlertComparison != savedAlertComparison ||
            localAlertThreshold != savedAlertThreshold ||
            localUnit != savedUnit ||
            localAgc != savedCameraAgc ||
            localEmissivity != savedCameraEmissivity ||
            localGainMode != savedCameraGainMode
    LaunchedEffect(hasUnsavedChanges) { onUnsavedChangesChanged(hasUnsavedChanges) }

    var showIpChangeConfirm by remember { mutableStateOf(false) }
    var showWifiDialog by remember { mutableStateOf(false) }
    var showDiscoveryDialog by remember { mutableStateOf(false) }

    fun performSave() {
        val cameraAgcOnDevice = cameraConfig?.agcEnabled ?: savedCameraAgc
        settingsManager.saveAutoConnect(localAutoConnect)
        settingsManager.saveCameraIp(localIp)
        settingsManager.saveManualRange(localManualRange)
        settingsManager.saveMinValue(clampManualRangeBound(localMin, localUnit == "Celsius", isMin = true))
        settingsManager.saveMaxValue(clampManualRangeBound(localMax, localUnit == "Celsius", isMin = false))
        settingsManager.saveSelectedPalette(localPalette)
        settingsManager.saveShutterSound(localShutter)
        settingsManager.saveSpotmeter(localSpotmeter)
        settingsManager.saveRegionMeasurement(localRegion)
        settingsManager.saveAlertEnabled(localAlertEnabled)
        settingsManager.saveAlertMetric(localAlertMetric)
        settingsManager.saveAlertComparison(localAlertComparison)
        settingsManager.saveAlertThreshold(localAlertThreshold)
        settingsManager.saveTemperatureUnit(localUnit)
        settingsManager.saveCameraAgc(localAgc)
        settingsManager.saveCameraEmissivity(localEmissivity)
        settingsManager.saveCameraGainMode(localGainMode)
        if (isConnected) {
            val emissivityPct = (localEmissivity.toIntOrNull() ?: 90).coerceIn(1, 100)
            viewModel.sendCameraConfig(localAgc, emissivityPct, localGainMode)
            if (localAgc != cameraAgcOnDevice) viewModel.refreshAfterConfigChange()
        }
        onNavigateBack()
    }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        HorizontalDivider()
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (isConnected) {
                SectionHeader("Camera Settings")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("AGC", modifier = Modifier.weight(1f))
                    Switch(checked = localAgc, onCheckedChange = { localAgc = it })
                }
                TextField(
                    value = localEmissivity,
                    onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 3) localEmissivity = v },
                    label = { Text("Emissivity %") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Gain Mode", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "High" to Constants.GAIN_MODE_HIGH,
                        "Low" to Constants.GAIN_MODE_LOW,
                        "Auto" to Constants.GAIN_MODE_AUTO,
                    ).forEach { (label, mode) ->
                        Row(
                            modifier = Modifier.selectable(selected = localGainMode == mode, onClick = { localGainMode = mode }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = localGainMode == mode, onClick = { localGainMode = mode })
                            Text(label)
                        }
                    }
                }
                OutlinedButton(onClick = {
                    viewModel.fetchWifiInfo()
                    showWifiDialog = true
                }) { Text("WiFi / Network...") }
                HorizontalDivider()
            }

            SectionHeader("Application Settings")

            // Saved Cameras — cameras found by a past Find Devices scan or connected to before.
            // Hidden entirely until there's at least one.
            if (savedCameras.isNotEmpty()) {
                Text("Saved Cameras", style = MaterialTheme.typography.labelLarge)
                Column {
                    savedCameras.forEach { camera ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { localIp = camera.ip }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(camera.name, fontWeight = FontWeight.SemiBold)
                                Text(camera.ip, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { settingsManager.removeSavedCamera(camera.ip) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove ${camera.name}")
                            }
                        }
                    }
                }
            }

            // Auto-connect on launch — connects to the most-recently-used Saved Camera above
            // with no Connect click needed. Off by default; a no-op if the list above is empty.
            SwitchRow("Auto-connect on launch", localAutoConnect) { localAutoConnect = it }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = localIp,
                    onValueChange = { localIp = it },
                    label = { Text("Camera IP Address") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showDiscoveryDialog = true }) {
                    Icon(Icons.Default.Search, contentDescription = "Find tCam devices")
                }
            }

            SwitchRow("Shutter Sound", localShutter) { localShutter = it }
            SwitchRow("Spotmeter", localSpotmeter) { localSpotmeter = it }
            SwitchRow("Region Measurement", localRegion) { localRegion = it }

            Text("Palette", style = MaterialTheme.typography.labelLarge)
            SettingsPaletteDropdown(localPalette) { localPalette = it }

            Text("Units", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                listOf("Celsius" to "°C", "Fahrenheit" to "°F").forEach { (unitName, label) ->
                    Row(
                        modifier = Modifier.selectable(selected = localUnit == unitName, onClick = { localUnit = unitName }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = localUnit == unitName, onClick = { localUnit = unitName })
                        Text(label)
                    }
                }
            }

            SwitchRow("Manual Range", localManualRange) { localManualRange = it }
            if (localManualRange) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TextField(
                        value = localMin,
                        onValueChange = { localMin = it },
                        label = { Text("Min") },
                        modifier = Modifier.weight(1f),
                    )
                    TextField(
                        value = localMax,
                        onValueChange = { localMax = it },
                        label = { Text("Max") },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            SwitchRow("Temperature Alert", localAlertEnabled) { localAlertEnabled = it }
            if (localAlertEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    listOf("Spot", "Max", "Min").forEach { metric ->
                        Row(
                            modifier = Modifier.selectable(selected = localAlertMetric == metric, onClick = { localAlertMetric = metric }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = localAlertMetric == metric, onClick = { localAlertMetric = metric })
                            Text(metric)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    listOf("Above", "Below").forEach { comparison ->
                        Row(
                            modifier = Modifier.selectable(
                                selected = localAlertComparison == comparison,
                                onClick = { localAlertComparison = comparison },
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = localAlertComparison == comparison, onClick = { localAlertComparison = comparison })
                            Text(comparison)
                        }
                    }
                }
                TextField(
                    value = localAlertThreshold,
                    onValueChange = { localAlertThreshold = it },
                    label = { Text(if (localUnit == "Celsius") "Threshold (°C)" else "Threshold (°F)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = {
                resetKey++
                onNavigateBack()
            }) { Text("Cancel") }
            Button(
                onClick = {
                    if (isConnected && localIp != savedIp) showIpChangeConfirm = true else performSave()
                },
            ) { Text("Save") }
        }
    }

    if (showIpChangeConfirm) {
        AlertDialog(
            onDismissRequest = { showIpChangeConfirm = false },
            title = { Text("Change IP Address") },
            text = { Text("This will disconnect the camera.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.toggleConnection()
                    showIpChangeConfirm = false
                    performSave()
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showIpChangeConfirm = false }) { Text("Cancel") } },
        )
    }

    if (showWifiDialog) {
        WifiConfigDialog(viewModel = viewModel, onDismiss = { showWifiDialog = false })
    }

    if (showDiscoveryDialog) {
        DiscoveryDialog(
            onDismiss = { showDiscoveryDialog = false },
            onSelect = { ip ->
                localIp = ip
                showDiscoveryDialog = false
            },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsPaletteDropdown(current: String, onSelect: (String) -> Unit) {
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

/** WiFi configuration dialog — SSID/password/static-IP fields, with an SSID-scan button backed
 *  by `nmcli` (see [scanWifiNetworks]). Saving restarts the camera's WiFi subsystem. */
@Composable
private fun WifiConfigDialog(viewModel: CameraViewModel, onDismiss: () -> Unit) {
    val wifiInfo by viewModel.wifiInfo.collectAsState()
    var isAccessPoint by remember { mutableStateOf(true) }
    var ssid by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var useStaticIp by remember { mutableStateOf(false) }
    var staticIp by remember { mutableStateOf("") }
    var staticNetmask by remember { mutableStateOf("") }
    var showSaveConfirm by remember { mutableStateOf(false) }
    var showSsidScanDialog by remember { mutableStateOf(false) }

    LaunchedEffect(wifiInfo) {
        val info = wifiInfo ?: return@LaunchedEffect
        val flags = info["flags"]?.toIntOrNull() ?: 0
        val isClientMode = (flags and Constants.WIFI_MASK_CLIENT_MODE) != 0
        isAccessPoint = !isClientMode
        useStaticIp = (flags and Constants.WIFI_MASK_STATIC_IP) != 0
        ssid = if (isClientMode) info["sta_ssid"].orEmpty() else info["ap_ssid"].orEmpty()
        staticIp = info["sta_ip_addr"].orEmpty()
        staticNetmask = info["sta_netmask"].orEmpty()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("WiFi Settings") },
        text = {
            if (wifiInfo == null) {
                CircularProgressIndicator()
            } else {
                Column(
                    modifier = Modifier.widthIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Camera is Access Point", modifier = Modifier.weight(1f))
                        Switch(checked = isAccessPoint, onCheckedChange = { isAccessPoint = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextField(
                            value = ssid,
                            onValueChange = { ssid = it },
                            label = { Text("SSID") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { showSsidScanDialog = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Scan for networks")
                        }
                    }
                    TextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Use Static IP when Client", modifier = Modifier.weight(1f))
                        Switch(checked = useStaticIp, onCheckedChange = { useStaticIp = it })
                    }
                    TextField(value = staticIp, onValueChange = { staticIp = it }, label = { Text("Client Static IP") }, singleLine = true)
                    TextField(
                        value = staticNetmask,
                        onValueChange = { staticNetmask = it },
                        label = { Text("Client Static Netmask") },
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = wifiInfo != null, onClick = { showSaveConfirm = true }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (showSaveConfirm) {
        AlertDialog(
            onDismissRequest = { showSaveConfirm = false },
            title = { Text("Save WiFi Settings?") },
            text = { Text("This will disconnect the camera. It will attempt to reconnect on the new network.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.sendWifiConfig(isAccessPoint, ssid, password, useStaticIp, staticIp, staticNetmask)
                    val reconnectIp = when {
                        isAccessPoint -> wifiInfo?.get("ap_ip_addr") ?: "192.168.4.1"
                        useStaticIp -> staticIp
                        else -> null
                    }
                    viewModel.reconnectAfterWifiChange(reconnectIp)
                    showSaveConfirm = false
                    onDismiss()
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showSaveConfirm = false }) { Text("Cancel") } },
        )
    }

    if (showSsidScanDialog) {
        SsidScanDialog(
            onDismiss = { showSsidScanDialog = false },
            onSelect = { picked ->
                ssid = picked
                showSsidScanDialog = false
            },
        )
    }
}

/** Lists nearby WiFi networks visible to this machine's own radio (via `nmcli`), so the SSID can
 *  be picked instead of typed. */
@Composable
private fun SsidScanDialog(onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    var networks by remember { mutableStateOf<List<String>>(emptyList()) }
    var isScanning by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        isScanning = true
        networks = scanWifiNetworks()
        isScanning = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Available Networks") },
        text = {
            Column(modifier = Modifier.widthIn(max = 400.dp)) {
                if (isScanning) {
                    CircularProgressIndicator(modifier = Modifier.padding(bottom = 12.dp))
                }
                if (networks.isEmpty()) {
                    Text(if (isScanning) "Scanning for networks…" else "No networks found.")
                } else {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        networks.forEach { network ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(selected = selected == network, onClick = { selected = network })
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = selected == network, onClick = { selected = network })
                                Text(network)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = selected != null, onClick = { selected?.let(onSelect) }) { Text("Select") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** mDNS "Find tCam Devices" dialog — searches for [Constants.SERVICE_TYPE] on the local network
 *  via [discoverTcamCameras] (JmDNS) and lets the user pick a discovered camera's IP. */
@Composable
private fun DiscoveryDialog(onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    var devices by remember { mutableStateOf<List<DiscoveredCamera>>(emptyList()) }
    var isDiscovering by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<DiscoveredCamera?>(null) }

    LaunchedEffect(Unit) {
        isDiscovering = true
        devices = discoverTcamCameras(timeoutMs = 8_000L)
        // Remember every camera this scan found, whether or not the user ends up selecting it —
        // matches tcamViewer2's Find Devices behavior of building up the Saved Cameras list from
        // every resolve, not just the one eventually chosen.
        devices.forEach { settingsManager.upsertSavedCamera(name = it.name, ip = it.ip) }
        isDiscovering = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Find tCam Devices") },
        text = {
            Column(modifier = Modifier.widthIn(max = 400.dp)) {
                if (isDiscovering) {
                    CircularProgressIndicator(modifier = Modifier.padding(bottom = 12.dp))
                }
                if (devices.isEmpty()) {
                    Text(if (isDiscovering) "Searching for cameras on your network…" else "No tCam devices found.")
                } else {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        devices.forEach { device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(selected = selected == device, onClick = { selected = device })
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = selected == device, onClick = { selected = device })
                                Column {
                                    Text(device.name, fontWeight = FontWeight.SemiBold)
                                    Text(device.ip, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = selected != null, onClick = { selected?.let { onSelect(it.ip) } }) { Text("Done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

internal fun clampManualRangeBound(value: String, isCelsiusUnit: Boolean, isMin: Boolean): String {
    val floatValue = value.toFloatOrNull() ?: return value
    return if (isMin) {
        val floor = if (isCelsiusUnit) -273f else (-273f * 9f / 5f + 32f)
        if (floatValue < floor) floor.toString() else value
    } else {
        val ceiling = if (isCelsiusUnit) 999f else (999f * 9f / 5f + 32f)
        if (floatValue > ceiling) ceiling.toString() else value
    }
}
