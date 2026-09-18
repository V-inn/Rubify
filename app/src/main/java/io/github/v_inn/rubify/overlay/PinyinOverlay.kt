package io.github.v_inn.rubify.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.Insets
import android.graphics.PixelFormat
import android.graphics.Rect
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import io.github.v_inn.rubify.LOG_TAG

/**
 * The full-screen pinyin window. `TYPE_ACCESSIBILITY_OVERLAY` needs no
 * overlay permission, and `FLAG_NOT_TOUCHABLE` passes every touch through to
 * the app underneath. Main thread only.
 */
class PinyinOverlay(private val service: AccessibilityService) {

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var view: View? = null

    val isShowing: Boolean get() = view != null

    fun show(content: OverlayContent) {
        hide()
        val overlayView = PinyinOverlayView(service, content, ::screenBounds, ::systemBars)
        try {
            windowManager.addView(overlayView, layoutParams())
            view = overlayView
            Log.i(LOG_TAG, "Overlay shown with ${content.labelCount} labels")
        } catch (e: RuntimeException) {
            // e.g. BadTokenException when the service is being disconnected.
            Log.e(LOG_TAG, "Could not show the overlay", e)
        }
    }

    fun hide() {
        val current = view ?: return
        view = null
        windowManager.removeViewImmediate(current)
        Log.i(LOG_TAG, "Overlay hidden")
    }

    private fun screenBounds(): Rect? =
        try {
            windowManager.maximumWindowMetrics.bounds
        } catch (e: RuntimeException) {
            Log.w(LOG_TAG, "Display bounds unavailable, using the overlay's own size", e)
            null
        }

    /**
     * Visible status and navigation bar insets of the display. The overlay
     * window's own insets are all zero on device (it lays out past them).
     */
    private fun systemBars(): Insets? =
        try {
            windowManager.currentWindowMetrics.windowInsets.getInsets(WindowInsets.Type.systemBars())
        } catch (e: RuntimeException) {
            Log.w(LOG_TAG, "System bar insets unavailable", e)
            null
        }

    private fun layoutParams() = LayoutParams(
        LayoutParams.MATCH_PARENT,
        LayoutParams.MATCH_PARENT,
        LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        LayoutParams.FLAG_NOT_TOUCHABLE or
            LayoutParams.FLAG_NOT_FOCUSABLE or
            LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        // Cover the status bar, navigation bar and cutout too, so view
        // coordinates line up with the full-screen screenshot.
        gravity = Gravity.TOP or Gravity.START
        fitInsetsTypes = 0
        layoutInDisplayCutoutMode = LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        title = "Rubify pinyin"
    }
}
