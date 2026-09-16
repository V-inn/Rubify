package com.rubify.service

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.rubify.BuildConfig
import com.rubify.LOG_TAG
import com.rubify.capture.DebugScreenshotStore
import com.rubify.capture.ScreenCapturer

/**
 * Reacts to exactly one thing: a tap on the accessibility button. It subscribes
 * to no accessibility events and cannot read window content (see
 * res/xml/accessibility_service_config.xml).
 */
class RubifyAccessibilityService : AccessibilityService() {

    private var buttonController: AccessibilityButtonController? = null
    private var screenCapturer: ScreenCapturer? = null
    private var screenshotStore: DebugScreenshotStore? = null

    /**
     * Last known button availability. The button disappears in full-screen
     * apps; the Quick Settings tile (phase 6) is the fallback.
     */
    @Volatile
    var isButtonAvailable: Boolean = false
        private set

    private val buttonCallback = object : AccessibilityButtonController.AccessibilityButtonCallback() {
        override fun onClicked(controller: AccessibilityButtonController) {
            Log.i(LOG_TAG, "Accessibility button clicked")
            screenCapturer?.capture()
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
        if (BuildConfig.DEBUG) screenshotStore = DebugScreenshotStore(this)
        screenCapturer = ScreenCapturer(this, ::onScreenshot)

        val controller = accessibilityButtonController
        controller.registerAccessibilityButtonCallback(buttonCallback)
        buttonController = controller
        isButtonAvailable = controller.isAccessibilityButtonAvailable
        Log.i(LOG_TAG, "Service connected, accessibility button available=$isButtonAvailable")
    }

    /** Runs on the capture thread. Owns [bitmap] and must recycle it. */
    private fun onScreenshot(bitmap: Bitmap) {
        try {
            Log.i(LOG_TAG, "Screenshot ${bitmap.width}x${bitmap.height}")
            screenshotStore?.save(bitmap)
        } finally {
            bitmap.recycle()
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
        screenshotStore = null
    }
}
