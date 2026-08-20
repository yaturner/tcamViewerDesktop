package com.das.tcamviewerdesktop.util

import com.das.tcamviewerdesktop.constants.Constants
import com.das.tcamviewerdesktop.model.ImageDto
import com.das.tcamviewerdesktop.model.Rect
import com.das.tcamviewerdesktop.model.TempSample
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.awt.image.BufferedImage
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

data class RecordingHandle(val file: File, val stream: FileOutputStream)

/** Ported from tcamViewer2's Android CameraUtils — same radiometric/telemetry decode and
 *  palette-mapping logic, with android.graphics.Bitmap swapped for java.awt.image.BufferedImage
 *  and app-private external storage swapped for a plain directory under the user's home. */
class CameraUtils {
    // Pre-allocated per-frame buffers — eliminates heap allocation per frame
    private val pixels = IntArray(Constants.IMAGE_WIDTH * Constants.IMAGE_HEIGHT)
    private val imageData = IntArray(Constants.IMAGE_WIDTH * Constants.IMAGE_HEIGHT)
    private val imageBytes = ByteArray(Constants.IMAGE_WIDTH * Constants.IMAGE_HEIGHT * 2)
    private val telData = IntArray(3 * 80) // 3 Lepton telemetry rows × 80 words

    // Cached settings — updated by CameraViewModel via flow observation to avoid a settings
    // read on the per-frame hot path.
    @Volatile var settingIsManualRange: Boolean = false

    @Volatile var settingManualMin: Float = 0f

    @Volatile var settingManualMax: Float = 100f

    @Volatile var settingIsCelsius: Boolean = true

    companion object {
        private const val offsetA: Int = 0
        private const val offsetB: Int = 80
        private const val offsetC: Int = 160

        val IP_PATTERN: Pattern = Pattern.compile(
            "^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$",
        )
        val sdf: SimpleDateFormat = SimpleDateFormat("MM/dd/yy HH:mm:ss", Locale.getDefault())
        val simpleDateFormatFolder: SimpleDateFormat = SimpleDateFormat("MM_dd_yyyy", Locale.getDefault())
        val simpleDateFormatFile: SimpleDateFormat = SimpleDateFormat("HH_mm_ss", Locale.getDefault())

        /** App-private data root — mirrors what getExternalFilesDir() gave the Android app,
         *  just rooted under the user's home instead of app-scoped Android storage. */
        val dataRoot: File = File(System.getProperty("user.home"), "tCamViewer")
    }

    @Synchronized
    @Throws(JSONException::class)
    fun processImageResponse(imageDto: ImageDto) {
        val palette: Array<IntArray?>? = imageDto.palette
        val isManualRange = settingIsManualRange
        val manualMin = settingManualMin
        val manualMax = settingManualMax
        val isCelsius = settingIsCelsius
        val metadata: JSONObject = imageDto.getJsonObject().getJSONObject("metadata")
        val radiometricString: String = imageDto.getJsonObject().getString("radiometric")
        val telemetryString: String = imageDto.getJsonObject().getString("telemetry")

        Base64.getDecoder().decode(radiometricString.toByteArray(), imageBytes)

        imageDto.creationDate = try {
            sdf.parse(metadata.optString("Date") + " " + metadata.optString("Time"))
        } catch (e: Exception) {
            Date()
        }

        parseTelemetryData(telemetryString)

        val status = ((telData[4] and 0xffff) shl 16) or (telData[3] and 0xffff)
        imageDto.isAGC = (status and Constants.TELEMETRY_MASK_AGC) == Constants.TELEMETRY_MASK_AGC
        imageDto.isShutdown = (status and Constants.TELEMETRY_MASK_SHUTDOWN) == Constants.TELEMETRY_MASK_SHUTDOWN
        imageDto.emissivity = telData[offsetB + 19]
        imageDto.gainMode = telData[offsetC + 5]
        imageDto.autoGainMode = telData[offsetC + 6]
        imageDto.tLinearEnabled = telData[offsetC + 48]
        imageDto.tLinearResolution = telData[offsetC + 49]
        imageDto.spotmeterMean = telData[offsetC + 50]
        val x1 = telData[offsetC + 55] and 0xffff
        val y1 = telData[offsetC + 54] and 0xffff
        val x2 = telData[offsetC + 57] and 0xffff
        val y2 = telData[offsetC + 56] and 0xffff
        imageDto.spotmeterLocation = Rect(x1, y1, x2, y2)

        var minTemp = Int.MAX_VALUE
        var maxTemp = Int.MIN_VALUE
        val nPixels = imageBytes.size / 2
        for (j in 0 until nPixels) {
            val i = j * 2
            val v = ((imageBytes[i + 1].toInt() and 0xff) shl 8) or (imageBytes[i].toInt() and 0xff)
            imageData[j] = v
            if (v < minTemp) minTemp = v
            if (v > maxTemp) maxTemp = v
        }
        // Copy out of the shared per-frame buffer — imageData is reused on every call, so
        // aliasing it directly would let a later frame silently mutate an ImageDto retained
        // past this call.
        imageDto.imageData = imageData.copyOf()
        imageDto.minTemperature = minTemp
        imageDto.maxTemperature = maxTemp

        val histogram = IntArray(256)

        if (imageDto.isAGC) {
            for (i in pixels.indices) {
                val idx = imageData[i].coerceIn(0, 255)
                pixels[i] = rgbToPixel(palette?.get(idx))
                histogram[idx]++
            }
        } else {
            val (rangeMin, rangeMax) = getRadiometricTemperatures(
                imageDto,
                isManualRange,
                manualMin,
                manualMax,
                isCelsius,
            )
            val diff = if (rangeMax > rangeMin) rangeMax - rangeMin else 1
            for (i in pixels.indices) {
                val v = if (isManualRange) imageData[i].coerceIn(rangeMin, rangeMax) else imageData[i]
                val idx = (((v - rangeMin) * 255) / diff).coerceIn(0, 255)
                pixels[i] = rgbToPixel(palette?.get(idx))
                histogram[idx]++
            }
        }

        imageDto.histogram = histogram
        imageDto.bitmap = renderBitmap(pixels)
    }

    private fun renderBitmap(pixelsArgb: IntArray): BufferedImage {
        val bmp = BufferedImage(Constants.IMAGE_WIDTH, Constants.IMAGE_HEIGHT, BufferedImage.TYPE_INT_ARGB)
        bmp.setRGB(0, 0, Constants.IMAGE_WIDTH, Constants.IMAGE_HEIGHT, pixelsArgb, 0, Constants.IMAGE_WIDTH)
        return bmp
    }

    private fun rgbToPixel(rgb: IntArray?): Int {
        val red = (rgb?.get(0) ?: 0).coerceIn(0, 255)
        val green = (rgb?.get(1) ?: 0).coerceIn(0, 255)
        val blue = (rgb?.get(2) ?: 0).coerceIn(0, 255)
        return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
    }

    private fun parseTelemetryData(telemetryString: String) {
        val telBytes = Base64.getDecoder().decode(telemetryString.toByteArray())
        val nWords = telBytes.size / 2
        for (j in 0 until nWords) {
            val i = j * 2
            telData[j] = ((telBytes[i + 1].toInt() and 0xff) shl 8) or (telBytes[i].toInt() and 0xff)
        }
    }

    fun remapWithPalette(
        dto: ImageDto,
        palette: Array<IntArray?>?,
        isManualRange: Boolean,
        manualMin: Float,
        manualMax: Float,
        isCelsius: Boolean,
    ): BufferedImage? {
        val data = dto.imageData?.copyOf() ?: return null
        val out = IntArray(data.size)
        if (dto.isAGC) {
            for (i in out.indices) {
                val idx = data[i].coerceIn(0, 255)
                out[i] = rgbToPixel(palette?.get(idx))
            }
        } else {
            val (rangeMin, rangeMax) = getRadiometricTemperatures(dto, isManualRange, manualMin, manualMax, isCelsius)
            val diff = if (rangeMax > rangeMin) rangeMax - rangeMin else 1
            for (i in out.indices) {
                val v = if (isManualRange) data[i].coerceIn(rangeMin, rangeMax) else data[i]
                val idx = (((v - rangeMin) * 255) / diff).coerceIn(0, 255)
                out[i] = rgbToPixel(palette?.get(idx))
            }
        }
        return renderBitmap(out)
    }

    fun getRadiometricTemperatures(
        imageDto: ImageDto,
        isManualRange: Boolean,
        manualMin: Float,
        manualMax: Float,
        isCelsius: Boolean,
    ): Pair<Int, Int> = if (isManualRange) {
        Pair(
            convertToRadiometric(imageDto, manualMin, isCelsius),
            convertToRadiometric(imageDto, manualMax, isCelsius),
        )
    } else {
        Pair(imageDto.minTemperature, imageDto.maxTemperature)
    }

    fun convertToRadiometric(imageDto: ImageDto, value: Float, isCelsius: Boolean): Int {
        val scale = if (imageDto.tLinearResolution == 0) 10f else 100f
        return if (isCelsius) {
            Math.round((value + 273.15f) * scale)
        } else {
            val c = (value - 32f) * .5556f
            Math.round((c + 273.15f) * scale)
        }
    }

    /** Returns the saved file (its full path is what save-confirmation toasts show), or null if
     *  the destination directory couldn't be created. */
    @Throws(IOException::class)
    fun saveTjsn(imageDto: ImageDto): File? {
        val rootDir = File(dataRoot, "Pictures")
        val dir = File(rootDir, generateNewPath())
        if (!dir.exists() && !dir.mkdirs()) return null

        val tjsn = File(dir, generateNewFilename() + ".tjsn")
        imageDto.filename = tjsn.name
        FileOutputStream(tjsn).use { stream ->
            stream.write(imageDto.getJsonObject().toString().toByteArray(StandardCharsets.US_ASCII))
        }
        return tjsn
    }

    /** Saves the temperature-over-time history as a `.tchart` JSON file, mirroring saveTjsn's
     *  layout (grouped into date folders). */
    @Throws(IOException::class)
    fun saveTempChart(
        samples: List<TempSample>,
        isCelsius: Boolean,
        primaryLabel: String = "Spot",
    ): Boolean {
        if (samples.isEmpty()) return false
        val dir = File(dataRoot, "Charts" + File.separator + generateNewPath())
        if (!dir.exists() && !dir.mkdirs()) return false

        val file = File(dir, "chart_" + simpleDateFormatFile.format(Date()) + ".tchart")
        val samplesArray = JSONArray()
        samples.forEach { s ->
            samplesArray.put(
                JSONObject().apply {
                    put("t", s.timestampMs)
                    put("spot", s.spot)
                    put("max", s.max)
                    put("min", s.min)
                },
            )
        }
        val json = JSONObject().apply {
            put("saved_time", sdf.format(Date()))
            put("unit", if (isCelsius) "Celsius" else "Fahrenheit")
            put("primary_label", primaryLabel)
            put("samples", samplesArray)
        }
        FileOutputStream(file).use { stream ->
            stream.write(json.toString().toByteArray(StandardCharsets.US_ASCII))
        }
        return true
    }

    fun generateNewFilename(): String = "img_" + simpleDateFormatFile.format(Date())

    fun generateNewPath(): String = simpleDateFormatFolder.format(Date())

    @Throws(IOException::class)
    fun openRecordingFile(): RecordingHandle {
        val rootDir = File(dataRoot, "Movies")
        val dir = File(rootDir, generateNewPath())
        if (!dir.exists()) dir.mkdirs()
        val filename = "vid_" + simpleDateFormatFile.format(Date()) + ".mtjsn"
        val file = File(dir, filename)
        return RecordingHandle(file, FileOutputStream(file))
    }

    @Throws(IOException::class)
    fun openTimeLapseFile(): RecordingHandle {
        val rootDir = File(dataRoot, "Movies")
        val dir = File(rootDir, generateNewPath())
        if (!dir.exists()) dir.mkdirs()
        val filename = "tl_" + simpleDateFormatFile.format(Date()) + ".tltjsn"
        val file = File(dir, filename)
        return RecordingHandle(file, FileOutputStream(file))
    }

    fun readTjsnFile(path: String): String {
        var json = ""
        var line: String?
        var bufferedReader: BufferedReader? = null
        var fileReader: FileReader? = null
        try {
            fileReader = FileReader(File(path))
            bufferedReader = BufferedReader(fileReader)
            do {
                line = bufferedReader.readLine()
                if (line != null) {
                    json += line
                }
            } while (line != null)
        } catch (e: IOException) {
            json = ""
        }

        if (bufferedReader != null) {
            try {
                fileReader!!.close()
                bufferedReader.close()
            } catch (e: Exception) {
            }
        }
        return json
    }
}
