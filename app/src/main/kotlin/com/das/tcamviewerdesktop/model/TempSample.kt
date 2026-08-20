package com.das.tcamviewerdesktop.model

/** One point in the rolling temperature-over-time history. Values are in whatever unit was
 *  currently selected when the sample was taken (same as the on-screen spot/max/min text). */
data class TempSample(
    val timestampMs: Long,
    val spot: Float,
    val max: Float,
    val min: Float,
)
