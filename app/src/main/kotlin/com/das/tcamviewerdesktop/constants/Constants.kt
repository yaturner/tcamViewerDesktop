package com.das.tcamviewerdesktop.constants

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

object Constants {
    const val GAIN_MODE_HIGH: Int = 0
    const val GAIN_MODE_LOW: Int = 1
    const val GAIN_MODE_AUTO: Int = 2

    val IP_PATTERN: Pattern = Pattern.compile(
        "^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$",
    )
    val sdf: SimpleDateFormat = SimpleDateFormat("MM/dd/yy HH:mm:ss", Locale.getDefault())
    val sdfRecording: SimpleDateFormat =
        SimpleDateFormat("MM/dd/yy HH:mm:ss.SSS", Locale.getDefault())
    val simpleDateFormatFolder: SimpleDateFormat =
        SimpleDateFormat("MM_dd_yyyy", Locale.getDefault())
    val simpleDateFormatFile: SimpleDateFormat = SimpleDateFormat("HH_mm_ss", Locale.getDefault())

    const val COLORBAR_WIDTH: Int = 48
    const val COLORBAR_HEIGHT: Int = 256
    const val HISTOGRAM_WIDTH: Int = 256

    const val BUFFER_LENGTH: Int = 65536
    const val RECORDING_FOOTER_LENGTH: Int = 147

    const val SUCCESS: String = "{\"result\":\"OK\"}"
    const val ERROR: String = "{\"result\":\"ERROR\"}"

    const val REQUEST_WRITE_PERMISSION: Int = 2001
    const val RESULT_CODE_CREATE_DOCUMENT: Int = 3001

    const val IMAGE_WIDTH: Int = 160
    const val IMAGE_HEIGHT: Int = 120

    // Keys for Save/Restore instance
    const val KEY_CAMERAUTILS: String = "CameraUtils"
    const val KEY_UTILS: String = "Utils"
    const val KEY_SETTINGS: String = "Settings"
    const val KEY_CAMERA_SERVICE: String = "CameraService"

    // Settings keys for SharedPrefs
    const val KEY_AGC: String = "agc"
    const val KEY_EMISSIVITY: String = "emissivity"
    const val KEY_GAIN_AUTO: String = "gainAuto"
    const val KEY_GAIN_HIGH: String = "gainHigh"
    const val KEY_GAIN_LOW: String = "gainLow"
    const val KEY_CAMERA_IP_ADDRESS: String = "cam_address"
    const val KEY_EXPORT_PICTURE_ON_SAVE: String = "export_on_save"
    const val KEY_EXPORT_METADATA: String = "export_metadata"
    const val KEY_EXPORT_RESOLUTION: String = "export_resolution"
    const val KEY_MANUAL_RANGE: String = "manual_range"
    const val KEY_MANUAL_RANGE_MAX: String = "manual_range_max"
    const val KEY_MANUAL_RANGE_MIN: String = "manual_range_min"
    const val KEY_PALETTE: String = "palette"
    const val KEY_SHUTTER_SOUND: String = "shutter_sound"
    const val KEY_SPOTMETER: String = "spotmeter"
    const val KEY_UNITS_F: String = "unitsF"
    const val KEY_UNITS_C: String = "unitsC"

    const val KEY_WIFI_ACCESSPOINT: String = "access_point"
    const val KEY_WIFI_SSID: String = "ssid"
    const val KEY_WIFI_PASSWORD: String = "password"
    const val KEY_WIFI_STATICIP: String = "static_ip"
    const val KEY_WIFI_STATICIPADDRESS: String = "static_ip_address"
    const val KEY_WIFI_STATICNETMASK: String = "static_ip_netmask"

    // Bundle keys
    const val KEY_IS_CAMERA_CONNECTED: String = "cam_connected"
    const val KEY_IS_SOCKET_CONNECTED: String = "soc_connected"
    const val KEY_CAMERA_IMAGE: String = "cam_image"
    const val KEY_SELECTED_PALETTE: String = "pal_selected"
    const val KEY_SELECTED_IMAGE: String = "image_selected"

    // Record Summary Footer
    val RECORDING_FOOTER: String =
        "{\"video_info\":{\"start_time\":\"%12s\",\"start_date\":\"%8s\",\"end_time\":\"%12s\"," +
            "\"end_date\":\"%8s\",\"num_frames\":%3d,\"version\":%2d}}\u0003"

    // Camera Commands
    const val CMD_GET_STATUS: String = "\u0002{\"cmd\":\"get_status\"}\u0003"
    const val CMD_GET_CONFIG: String = "\u0002{\"cmd\":\"get_config\"}\u0003"
    const val CMD_GET_WIFI: String = "\u0002{\"cmd\":\"get_wifi\"}\u0003"
    const val CMD_SET_TIME: String = "\u0002{\"cmd\":\"set_time\", \"args\": %s}\u0003"
    const val CMD_SET_CONFIG: String = "\u0002{\"cmd\":\"set_config\", \"args\": %s}\u0003"
    const val CMD_SET_SPOTMETER: String = "\u0002{\"cmd\":\"set_spotmeter\", \"args\": %s\n}\u0003"
    const val CMD_SET_STREAM_ON: String = "\u0002{\"cmd\":\"stream_on\", \"args\": %s}\u0003"
    const val CMD_SET_STREAM_OFF: String = "\u0002{\"cmd\":\"stream_off\"}\u0003"
    const val CMD_SET_WIFI: String = "\u0002{\"cmd\":\"set_wifi\", \"args\": %s}\u0003"
    const val CMD_GET_IMAGE: String = "\u0002{\"cmd\":\"get_image\"}\u0003"
    const val CMD_RUN_FFC: String = "\u0002{\"cmd\":\"run_ffc\"}\u0003"

    // Camera Command args
    val ARGS_SET_TIME: String = "{" +
        "    \"sec\":  %d," +
        "    \"min\":  %d," +
        "    \"hour\": %d," +
        "    \"dow\":  %d," +
        "    \"day\":  %d," +
        "    \"mon\":  %d," +
        "    \"year\": %d" +
        "   }"
    val ARGS_SET_CONFIG: String = "{\n" +
        "    \"agc_enabled\": %d,\n" +
        "    \"emissivity\": %d,\n" +
        "    \"gain_mode\": %d\n" +
        "  }"
    val ARGS_SET_SPOTMETER: String = "{\n" +
        "    \"c1\": %d,\n" +
        "    \"c2\": %d,\n" +
        "    \"r1\": %d,\n" +
        "    \"r2\": %d \n" +
        "  }"
    val ARGS_SET_STREAM_ON: String = "{\n" +
        "    \"delay_msec\":%d,\n" +
        "    \"num_frames\":%d\n" +
        "   }"

    // If Camera is Access Point, send
    val ARGS_SET_WIFI_AP: String = "{\n" +
        "    \"ap_ssid\": \"%s\",\n" +
        "    \"ap_pw\": \"%s\",\n" +
        "    \"flags\": 1\n" +
        "    }"

    // If Camera is NOT Access Point and NOT Use static IP when Client, send
    val ARGS_SET_WIFI_NOT_STATIC: String = "{\n" +
        "    \"sta_ssid\": \"%s\",\n" +
        "    \"sta_pw\": \"%s\",\n" +
        "    \"flags\": 129\n" +
        "    }"

    // If Camera is NOT Access Point and Use static IP when Client, send
    val ARGS_SET_WIFI_STATIC: String = "{\n" +
        "    \"sta_ssid\": \"%s\",\n" +
        "    \"sta_pw\": \"%s\",\n" +
        "    \"sta_ip_addr\": \"%s\",\n" +
        "    \"sta_netmask\": \"%s\",\n" +
        "    \"flags\": 145\n" +
        "    }"

    val TELEMETRY_MASK_AGC: Int = (1 shl 12)
    val TELEMETRY_MASK_SHUTDOWN: Int = (1 shl 20)
    val WIFI_MASK_CLIENT_MODE: Int = (1 shl 7)
    val WIFI_MASK_STATIC_IP: Int = (1 shl 4)
    const val WIFI_MASK_WIFI_ENABLED: Int = 1

    const val SORT_ORDER_ASCENDING: Int = 1
    const val SORT_ORDER_DESCENDING: Int = 2

    const val ROTATE_FORWARD: Int = 1
    val ROTATE_BACKWARD: Int = -1
    val ERROR_RESPONSE: String = "{\n" +
        "\"error\":{\n" +
        "\"exception\":\"%s\"\n" +
        "}\n" +
        "}"
    val CONNECTED_RESPONSE: String = "{\n" +
        "\"connected\":{\n" +
        "\"result\":\"%s\"\n" +
        "}\n" +
        "}"

    // Constants for playback fragment
    var PLAYBACK_ACTION: String = "playback_action"
    const val PLAYBACK_ACTION_PLAY: Int = 0
    const val PLAYBACK_ACTION_ANALYZE: Int = 1
    const val PLAYBACK_ACTION_SAVE: Int = 2

    // mNDS
    const val SERVICE_TYPE: String = "_tcam-socket._tcp."
}
