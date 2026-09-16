package com.rubify.service

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
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
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reacts to exactly one thing: a tap on the accessibility button. It subscribes
 * to no accessibility events and cannot read window content (see
 * res/xml/accessibility_service_config.xml).
 *
 * A tap runs one reading: capture, then OCR. Taps during a reading are dropped.
 */
class RubifyAccessibilityService : AccessibilityService() {

    private var buttonController: AccessibilityButtonController? = null
    private var workerThread: HandlerThread? = null
    private var screenCapturer: ScreenCapturer? = null
    private var recognizer: HanziRecognizer? = null
    private var debugStore: DebugImageStore? = null

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
        screenCapturer = ScreenCapturer(this, executor)
        recognizer = HanziRecognizer()
        if (BuildConfig.DEBUG) debugStore = DebugImageStore(this)

        val controller = accessibilityButtonController
        controller.registerAccessibilityButtonCallback(buttonCallback)
        buttonController = controller
        isButtonAvailable = controller.isAccessibilityButtonAvailable
        Log.i(LOG_TAG, "Service connected, accessibility button available=$isButtonAvailable")
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
                    onPage(page, recognitionMs)
                    if (captureId != null) saveOcrDebugImage(bitmap, page, captureId)
                }
            } finally {
                bitmap.recycle()
                reading.set(false)
            }
        }
        if (captureId != null) debugStore?.save(bitmap, captureId, "screenshot")
    }

    private fun onPage(page: OcrPage, recognitionMs: Long) {
        Log.i(LOG_TAG, "OCR in $recognitionMs ms: ${page.lines.size} lines, ${page.hanziCount} hanzi")
        // Recognized text is screen content: debug builds only.
        if (BuildConfig.DEBUG) {
            for (line in page.lines) {
                Log.d(LOG_TAG, "line ${line.box} \"${line.text}\"")
                Log.v(LOG_TAG, "  ${line.describeChars()}")
            }
        }
    }

    private fun saveOcrDebugImage(screenshot: Bitmap, page: OcrPage, captureId: String) {
        val store = debugStore ?: return
        val annotated = OcrDebugRenderer.render(screenshot, page)
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
    }
}
