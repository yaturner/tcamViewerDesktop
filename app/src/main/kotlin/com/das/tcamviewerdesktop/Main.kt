package com.das.tcamviewerdesktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.das.tcamviewerdesktop.factory.PaletteFactory
import com.das.tcamviewerdesktop.model.CameraViewModel
import com.das.tcamviewerdesktop.net.CameraService
import com.das.tcamviewerdesktop.ui.App
import com.das.tcamviewerdesktop.util.CameraUtils
import com.das.tcamviewerdesktop.util.SettingsManager

fun main() {
    // Mirrors tcamViewer2's MainActivity.onCreate() global-singleton init.
    cameraService = CameraService()
    cameraUtils = CameraUtils()
    paletteFactory = PaletteFactory()
    settingsManager = SettingsManager()

    val viewModel = CameraViewModel()

    application {
        Window(
            onCloseRequest = {
                viewModel.close()
                cameraService.shutdown()
                exitApplication()
            },
            title = "tCam Viewer (Desktop)",
        ) {
            App(viewModel)
        }
    }
}
