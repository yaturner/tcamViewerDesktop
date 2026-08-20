package com.das.tcamviewerdesktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.das.tcamviewerdesktop.model.TempSample
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/** Rounds a raw axis-step estimate to a "nice" 1/2/5×10^n value — ported verbatim from Android's
 *  CameraScreen.kt (pure math, no platform dependency). */
private fun niceAxisStep(range: Float): Float {
    if (range <= 0f) return 1f
    val rawStep = range / 5f
    val magnitude = 10.0.pow(floor(log10(rawStep.toDouble())))
    val residual = rawStep / magnitude
    val niceResidual = when {
        residual <= 1 -> 1.0
        residual <= 2 -> 2.0
        residual <= 5 -> 5.0
        else -> 10.0
    }
    return (niceResidual * magnitude).toFloat()
}

@Composable
fun LegendEntry(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(width = 16.dp, height = 3.dp)) {
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = size.height,
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontSize = 12.sp, color = Color(0xFFAEAEB2))
    }
}

/** Rolling/saved line chart of spot(or avg)/max/min temperature — ported from Android's
 *  CameraScreen.kt/ChartsScreen.kt shared composable. Axis label drawing uses Compose's
 *  [rememberTextMeasurer]/[drawText] instead of the Android version's
 *  `nativeCanvas`+`android.graphics.Paint` — Skia's native canvas API on desktop isn't the same
 *  as Android's, but the cross-platform Compose text-drawing API works identically on both. */
@Composable
fun TemperatureHistoryChart(
    samples: List<TempSample>,
    isCelsius: Boolean,
    primaryLabel: String = "Spot",
) {
    val unitSuffix = if (isCelsius) "°C" else "°F"
    val chartBackground = Color(0xFF1C1C1E)
    val gridColor = Color(0xFF7A7A7E)
    val axisTextColor = Color(0xFFAEAEB2)
    val textMeasurer = rememberTextMeasurer()
    val axisTextStyle = TextStyle(fontSize = 10.sp, color = axisTextColor)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(chartBackground, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        if (samples.size < 2) {
            Box(
                modifier = Modifier.fillMaxWidth().height(220.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Collecting data…", color = Color.Gray)
            }
            return@Column
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            LegendEntry(Color(0xFFE53935), "Max")
            Spacer(modifier = Modifier.width(14.dp))
            LegendEntry(Color(0xFF43A047), primaryLabel)
            Spacer(modifier = Modifier.width(14.dp))
            LegendEntry(Color(0xFF1E88E5), "Min")
        }

        val rawYMin = samples.minOf { minOf(it.spot, it.max, it.min) }
        val rawYMax = samples.maxOf { maxOf(it.spot, it.max, it.min) }
        val yStep = niceAxisStep(rawYMax - rawYMin)
        val yMin = floor(rawYMin / yStep) * yStep
        val yMax = ceil(rawYMax / yStep) * yStep
        val yRange = (yMax - yMin).takeIf { it > 0.01f } ?: 1f
        val yTicks = ((yMax - yMin) / yStep).roundToInt().coerceAtLeast(1)

        val tStart = samples.first().timestampMs
        val tEnd = samples.last().timestampMs
        val tRange = (tEnd - tStart).takeIf { it > 0L } ?: 1L
        val totalMinutes = tRange / 60_000f
        val xStep = niceAxisStep(totalMinutes)
        val xTicks = (totalMinutes / xStep).roundToInt().coerceAtLeast(1)
        val xDecimals = when {
            xStep >= 1f -> 0
            xStep >= 0.1f -> 1
            else -> 2
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(top = 6.dp),
        ) {
            val leftMargin = 34.dp.toPx()
            val bottomMargin = 40.dp.toPx()
            val plotWidth = size.width - leftMargin
            val plotHeight = size.height - bottomMargin

            fun xOf(t: Long) = leftMargin + (t - tStart).toFloat() / tRange * plotWidth
            fun yOf(v: Float) = plotHeight - ((v - yMin) / yRange * plotHeight)

            // Horizontal gridlines + y-axis labels.
            for (i in 0..yTicks) {
                val v = yMin + i * yStep
                val y = yOf(v)
                drawLine(gridColor, Offset(leftMargin, y), Offset(size.width, y), strokeWidth = 1f)
                val layout = textMeasurer.measure("%.0f".format(v), axisTextStyle)
                drawText(layout, topLeft = Offset(leftMargin - layout.size.width - 6.dp.toPx(), y - layout.size.height / 2f))
            }

            // Vertical gridlines + x-axis (minutes) labels.
            for (i in 0..xTicks) {
                val minutesAt = i * xStep
                val tAt = tStart + (minutesAt * 60_000f).toLong()
                if (tAt > tEnd) break
                val x = xOf(tAt)
                drawLine(gridColor, Offset(x, 0f), Offset(x, plotHeight), strokeWidth = 1f)
                val layout = textMeasurer.measure("%.${xDecimals}f".format(minutesAt), axisTextStyle)
                drawText(layout, topLeft = Offset(x - layout.size.width / 2f, plotHeight + 6.dp.toPx()))
            }

            val titleLayout = textMeasurer.measure("Minutes", axisTextStyle)
            drawText(
                titleLayout,
                topLeft = Offset(leftMargin + (plotWidth - titleLayout.size.width) / 2f, size.height - titleLayout.size.height),
            )

            drawText(textMeasurer.measure(unitSuffix, axisTextStyle), topLeft = Offset(0f, 0f))

            // Samples can arrive far denser than a marker every few pixels would allow — markers
            // are thinned to whichever samples land at least markerSpacing apart (always
            // including the first and last point) rather than one per sample.
            val markerSpacing = 18.dp.toPx()
            val markerRadius = 2.5.dp.toPx()

            fun drawSeries(pick: (TempSample) -> Float, color: Color, strokeWidth: Float) {
                val path = Path()
                var lastMarkerX = Float.NEGATIVE_INFINITY
                samples.forEachIndexed { i, s ->
                    val px = xOf(s.timestampMs)
                    val py = yOf(pick(s))
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    val isLast = i == samples.lastIndex
                    if (px - lastMarkerX >= markerSpacing || isLast) {
                        drawCircle(color = color, radius = markerRadius, center = Offset(px, py))
                        lastMarkerX = px
                    }
                }
                drawPath(path, color = color, style = Stroke(width = strokeWidth))
            }
            drawSeries({ it.max }, Color(0xFFE53935), 3f)
            drawSeries({ it.spot }, Color(0xFF43A047), 5f)
            drawSeries({ it.min }, Color(0xFF1E88E5), 3f)
        }
    }
}
