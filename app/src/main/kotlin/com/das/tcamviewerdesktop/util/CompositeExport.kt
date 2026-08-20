package com.das.tcamviewerdesktop.util

import com.das.tcamviewerdesktop.constants.Constants
import com.das.tcamviewerdesktop.model.ImageDto
import com.das.tcamviewerdesktop.model.Rect
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

private fun formatTemp(rawValue: Int, scale: Float, isCelsius: Boolean): String {
    val tempC = rawValue / scale - 273.15f
    return if (isCelsius) "%.1f°C".format(tempC) else "%.1f°F".format(tempC * 9f / 5f + 32f)
}

/** Draws the fixed 4x4-camera-pixel hotspot/spotmeter square — matching the on-screen marker
 *  style (thick black outline + thin white outline on the same rect) — ported from Android's
 *  utils/HotspotMarker.kt (android.graphics.Canvas -> java.awt.Graphics2D). */
private fun drawHotspotMarker(
    g: java.awt.Graphics2D,
    spotmeterRect: Rect,
    imgWidthPx: Int,
    imgHeightPx: Int,
    offsetY: Float = 0f,
) {
    val markerSizePx = 4f
    val sx = imgWidthPx / Constants.IMAGE_WIDTH.toFloat()
    val sy = imgHeightPx / Constants.IMAGE_HEIGHT.toFloat()
    val centerCol = (spotmeterRect.left + spotmeterRect.right) / 2f
    val centerRow = (spotmeterRect.top + spotmeterRect.bottom) / 2f
    val left = (centerCol - markerSizePx / 2f) * sx
    val top = offsetY + (centerRow - markerSizePx / 2f) * sy
    val w = markerSizePx * sx
    val h = markerSizePx * sy

    g.color = Color.BLACK
    g.stroke = BasicStroke(6f)
    g.drawRect(left.toInt(), top.toInt(), w.toInt(), h.toInt())
    g.color = Color.WHITE
    g.stroke = BasicStroke(2f)
    g.drawRect(left.toInt(), top.toInt(), w.toInt(), h.toInt())
}

/** MM_dd_yyyy → w×h, or null if malformed. "640x480" -> (640, 480). */
fun parseResolution(s: String): Pair<Int, Int>? {
    val parts = s.lowercase().split("x")
    if (parts.size != 2) return null
    val w = parts[0].trim().toIntOrNull() ?: return null
    val h = parts[1].trim().toIntOrNull() ?: return null
    return w to h
}

/** Composites the colorized thermal image with a header (spotmeter temp), sidebar (color bar +
 *  max/min + spotmeter arrow), and footer (gain/emissivity/time/date) — ported from Android's
 *  LibraryScreen.kt buildShareBitmap() (android.graphics.Canvas/Paint -> java.awt.Graphics2D).
 *  With [exportMetadata] off, this is just the plain colorized picture at [resolution] — no
 *  chrome around it. */
fun buildCompositeImage(
    dto: ImageDto,
    isCelsius: Boolean,
    spotmeterEnabled: Boolean,
    exportMetadata: Boolean = true,
    resolution: Pair<Int, Int> = 640 to 480,
): BufferedImage {
    val tempScale = if (dto.tLinearResolution == 0) 10f else 100f
    val hasThermal = dto.tLinearEnabled != 0

    val (imgW, imgH) = resolution
    val scale = imgW / 640f
    val headerH = if (exportMetadata) (64 * scale).toInt() else 0
    val footerRow1Y = 34f * scale
    val footerRow2Y = 72f * scale
    val bottomPad = if (exportMetadata) (96 * scale).toInt() else 0
    val sidebarW = if (hasThermal && exportMetadata) (120 * scale).toInt() else 0
    val totalW = imgW + sidebarW
    val totalH = headerH + imgH + bottomPad

    val result = BufferedImage(totalW, totalH, BufferedImage.TYPE_INT_ARGB)
    val g = result.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    g.color = Color.BLACK
    g.fillRect(0, 0, totalW, totalH)

    // ── Header: centred spotmeter temperature (only when Spotmeter setting is on) ──
    if (exportMetadata && hasThermal && spotmeterEnabled) {
        g.color = Color.WHITE
        g.font = Font("SansSerif", Font.BOLD, (40 * scale).toInt())
        val text = formatTemp(dto.spotmeterMean, tempScale, isCelsius)
        val width = g.fontMetrics.stringWidth(text)
        g.drawString(text, imgW / 2f - width / 2f, headerH - 18f * scale)
    }

    // ── Thermal image, scaled to fill imgW×imgH, starting at y = headerH ──
    dto.bitmap?.let { src ->
        g.drawImage(src, 0, headerH, imgW, imgH, null)
    }

    if (hasThermal && exportMetadata) {
        if (spotmeterEnabled) {
            dto.spotmeterLocation?.let { rect -> drawHotspotMarker(g, rect, imgW, imgH, offsetY = headerH.toFloat()) }
        }

        // ── Colour bar: top-aligned with the image, full image height ──
        val cbW = (44 * scale).toInt()
        val cbH = imgH
        val cbX = imgW + (sidebarW - cbW) / 2
        val cbY = headerH

        val cbPixels = IntArray(256) { i ->
            val rgb = dto.palette?.get(255 - i)
            val r = rgb?.get(0) ?: 0
            val g2 = rgb?.get(1) ?: 0
            val b = rgb?.get(2) ?: 0
            (0xFF shl 24) or (r shl 16) or (g2 shl 8) or b
        }
        val cbSrc = BufferedImage(1, 256, BufferedImage.TYPE_INT_ARGB)
        cbSrc.setRGB(0, 0, 1, 256, cbPixels, 0, 1)
        g.drawImage(cbSrc, cbX, cbY, cbW, cbH, null)

        // ── Arrow marking where the current spotmeter reading falls on the bar ──
        if (spotmeterEnabled && dto.maxTemperature != dto.minTemperature) {
            val fraction = (
                (dto.maxTemperature - dto.spotmeterMean).toFloat() /
                    (dto.maxTemperature - dto.minTemperature)
                ).coerceIn(0f, 1f)
            val arrowY = cbY + fraction * cbH
            val tipX = cbX.toFloat()
            val baseX = tipX - 25f * scale
            val halfH = 18f * scale
            val arrowPath = Path2D.Float().apply {
                moveTo(tipX.toDouble(), arrowY.toDouble())
                lineTo(baseX.toDouble(), (arrowY - halfH).toDouble())
                lineTo(baseX.toDouble(), (arrowY + halfH).toDouble())
                closePath()
            }
            g.color = Color.WHITE
            g.fill(arrowPath)
            g.color = Color.BLACK
            g.stroke = BasicStroke(2f * scale)
            g.draw(arrowPath)
        }

        // ── Max / min labels (above and below the colour bar) ──
        g.color = Color.WHITE
        g.font = Font("SansSerif", Font.PLAIN, (30 * scale).toInt())
        val sidebarCx = imgW + sidebarW / 2f
        val maxText = formatTemp(dto.maxTemperature, tempScale, isCelsius)
        g.drawString(maxText, sidebarCx - g.fontMetrics.stringWidth(maxText) / 2f, headerH - 12f * scale)
        val minText = formatTemp(dto.minTemperature, tempScale, isCelsius)
        g.drawString(minText, sidebarCx - g.fontMetrics.stringWidth(minText) / 2f, (headerH + imgH + footerRow2Y))
    }

    // ── Footer: row 1 = gain mode + emissivity, row 2 = time + date saved ──
    if (exportMetadata) {
        val gainLabel = when (dto.gainMode) {
            Constants.GAIN_MODE_HIGH -> "HIGH"
            Constants.GAIN_MODE_LOW -> "LOW"
            Constants.GAIN_MODE_AUTO -> "AUTO"
            else -> "?"
        }
        val emissivityStr = "%.2f".format(dto.emissivity / 8192f)
        val timeStr = dto.metadata?.optString("Time").orEmpty()
        val dateStr = dto.metadata?.optString("Date").orEmpty()

        g.color = Color.WHITE
        g.font = Font("SansSerif", Font.PLAIN, (28 * scale).toInt())
        val footerTop = headerH + imgH
        val leftX = 16f * scale
        val rightX = imgW - 16f * scale
        g.drawString("g $gainLabel", leftX, footerTop + footerRow1Y)
        g.drawString("ε $emissivityStr", rightX - g.fontMetrics.stringWidth("ε $emissivityStr"), footerTop + footerRow1Y)
        g.drawString(timeStr, leftX, footerTop + footerRow2Y)
        g.drawString(dateStr, rightX - g.fontMetrics.stringWidth(dateStr), footerTop + footerRow2Y)
    }

    g.dispose()
    return result
}

/** Saves a composite/exported PNG under `~/tCamViewer/Exports/MM_DD_YYYY/`, mirroring
 *  [CameraUtils.saveTjsn]'s per-date-folder layout. Returns the saved file, or null on failure. */
fun saveExportedPng(image: BufferedImage, baseName: String): File? {
    val dir = File(CameraUtils.dataRoot, "Exports" + File.separator + com.das.tcamviewerdesktop.cameraUtils.generateNewPath())
    if (!dir.exists() && !dir.mkdirs()) return null
    val file = File(dir, "$baseName.png")
    return try {
        ImageIO.write(image, "png", file)
        file
    } catch (e: Exception) {
        null
    }
}
