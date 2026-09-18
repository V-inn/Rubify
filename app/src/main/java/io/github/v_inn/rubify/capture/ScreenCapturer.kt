package io.github.v_inn.rubify.capture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import io.github.v_inn.rubify.LOG_TAG
import java.util.concurrent.Executor

/**
 * Silent screenshots through [AccessibilityService.takeScreenshot] (API 30+).
 * Unlike MediaProjection there is no dialog and no recording notification.
 *
 * Results are delivered on [executor]. The caller is responsible for not
 * starting a capture while one is still running.
 */
class ScreenCapturer(
    private val service: AccessibilityService,
    private val executor: Executor,
) {

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Captures the default display after [settleDelayMs]. Exactly one of the
     * callbacks runs, on the executor. [onCaptured] receives a software
     * ARGB_8888 bitmap in physical device pixels and takes ownership of it
     * (must recycle).
     */
    fun capture(settleDelayMs: Long, onCaptured: (Bitmap) -> Unit, onFailed: () -> Unit) {
        mainHandler.postDelayed({ takeScreenshot(onCaptured, onFailed) }, settleDelayMs)
    }

    private fun takeScreenshot(onCaptured: (Bitmap) -> Unit, onFailed: () -> Unit) {
        val callback = object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
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
                    onFailed()
                } else {
                    onCaptured(bitmap)
                }
            }

            override fun onFailure(errorCode: Int) {
                Log.w(LOG_TAG, "Screenshot failed: ${errorName(errorCode)}")
                onFailed()
            }
        }
        try {
            service.takeScreenshot(Display.DEFAULT_DISPLAY, executor, callback)
        } catch (e: RuntimeException) {
            // e.g. SecurityException if canTakeScreenshot is missing from the config.
            Log.e(LOG_TAG, "takeScreenshot rejected", e)
            onFailed()
        }
    }

    /** Cancels a capture that is still waiting for its settle delay. */
    fun shutdown() {
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun errorName(errorCode: Int): String = when (errorCode) {
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR -> "INTERNAL_ERROR"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS -> "NO_ACCESSIBILITY_ACCESS"
        // The system rate-limits screenshots to roughly one per second.
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> "INTERVAL_TIME_SHORT"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY -> "INVALID_DISPLAY"
        else -> "error code $errorCode"
    }
}
