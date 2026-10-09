package com.das.tcamviewerdesktop.util

import java.io.File
import java.io.IOException

/**
 * Helpers for files stored on a full tCam's micro-SD card — ported from tcamViewer2's Android
 * DeviceFiles. Names come from the camera, so anything used as a path segment goes through
 * [isSafeName] first.
 */
object DeviceFiles {
    const val IMAGE_EXTENSION = "tjsn"

    /** Splits the comma-separated `name_list` from a `filesystem_list` reply. The camera leaves a
     *  trailing comma, so empty entries are dropped. */
    fun parseNameList(nameList: String): List<String> = nameList.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    /** A name is usable as a single path segment: no separators and not "." or "..". */
    fun isSafeName(name: String): Boolean = name.isNotEmpty() &&
        name != "." &&
        name != ".." &&
        !name.contains('/') &&
        !name.contains('\\')

    /** Only still images are downloadable for now. Video (.tmjsn) replies have a different
     *  multi-message layout that the import path does not handle yet. */
    fun isDownloadableImage(name: String): Boolean = isSafeName(name) && name.endsWith(".$IMAGE_EXTENSION")

    /** Same root the Library scans, so imported folders show up beside captured ones. */
    fun picturesRoot(): File = File(CameraUtils.dataRoot, "Pictures")

    /**
     * Writes [content] to `root/folder/fileName`, keeping the camera's folder name. Writes to a
     * `.part` file first, so an interrupted download never leaves a half-written image that the
     * Library would try to open. Returns [SaveResult.ALREADY_PRESENT] without writing if the file
     * already exists (already imported).
     */
    @Throws(IOException::class)
    fun saveImage(root: File, folder: String, fileName: String, content: String): SaveResult {
        require(isSafeName(folder) && isSafeName(fileName)) { "Unsafe device path: $folder/$fileName" }
        val dir = File(root, folder)
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Could not create ${dir.path}")

        val target = File(dir, fileName)
        if (target.exists()) return SaveResult.ALREADY_PRESENT

        val partial = File(dir, "$fileName.part")
        try {
            partial.writeText(content, Charsets.US_ASCII)
            if (!partial.renameTo(target)) throw IOException("Could not rename to ${target.name}")
        } catch (e: IOException) {
            partial.delete()
            throw e
        }
        return SaveResult.SAVED
    }

    enum class SaveResult { SAVED, ALREADY_PRESENT }
}
