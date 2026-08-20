package com.das.tcamviewerdesktop.model

import com.das.tcamviewerdesktop.constants.Constants

data class CameraConfig(
    val agcEnabled: Boolean = false,
    val emissivity: Int = 90, // percentage 1-100, per tCam's set_config/get_config API
    val gainMode: Int = Constants.GAIN_MODE_HIGH,
)

/** POINT is a single-pixel-neighborhood spotmeter; REGION is a user-resizable box showing
 *  avg/min/max within it. Mutually exclusive — only one overlay/readout at a time. */
enum class MeasurementMode { POINT, REGION }
