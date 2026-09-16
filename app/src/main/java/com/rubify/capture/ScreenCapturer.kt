package com.rubify.capture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import com.rubify.LOG_TAG
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Silent screenshots through [AccessibilityService.takeScreenshot] (API 30+).
 * Unlike MediaProjection there is no dialog and no recording notification.
 *
 * [onCaptured] runs on the capture thread with a software ARGB_8888 bitmap
 * in physical device pixels, and takes ownership of it (must recycle).
 */
class ScreenCapturer(
    private val service: AccessibilityService,
    private val onCaptured: (Bitmap) -> Unit,
) {

    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "rubify-capture") }

    private val mainHandler = Handler(Looper.getMainLooper())

    private val inFlight = AtomicBoolean(false)

    private val callback = object : TakeScreenshotCallback {
        override fun onSuccess(result: ScreenshotResult) {
            try {
                val bitmap = result.hardwareBuffer.use { buffer ->
                    Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)?.let { hardware ->
                        try {
                            // Hardware bitmaps can't be read pixel by pixel; OCR and
                            // PNG encoding both need a software copy.
                            hardware.copy(Bitmap.Config.ARGB_8888, false)
                        } finally {
                            hardware.recycle()
                        }
                    }
                }
                if (bitmap == null) {
                    Log.w(LOG_TAG, "Screenshot could not be converted to a bitmap")
                    return
                }
                onCaptured(bitmap)
            } finally {
                inFlight.set(false)
            }
        }

        override fun onFailure(errorCode: Int) {
            inFlight.set(false)
            Log.w(LOG_TAG, "Screenshot failed: ${errorName(errorCode)}")
        }
    }

    /**
     * Schedules a capture of the default display after [SETTLE_DELAY_MS].
     * Returns false if the tap was dropped because a capture is still running.
     */
    fun capture(): Boolean {
        if (!inFlight.compareAndSet(false, true)) {
            Log.i(LOG_TAG, "Capture already in progress, tap dropped")
            return false
        }
        // When another service also uses the accessibility button, the tap
        // opens a system chooser. Capturing right away (measured: ~50 ms after
        // the click) records the chooser and its dim scrim over the content.
        mainHandler.postDelayed(::takeScreenshot, SETTLE_DELAY_MS)
        return true
    }

    private fun takeScreenshot() {
        try {
            service.takeScreenshot(Display.DEFAULT_DISPLAY, executor, callback)
        } catch (e: RuntimeException) {
            // e.g. SecurityException if canTakeScreenshot is missing from the config.
            inFlight.set(false)
            Log.e(LOG_TAG, "takeScreenshot rejected", e)
        }
    }

    fun shutdown() {
        mainHandler.removeCallbacksAndMessages(null)
        executor.shutdown()
    }

    private fun errorName(errorCode: Int): String = when (errorCode) {
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR -> "INTERNAL_ERROR"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS -> "NO_ACCESSIBILITY_ACCESS"
        // The system rate-limits screenshots to roughly one per second.
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> "INTERVAL_TIME_SHORT"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY -> "INVALID_DISPLAY"
        else -> "error code $errorCode"
    }

    private companion object {
        /** Long enough for the button chooser to finish its dismiss animation. */
        const val SETTLE_DELAY_MS = 400L
    }
}
