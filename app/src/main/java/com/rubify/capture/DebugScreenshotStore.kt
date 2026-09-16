package com.rubify.capture

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.rubify.LOG_TAG
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Debug builds only: keeps the last few screenshots as PNGs in app-private
 * storage so captures can be checked against the screen. Pull one with
 * `adb exec-out run-as com.rubify cat files/debug-screenshots/<name>.png > shot.png`.
 */
class DebugScreenshotStore(context: Context) {

    private val dir = File(context.filesDir, DIR_NAME)

    fun save(bitmap: Bitmap): File? {
        if (!dir.isDirectory && !dir.mkdirs()) {
            Log.w(LOG_TAG, "Cannot create ${dir.absolutePath}")
            return null
        }
        val file = File(dir, "screenshot-${LocalDateTime.now().format(NAME_FORMAT)}.png")
        return try {
            file.outputStream().buffered().use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    throw IOException("PNG encoding failed")
                }
            }
            prune()
            Log.i(LOG_TAG, "Saved debug screenshot ${file.absolutePath}")
            file
        } catch (e: IOException) {
            file.delete()
            Log.w(LOG_TAG, "Could not save debug screenshot", e)
            null
        }
    }

    private fun prune() {
        val names = dir.list()?.toList() ?: return
        screenshotsToPrune(names, KEEP).forEach { File(dir, it).delete() }
    }

    companion object {
        const val DIR_NAME = "debug-screenshots"
        private const val KEEP = 5
        private val NAME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
    }
}

/**
 * Names of the screenshots to delete so that only the newest [keep] remain.
 * File names embed a sortable timestamp, so name order is age order.
 */
internal fun screenshotsToPrune(names: List<String>, keep: Int): List<String> =
    names.filter { it.startsWith("screenshot-") && it.endsWith(".png") }
        .sortedDescending()
        .drop(keep)
