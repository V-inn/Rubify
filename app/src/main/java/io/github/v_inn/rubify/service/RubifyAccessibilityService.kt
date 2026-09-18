package io.github.v_inn.rubify.service

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.service.quicksettings.TileService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import io.github.v_inn.rubify.BuildConfig
import io.github.v_inn.rubify.LOG_TAG
import io.github.v_inn.rubify.capture.ScreenCapturer
import io.github.v_inn.rubify.consent.Consent
import io.github.v_inn.rubify.consent.ConsentActivity
import io.github.v_inn.rubify.debug.DebugImageStore
import io.github.v_inn.rubify.debug.OcrDebugRenderer
import io.github.v_inn.rubify.ocr.HanziRecognizer
import io.github.v_inn.rubify.ocr.OcrPage
import io.github.v_inn.rubify.ocr.describeChars
import io.github.v_inn.rubify.overlay.ControlBubble
import io.github.v_inn.rubify.overlay.OverlayContent
import io.github.v_inn.rubify.overlay.PinyinOverlay
import io.github.v_inn.rubify.overlay.overlayContentOf
import io.github.v_inn.rubify.pinyin.PinyinAnnotator
import io.github.v_inn.rubify.pinyin.PinyinAssets
import io.github.v_inn.rubify.tile.ReadingTileService
import java.lang.ref.WeakReference
import java.util.concurrent.Executor

/**
 * Reads the screen when the user asks: the accessibility button, the Quick
 * Settings tile, or the control bubble (see [ReadingSession]). Nothing is
 * read until the user has accepted the disclosure ([Consent]); until then a
 * tap opens it. It subscribes to no
 * accessibility events and cannot read window content (see
 * res/xml/accessibility_service_config.xml).
 *
 * A reading is capture, OCR, pinyin, overlay. Its steps run on the worker;
 * session state and the overlay windows live on the main thread.
 */
class RubifyAccessibilityService : AccessibilityService(), ReadingSession.Host {

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

    /** Worker-confined: set by the load task queued first on the worker. */
    private var annotator: PinyinAnnotator? = null

    // Main-thread confined.
    private var overlay: PinyinOverlay? = null
    private var controls: ControlBubble? = null
    private val session = ReadingSession(this)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var scheduledAction: Runnable? = null
    private var orientation = Configuration.ORIENTATION_UNDEFINED

    /** Main thread. For the tile. */
    val isSessionActive: Boolean get() = session.isActive

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
            if (Consent.isGiven(this@RubifyAccessibilityService)) {
                session.onButtonClicked()
            } else {
                openDisclosure()
            }
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
        overlay = PinyinOverlay(this)
        controls = ControlBubble(
            this,
            onRefresh = session::onRefreshRequested,
            onTogglePinyin = session::onTogglePinyinRequested,
            onClose = session::onCloseRequested,
        )
        orientation = resources.configuration.orientation
        if (BuildConfig.DEBUG) debugStore = DebugImageStore(this)

        val controller = accessibilityButtonController
        controller.registerAccessibilityButtonCallback(buttonCallback)
        buttonController = controller
        isButtonAvailable = controller.isAccessibilityButtonAvailable
        running = WeakReference(this)
        Log.i(LOG_TAG, "Service connected, accessibility button available=$isButtonAvailable")
        requestTileUpdate()
    }

    /** Main thread. Called by [ReadingTileService]. */
    fun onTileClicked() {
        Log.i(LOG_TAG, "Tile clicked")
        if (!Consent.isGiven(this)) {
            // The tile checks first; this is the backstop.
            closeQuickSettings()
            openDisclosure()
            return
        }
        if (!session.isActive) closeQuickSettings()
        session.onTileClicked()
    }

    /** Main thread. Called by [ConsentActivity]. */
    fun onConsentWithdrawn() {
        if (session.isActive) session.onCloseRequested()
    }

    private fun openDisclosure() {
        Log.i(LOG_TAG, "No consent yet, opening the disclosure")
        startActivity(
            Intent(this, ConsentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun closeQuickSettings() {
        val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE
        } else {
            // Android 11 has no dismiss action; Back closes the open panel.
            GLOBAL_ACTION_BACK
        }
        if (!performGlobalAction(action)) Log.w(LOG_TAG, "Could not close Quick Settings")
    }

    private fun requestTileUpdate() {
        try {
            TileService.requestListeningState(this, ComponentName(this, ReadingTileService::class.java))
        } catch (e: RuntimeException) {
            Log.w(LOG_TAG, "Tile update request failed", e)
        }
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

    // ReadingSession.Host, all on the main thread.

    override fun startReading(generation: Int, settleDelayMs: Long) {
        val capturer = screenCapturer ?: return
        capturer.capture(
            settleDelayMs,
            onCaptured = { bitmap -> onScreenshot(bitmap, generation) },
            onFailed = { mainHandler.post { session.onReadingFailed(generation) } },
        )
    }

    override fun showPinyin(content: OverlayContent) {
        overlay?.show(content)
        controls?.bringToFront()
    }

    override fun hidePinyin() {
        overlay?.hide()
    }

    override fun showControls(pinyinVisible: Boolean) {
        controls?.show(pinyinVisible)
    }

    override fun hideControls() {
        controls?.hide()
    }

    override fun sessionChanged(active: Boolean) {
        requestTileUpdate()
    }

    override fun schedule(delayMs: Long, action: () -> Unit) {
        cancelScheduled()
        val runnable = Runnable {
            scheduledAction = null
            action()
        }
        scheduledAction = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    override fun cancelScheduled() {
        scheduledAction?.let(mainHandler::removeCallbacks)
        scheduledAction = null
    }

    // Reading pipeline, on the worker.

    /** Owns [bitmap] and must recycle it. */
    private fun onScreenshot(bitmap: Bitmap, generation: Int) {
        val recognizer = recognizer
        val worker = worker
        if (recognizer == null || worker == null) {
            bitmap.recycle()
            mainHandler.post { session.onReadingFailed(generation) }
            return
        }
        Log.i(LOG_TAG, "Screenshot ${bitmap.width}x${bitmap.height}")
        val captureId = debugStore?.newCaptureId()

        // OCR runs on ML Kit's threads and its result is queued on the worker.
        recognizer.recognize(bitmap, worker) { page, recognitionMs ->
            try {
                if (page == null) {
                    mainHandler.post { session.onReadingFailed(generation) }
                    return@recognize
                }
                val pinyin = annotate(page, recognitionMs)
                val content = overlayContentOf(page, pinyin)
                mainHandler.post { session.onReadingFinished(generation, content) }
                // Debug images are written after the overlay is on its way.
                if (captureId != null) {
                    debugStore?.save(bitmap, captureId, "screenshot")
                    saveOcrDebugImage(bitmap, page, pinyin, captureId)
                }
            } finally {
                bitmap.recycle()
            }
        }
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
                Log.d(
                    LOG_TAG,
                    "  " + line.chars.zip(readings) { c, p -> if (p != null) "${c.text}($p)" else c.text }
                        .joinToString(""),
                )
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.orientation != orientation) {
            orientation = newConfig.orientation
            Log.i(LOG_TAG, "Orientation changed to $orientation")
            session.onScreenMoved()
            // Place the bubble again for the new screen shape.
            controls?.bringToFront()
        }
    }

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
        if (running?.get() === this) running = null
        if (session.isActive) session.onButtonClicked()
        mainHandler.removeCallbacksAndMessages(null)
        overlay?.hide()
        overlay = null
        controls?.hide()
        controls = null
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

    companion object {
        /** The connected service in this process. Main thread only. */
        private var running: WeakReference<RubifyAccessibilityService>? = null

        fun running(): RubifyAccessibilityService? = running?.get()
    }
}
