package io.github.v_inn.rubify.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import io.github.v_inn.rubify.LOG_TAG
import java.io.Closeable
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicLong

/**
 * On-device OCR with ML Kit Text Recognition v2 and its bundled Chinese
 * model, which also reads Latin text. Hierarchy is Block > Line > Element >
 * Symbol; for Chinese each Symbol is a single character with its own box.
 */
class HanziRecognizer : Closeable {

    private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    /**
     * Recognizes [bitmap] asynchronously. [onResult] runs once on
     * [callbackExecutor] with the page (null if recognition failed or was
     * cancelled) and the recognition time in ms, excluding any wait for the
     * executor. The caller keeps ownership of [bitmap] and must not recycle it
     * before [onResult] has run.
     */
    fun recognize(
        bitmap: Bitmap,
        callbackExecutor: Executor,
        onResult: (page: OcrPage?, recognitionMs: Long) -> Unit,
    ) {
        val startedAt = SystemClock.elapsedRealtime()
        val finishedAt = AtomicLong()
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            // Listeners are dispatched in order, so this inline one stamps the
            // completion time before the callback is queued on the executor.
            .addOnCompleteListener({ it.run() }) { finishedAt.set(SystemClock.elapsedRealtime()) }
            .addOnCompleteListener(callbackExecutor) { task ->
                val recognitionMs = finishedAt.get() - startedAt
                if (task.isSuccessful) {
                    onResult(task.result.toPage(bitmap.width, bitmap.height), recognitionMs)
                } else {
                    Log.w(LOG_TAG, "OCR failed", task.exception)
                    onResult(null, recognitionMs)
                }
            }
    }

    override fun close() {
        recognizer.close()
    }
}

private fun Text.toPage(width: Int, height: Int) = OcrPage(
    width = width,
    height = height,
    lines = textBlocks.flatMap { block -> block.lines.mapNotNull { it.toOcrLine() } },
)

private fun Text.Line.toOcrLine(): OcrLine? {
    val lineBox = boundingBox?.toPixelBox() ?: return null
    val chars = elements.flatMap { it.symbols }.mapNotNull { symbol ->
        symbol.boundingBox?.let { OcrChar(symbol.text, it.toPixelBox(), symbol.confidence) }
    }
    return OcrLine(text, lineBox, chars)
}

private fun Rect.toPixelBox() = PixelBox(left, top, right, bottom)
