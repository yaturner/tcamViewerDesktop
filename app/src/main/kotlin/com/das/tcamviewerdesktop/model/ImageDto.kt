package com.das.tcamviewerdesktop.model

import com.das.tcamviewerdesktop.cameraUtils
import com.das.tcamviewerdesktop.paletteFactory
import org.json.JSONException
import org.json.JSONObject
import java.awt.image.BufferedImage
import java.util.Date

/** Data model for a single thermal frame — ported from tcamViewer2's Android ImageDto with
 *  android.graphics.Bitmap/Rect swapped for java.awt.image.BufferedImage/[Rect]. */
class ImageDto {
    var isAGC: Boolean = false
    var isShutdown: Boolean = false
    var emissivity: Int = 0
    var tLinearEnabled: Int = 0
    var tLinearResolution: Int = 0 // 0 = 0.1, 1 = 0.01
    var spotmeterMean: Int = 0
    var spotmeterLocation: Rect? = null
    var isShutterLockout: Boolean = false
    var fFCState: Int = 0
    var fFCDesired: Int = 0
    var gainMode: Int = 0
    var autoGainMode: Int = 0
    var maxTemperature: Int = 0
    var minTemperature: Int = 0
    var creationDate: Date? = null

    @get:JvmName("getJsonObjectNullable")
    var jsonObject: JSONObject? = null
    var metadata: JSONObject? = null
    var filename: String? = ""
    var tjsnString: String? = null
    var palette: Array<IntArray?>? = null
    var imageData: IntArray? = null
    var paletteName: String? = null
        set(value) {
            field = value
            try {
                val meta = jsonObject!!.getJSONObject("metadata")
                meta.remove("paletteName")
                meta.put("palette", value)
            } catch (_: JSONException) {}
        }
    var bitmap: BufferedImage? = null
    var histogram: IntArray? = null

    // Constructor from file
    suspend fun initFromFile(filename: String?, paletteName: String?) {
        this.filename = filename
        tjsnString = cameraUtils.readTjsnFile(filename!!)
        if (tjsnString != null && tjsnString!!.isNotEmpty()) {
            try {
                jsonObject = JSONObject(tjsnString!!)
            } catch (e: JSONException) {
            }
        } else {
            return
        }
        init(paletteName)
    }

    companion object {
        suspend fun create(
            jsonObject: JSONObject,
            paletteName: String?,
        ): ImageDto {
            val imageDto = ImageDto()
            imageDto.jsonObject = jsonObject
            imageDto.init(paletteName)
            return imageDto
        }

        suspend fun create(filename: String, paletteName: String?): ImageDto {
            val imageDto = ImageDto()
            imageDto.initFromFile(filename, paletteName)
            return imageDto
        }
    }

    private suspend fun init(paletteName: String?) {
        try {
            metadata = jsonObject!!.getJSONObject("metadata")
            // Prefer the explicitly passed palette; fall back to what's stored in metadata; default Rainbow
            val resolved = paletteName
                ?: metadata!!.optString("palette").takeIf { it.isNotEmpty() && paletteFactory.getPaletteByName(it) != null }
                ?: "Rainbow"
            this.paletteName = resolved // setter also writes resolved name into metadata["palette"]
            palette = paletteFactory.getPaletteByName(this.paletteName)
            bitmap = null
            cameraUtils.processImageResponse(this)
        } catch (e: JSONException) {
        }
        creationDate = Date()
    }

    suspend fun parse(obj: JSONObject, paletteName: String?) {
        jsonObject = obj
        create(obj, paletteName)
    }

    fun getJsonObject(): JSONObject = jsonObject!!
}
