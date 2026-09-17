package com.rubify.debug

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.rubify.LOG_TAG
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Debug builds only: keeps the images of the last few captures as JPEGs in
 * app-private storage, named `<captureId>-<kind>.jpg`. JPEG because PNG
 * encoding a full screen takes ~0.8 s on device. Pull one with
 * `adb exec-out run-as com.rubify cat files/debug-images/<name>.jpg > out.jpg`.
 */
class DebugImageStore(context: Context) {

    private val dir = File(context.filesDir, DIR_NAME)

    /** A sortable id that groups the images of one capture. */
    fun newCaptureId(): String = LocalDateTime.now().format(ID_FORMAT)

    fun save(bitmap: Bitmap, captureId: String, kind: String): File? {
        if (!dir.isDirectory && !dir.mkdirs()) {
            Log.w(LOG_TAG, "Cannot create ${dir.absolutePath}")
            return null
        }
        val file = File(dir, "$captureId-$kind.jpg")
        return try {
            file.outputStream().buffered().use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                    throw IOException("JPEG encoding failed")
                }
            }
            prune()
            Log.i(LOG_TAG, "Saved debug image ${file.absolutePath}")
            file
        } catch (e: IOException) {
            file.delete()
            Log.w(LOG_TAG, "Could not save debug image", e)
            null
        }
    }

    private fun prune() {
        val names = dir.list()?.toList() ?: return
        debugImagesToPrune(names, KEEP_CAPTURES).forEach { File(dir, it).delete() }
    }

    companion object {
        const val DIR_NAME = "debug-images"
        private const val KEEP_CAPTURES = 5
        private const val JPEG_QUALITY = 90
        private val ID_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
    }
}

private val DEBUG_IMAGE_NAME = Regex("""^(\d{8}-\d{6}-\d{3})-[a-z]+\.(png|jpg)$""")

/**
 * Names of the images to delete so that only the newest [keepCaptures]
 * captures remain. Capture ids are timestamps, so their sort order is their
 * age order. Files that don't follow the naming scheme are left alone.
 */
internal fun debugImagesToPrune(names: List<String>, keepCaptures: Int): List<String> {
    val idByName = names.mapNotNull { name ->
        DEBUG_IMAGE_NAME.matchEntire(name)?.let { name to it.groupValues[1] }
    }
    val kept = idByName.map { it.second }.distinct().sortedDescending().take(keepCaptures).toSet()
    return idByName.filter { it.second !in kept }.map { it.first }
}
