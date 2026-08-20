package com.das.tcamviewerdesktop.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.prefs.Preferences

/** Desktop stand-in for tcamViewer2's DataStore-backed SettingsDataManager — same key set and
 *  Flow-based read API, backed by java.util.prefs.Preferences (a per-OS-user settings store,
 *  the closest desktop-JVM equivalent to Android's DataStore) instead of DataStore. Each setting
 *  is a MutableStateFlow seeded from Preferences on construction; save*() writes through to
 *  Preferences and updates the flow immediately so collectors see it without a reload. */
class SettingsManager {
    private val prefs = Preferences.userRoot().node("com/das/tcamviewerdesktop")

    private fun stringFlow(key: String, default: String): MutableStateFlow<String> =
        MutableStateFlow(prefs.get(key, default))

    private fun boolFlow(key: String, default: Boolean): MutableStateFlow<Boolean> =
        MutableStateFlow(prefs.getBoolean(key, default))

    private val _cameraIp = stringFlow("camera_ip", "192.168.4.1")
    val cameraIpFlow: StateFlow<String> = _cameraIp.asStateFlow()

    private val _selectedPalette = stringFlow("selected_palette", "Rainbow")
    val selectedPaletteFlow: StateFlow<String> = _selectedPalette.asStateFlow()

    private val _manualRange = boolFlow("manual_range", false)
    val manualRangeFlow: StateFlow<Boolean> = _manualRange.asStateFlow()

    private val _minValue = stringFlow("min_value", "0")
    val minValueFlow: StateFlow<String> = _minValue.asStateFlow()

    private val _maxValue = stringFlow("max_value", "100")
    val maxValueFlow: StateFlow<String> = _maxValue.asStateFlow()

    private val _temperatureUnit = stringFlow("temperature_unit", "Celsius")
    val temperatureUnitFlow: StateFlow<String> = _temperatureUnit.asStateFlow()

    private val _shutterSound = boolFlow("shutter_sound", true)
    val shutterSoundFlow: StateFlow<Boolean> = _shutterSound.asStateFlow()

    private val _spotmeter = boolFlow("spotmeter", true)
    val spotmeterFlow: StateFlow<Boolean> = _spotmeter.asStateFlow()

    private val _regionMeasurement = boolFlow("region_measurement", false)
    val regionMeasurementFlow: StateFlow<Boolean> = _regionMeasurement.asStateFlow()

    private val _alertEnabled = boolFlow("alert_enabled", false)
    val alertEnabledFlow: StateFlow<Boolean> = _alertEnabled.asStateFlow()

    private val _alertMetric = stringFlow("alert_metric", "Spot")
    val alertMetricFlow: StateFlow<String> = _alertMetric.asStateFlow()

    private val _alertComparison = stringFlow("alert_comparison", "Above")
    val alertComparisonFlow: StateFlow<String> = _alertComparison.asStateFlow()

    private val _alertThreshold = stringFlow("alert_threshold", "100")
    val alertThresholdFlow: StateFlow<String> = _alertThreshold.asStateFlow()

    private val _cameraAgc = boolFlow("camera_agc", false)
    val cameraAgcFlow: StateFlow<Boolean> = _cameraAgc.asStateFlow()

    private val _cameraEmissivity = stringFlow("camera_emissivity", "90")
    val cameraEmissivityFlow: StateFlow<String> = _cameraEmissivity.asStateFlow()

    private val _cameraGainMode = MutableStateFlow(prefs.getInt("camera_gain_mode", 0))
    val cameraGainModeFlow: StateFlow<Int> = _cameraGainMode.asStateFlow()

    fun saveCameraIp(ip: String) { prefs.put("camera_ip", ip); _cameraIp.value = ip }

    fun saveSelectedPalette(palette: String) { prefs.put("selected_palette", palette); _selectedPalette.value = palette }

    fun saveManualRange(enabled: Boolean) { prefs.putBoolean("manual_range", enabled); _manualRange.value = enabled }

    fun saveMinValue(value: String) { prefs.put("min_value", value); _minValue.value = value }

    fun saveMaxValue(value: String) { prefs.put("max_value", value); _maxValue.value = value }

    fun saveTemperatureUnit(unit: String) { prefs.put("temperature_unit", unit); _temperatureUnit.value = unit }

    fun saveShutterSound(enabled: Boolean) { prefs.putBoolean("shutter_sound", enabled); _shutterSound.value = enabled }

    fun saveSpotmeter(enabled: Boolean) { prefs.putBoolean("spotmeter", enabled); _spotmeter.value = enabled }

    fun saveRegionMeasurement(enabled: Boolean) {
        prefs.putBoolean("region_measurement", enabled)
        _regionMeasurement.value = enabled
    }

    fun saveAlertEnabled(enabled: Boolean) { prefs.putBoolean("alert_enabled", enabled); _alertEnabled.value = enabled }

    fun saveAlertMetric(metric: String) { prefs.put("alert_metric", metric); _alertMetric.value = metric }

    fun saveAlertComparison(comparison: String) {
        prefs.put("alert_comparison", comparison)
        _alertComparison.value = comparison
    }

    fun saveAlertThreshold(value: String) { prefs.put("alert_threshold", value); _alertThreshold.value = value }

    fun saveCameraAgc(enabled: Boolean) { prefs.putBoolean("camera_agc", enabled); _cameraAgc.value = enabled }

    fun saveCameraEmissivity(value: String) {
        prefs.put("camera_emissivity", value)
        _cameraEmissivity.value = value
    }

    fun saveCameraGainMode(mode: Int) { prefs.putInt("camera_gain_mode", mode); _cameraGainMode.value = mode }

    fun getCameraIp(): String = _cameraIp.value

    fun isUnitsCelsius(): Boolean = _temperatureUnit.value == "Celsius"
}
