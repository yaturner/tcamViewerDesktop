package com.das.tcamviewerdesktop.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.das.tcamviewerdesktop.model.CameraViewModel
import kotlinx.coroutines.flow.collectLatest

private enum class Screen(val label: String) { CAMERA("Camera"), SETTINGS("Settings"), LIBRARY("Library") }

@Composable
fun App(viewModel: CameraViewModel) {
    var screen by remember { mutableStateOf(Screen.CAMERA) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail {
                    NavigationRailItem(
                        selected = screen == Screen.CAMERA,
                        onClick = { screen = Screen.CAMERA },
                        icon = { Icon(Icons.Filled.Videocam, contentDescription = Screen.CAMERA.label) },
                        label = { Text(Screen.CAMERA.label) },
                    )
                    NavigationRailItem(
                        selected = screen == Screen.SETTINGS,
                        onClick = { screen = Screen.SETTINGS },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = Screen.SETTINGS.label) },
                        label = { Text(Screen.SETTINGS.label) },
                    )
                    NavigationRailItem(
                        selected = screen == Screen.LIBRARY,
                        onClick = { screen = Screen.LIBRARY },
                        icon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = Screen.LIBRARY.label) },
                        label = { Text(Screen.LIBRARY.label) },
                    )
                }
                when (screen) {
                    Screen.CAMERA -> CameraScreen(viewModel, modifier = Modifier.fillMaxSize())
                    Screen.SETTINGS -> SettingsScreen(viewModel, modifier = Modifier.fillMaxSize())
                    Screen.LIBRARY -> LibraryScreen(modifier = Modifier.fillMaxSize())
                }
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
