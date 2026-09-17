package com.rubify.overlay

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.widget.ImageButton
import com.rubify.R
import kotlin.math.hypot

/**
 * The small floating bubble shown with the pinyin: Refresh reads the screen
 * again (after scrolling, say), the eye hides or shows the pinyin, Close ends
 * the session. Dragging any button moves the bubble. Its position is saved
 * as a fraction of the screen, so it survives rotations and restarts.
 * Touches outside the bubble go to the app underneath. Main thread only.
 */
class ControlBubble(
    private val service: AccessibilityService,
    private val onRefresh: () -> Unit,
    private val onTogglePinyin: () -> Unit,
    private val onClose: () -> Unit,
) {

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val prefs = service.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val margin = (MARGIN_DP * service.resources.displayMetrics.density).toInt()
    private var view: View? = null

    val isShowing: Boolean get() = view != null

    /**
     * Shows the bubble, or updates it if already showing. [pinyinVisible]
     * picks the toggle's icon: hide while pinyin is up, show otherwise.
     */
    // The bubble is a window's root view: there is no parent to inflate into.
    @SuppressLint("InflateParams")
    fun show(pinyinVisible: Boolean) {
        view?.let {
            bindToggle(it, pinyinVisible)
            return
        }
        val context = ContextThemeWrapper(service, R.style.Theme_Rubify)
        val root = LayoutInflater.from(context).inflate(R.layout.overlay_controls, null)
        root.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val params = layoutParams()
        val screen = windowManager.maximumWindowMetrics.bounds
        val (x, y) = savedPlacement().toPixels(
            screen.width(), screen.height(), root.measuredWidth, root.measuredHeight, margin,
        )
        params.x = x
        params.y = y

        val drag = DragToMove(root, params, screen)
        root.findViewById<View>(R.id.refresh).apply {
            setOnClickListener { onRefresh() }
            setOnTouchListener(drag)
        }
        root.findViewById<View>(R.id.toggle_pinyin).apply {
            setOnClickListener { onTogglePinyin() }
            setOnTouchListener(drag)
        }
        root.findViewById<View>(R.id.close).apply {
            setOnClickListener { onClose() }
            setOnTouchListener(drag)
        }
        bindToggle(root, pinyinVisible)
        windowManager.addView(root, params)
        view = root
    }

    private fun bindToggle(root: View, pinyinVisible: Boolean) {
        root.findViewById<ImageButton>(R.id.toggle_pinyin).apply {
            tag = pinyinVisible
            setImageResource(if (pinyinVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility)
            contentDescription = context.getString(
                if (pinyinVisible) R.string.hide_pinyin else R.string.show_pinyin,
            )
        }
    }

    fun hide() {
        val current = view ?: return
        view = null
        windowManager.removeViewImmediate(current)
    }

    /**
     * Re-adds the bubble so it sits above a window added after it, or is
     * placed again for a rotated display.
     */
    fun bringToFront() {
        val current = view ?: return
        val pinyinVisible = current.findViewById<View>(R.id.toggle_pinyin).tag as? Boolean ?: true
        hide()
        show(pinyinVisible)
    }

    private fun savedPlacement(): BubblePlacement =
        if (prefs.contains(KEY_X) && prefs.contains(KEY_Y)) {
            BubblePlacement(prefs.getFloat(KEY_X, 1f), prefs.getFloat(KEY_Y, 0.4f))
        } else {
            BubblePlacement.DEFAULT
        }

    private fun savePlacement(root: View, params: LayoutParams, screen: Rect) {
        val placement = BubblePlacement.fromPixels(
            params.x, params.y, screen.width(), screen.height(), root.width, root.height, margin,
        )
        prefs.edit().putFloat(KEY_X, placement.x).putFloat(KEY_Y, placement.y).apply()
    }

    private fun layoutParams() = LayoutParams(
        LayoutParams.WRAP_CONTENT,
        LayoutParams.WRAP_CONTENT,
        LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        // Not focusable also means not touch-modal: touches outside the
        // bubble reach the app underneath.
        LayoutParams.FLAG_NOT_FOCUSABLE or LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        title = "Rubify controls"
    }

    /** Moves the window once a touch travels past the touch slop; otherwise clicks. */
    private inner class DragToMove(
        private val root: View,
        private val params: LayoutParams,
        private val screen: Rect,
    ) : View.OnTouchListener {

        private val touchSlop = ViewConfiguration.get(root.context).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    v.isPressed = true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && hypot(dx, dy) > touchSlop) {
                        dragging = true
                        v.isPressed = false
                    }
                    if (dragging && view === root) {
                        params.x = (startX + dx.toInt())
                            .coerceIn(BubblePlacement.range(screen.width(), root.width, margin))
                        params.y = (startY + dy.toInt())
                            .coerceIn(BubblePlacement.range(screen.height(), root.height, margin))
                        windowManager.updateViewLayout(root, params)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    v.isPressed = false
                    if (dragging) savePlacement(root, params, screen) else v.performClick()
                }
                MotionEvent.ACTION_CANCEL -> v.isPressed = false
            }
            return true
        }
    }

    private companion object {
        const val MARGIN_DP = 12
        const val PREFS = "control_bubble"
        const val KEY_X = "x"
        const val KEY_Y = "y"
    }
}
