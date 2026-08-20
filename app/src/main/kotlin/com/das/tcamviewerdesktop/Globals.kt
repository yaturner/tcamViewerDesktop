package com.das.tcamviewerdesktop

import com.das.tcamviewerdesktop.factory.PaletteFactory
import com.das.tcamviewerdesktop.net.CameraService
import com.das.tcamviewerdesktop.util.CameraUtils
import com.das.tcamviewerdesktop.util.SettingsManager

// Mirrors the file-level global singleton pattern used in tcamViewer2's MainActivity.kt —
// initialized once in main() before the Compose window is created.
lateinit var cameraService: CameraService
lateinit var cameraUtils: CameraUtils
lateinit var paletteFactory: PaletteFactory
lateinit var settingsManager: SettingsManager
