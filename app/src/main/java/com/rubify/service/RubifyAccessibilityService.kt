package com.rubify.service

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.rubify.BuildConfig
import com.rubify.LOG_TAG
import com.rubify.capture.ScreenCapturer
import com.rubify.debug.DebugImageStore
import com.rubify.debug.OcrDebugRenderer
import com.rubify.ocr.HanziRecognizer
import com.rubify.ocr.OcrPage
import com.rubify.ocr.describeChars
import com.rubify.pinyin.PinyinAnnotator
import com.rubify.pinyin.PinyinAssets
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reacts to exactly one thing: a tap on the accessibility button. It subscribes
 * to no accessibility events and cannot read window content (see
 * res/xml/accessibility_service_config.xml).
 *
 * A tap runs one reading: capture, OCR, then pinyin. Taps during a reading
 * are dropped.
 */
class RubifyAccessibilityService : AccessibilityService() {

    private var buttonController: AccessibilityButtonController? = null
    private var workerThread: HandlerThread? = null
    private var screenCapturer: ScreenCapturer? = null
    private var recognizer: HanziRecognizer? = null
    private var debugStore: DebugImageStore? = null

    /** Worker-confined: set by the load task queued first on the worker. */
    private var annotator: PinyinAnnotator? = null

    /**
     * Pipeline callbacks run here, one at a time. A Handler-backed executor
     * because posting to it after shutdown is a logged no-op, not an exception
     * thrown on a system or ML Kit thread.
     */
    private var worker: Executor? = null

    private val reading = AtomicBoolean(false)

    /**
     * Last known button availability. The button disappears in full-screen
     * apps; the Quick Settings tile (phase 6) is the fallback. Unreliable on
     * some devices, see AGENTS.md.
     */
    @Volatile
    var isButtonAvailable: Boolean = false
        private set

    private val buttonCallback = object : AccessibilityButtonController.AccessibilityButtonCallback() {
        override fun onClicked(controller: AccessibilityButtonController) {
            Log.i(LOG_TAG, "Accessibility button clicked")
            startReading()
        }

        override fun onAvailabilityChanged(
            controller: AccessibilityButtonController,
            available: Boolean,
        ) {
            isButtonAvailable = available
            Log.i(LOG_TAG, "Accessibility button available=$available")
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val thread = HandlerThread("rubify-worker").apply { start() }
        val handler = Handler(thread.looper)
        val executor = Executor { handler.post(it) }
        workerThread = thread
        worker = executor
        // Queued before any reading, so every OCR callback sees the result.
        executor.execute(::loadPinyinDictionary)
        screenCapturer = ScreenCapturer(this, executor)
        recognizer = HanziRecognizer()
        if (BuildConfig.DEBUG) debugStore = DebugImageStore(this)

        val controller = accessibilityButtonController
        controller.registerAccessibilityButtonCallback(buttonCallback)
        buttonController = controller
        isButtonAvailable = controller.isAccessibilityButtonAvailable
        Log.i(LOG_TAG, "Service connected, accessibility button available=$isButtonAvailable")
    }

    private fun loadPinyinDictionary() {
        val startedAt = SystemClock.elapsedRealtime()
        try {
            val dictionary = PinyinAssets.load(assets)
            annotator = PinyinAnnotator(dictionary)
            Log.i(
                LOG_TAG,
                "Pinyin dictionary: ${dictionary.wordCount} words in " +
                    "${SystemClock.elapsedRealtime() - startedAt} ms",
            )
        } catch (e: Exception) {
            // Readings still run; lines just get no pinyin.
            Log.e(LOG_TAG, "Could not load the pinyin dictionary", e)
        }
    }

    private fun startReading() {
        val capturer = screenCapturer ?: return
        if (!reading.compareAndSet(false, true)) {
            Log.i(LOG_TAG, "Still reading the previous capture, tap dropped")
            return
        }
        capturer.capture(onCaptured = ::onScreenshot, onFailed = { reading.set(false) })
    }

    /** Runs on the worker. Owns [bitmap] and must recycle it. */
    private fun onScreenshot(bitmap: Bitmap) {
        val recognizer = recognizer
        val worker = worker
        if (recognizer == null || worker == null) {
            bitmap.recycle()
            reading.set(false)
            return
        }
        Log.i(LOG_TAG, "Screenshot ${bitmap.width}x${bitmap.height}")
        val captureId = debugStore?.newCaptureId()

        // OCR runs on ML Kit's threads and its result is queued on the worker,
        // behind the debug save below, so the bitmap is still alive for both.
        recognizer.recognize(bitmap, worker) { page, recognitionMs ->
            try {
                if (page != null) {
                    val pinyin = annotate(page, recognitionMs)
                    if (captureId != null) saveOcrDebugImage(bitmap, page, pinyin, captureId)
                }
            } finally {
                bitmap.recycle()
                reading.set(false)
            }
        }
        if (captureId != null) debugStore?.save(bitmap, captureId, "screenshot")
    }

    /** Returns the pinyin of each line's characters, aligned with `page.lines`. */
    private fun annotate(page: OcrPage, recognitionMs: Long): List<List<String?>> {
        Log.i(LOG_TAG, "OCR in $recognitionMs ms: ${page.lines.size} lines, ${page.hanziCount} hanzi")
        val annotator = annotator
        val startedAt = SystemClock.elapsedRealtime()
        val pinyin = page.lines.map { line ->
            annotator?.annotate(line) ?: List(line.chars.size) { null }
        }
        Log.i(LOG_TAG, "Pinyin in ${SystemClock.elapsedRealtime() - startedAt} ms")
        // Recognized text is screen content: debug builds only.
        if (BuildConfig.DEBUG) {
            page.lines.zip(pinyin) { line, readings ->
                Log.d(LOG_TAG, "line ${line.box} \"${line.text}\"")
                Log.d(LOG_TAG, "  " + line.chars.zip(readings) { c, p -> if (p != null) "${c.text}($p)" else c.text }.joinToString(""))
                Log.v(LOG_TAG, "  ${line.describeChars()}")
            }
        }
        return pinyin
    }

    private fun saveOcrDebugImage(
        screenshot: Bitmap,
        page: OcrPage,
        pinyin: List<List<String?>>,
        captureId: String,
    ) {
        val store = debugStore ?: return
        val annotated = OcrDebugRenderer.render(screenshot, page, pinyin)
        try {
            store.save(annotated, captureId, "ocr")
        } finally {
            annotated.recycle()
        }
    }

    // No accessibilityEventTypes are declared, so nothing is delivered here.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        release()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    private fun release() {
        buttonController?.unregisterAccessibilityButtonCallback(buttonCallback)
        buttonController = null
        screenCapturer?.shutdown()
        screenCapturer = null
        recognizer?.close()
        recognizer = null
        // Lets queued work finish; later posts are dropped.
        workerThread?.quitSafely()
        workerThread = null
        worker = null
        debugStore = null
        annotator = null
    }
}
