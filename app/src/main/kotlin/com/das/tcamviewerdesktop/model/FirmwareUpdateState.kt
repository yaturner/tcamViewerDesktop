package com.das.tcamviewerdesktop.model

import com.das.tcamviewerdesktop.util.FirmwareInfo

/** Result of "check for new firmware": what the connected camera runs and the latest release
 *  for its family on danjuliodesigns.com. Null [latest] with an [error] means the check failed. */
data class FirmwareCheck(
    val checking: Boolean = true,
    val cameraName: String = "",
    val model: Int = 0,
    val installedVersion: String = "",
    val latest: FirmwareInfo? = null,
    val error: String? = null,
)

/** Progress of an OTA firmware update. */
sealed interface FirmwareUpdateState {
    data object Idle : FirmwareUpdateState

    data object Downloading : FirmwareUpdateState

    /** Sent fw_update_request; waiting for the user to confirm on the camera itself. */
    data class WaitingForConfirmation(val isFullTcam: Boolean, val version: String) : FirmwareUpdateState

    data class Transferring(val bytesSent: Int, val totalBytes: Int) : FirmwareUpdateState

    data class Succeeded(val message: String) : FirmwareUpdateState

    data class Failed(val message: String) : FirmwareUpdateState
}
