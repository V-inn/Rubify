package com.rubify.overlay

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.graphics.Point
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
 * the session. Dragging any button moves the bubble; its position is kept
 * while the service runs.
 * Touches outside the bubble go to the app underneath. Main thread only.
 */
class ControlBubble(
    private val service: AccessibilityService,
    private val onRefresh: () -> Unit,
    private val onTogglePinyin: () -> Unit,
    private val onClose: () -> Unit,
) {

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var view: View? = null
    private var position: Point? = null

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
        val start = position ?: defaultPosition(screen, root)
        params.x = start.x.coerceIn(0, maxOf(0, screen.width() - root.measuredWidth))
        params.y = start.y.coerceIn(0, maxOf(0, screen.height() - root.measuredHeight))

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

    /** Re-adds the bubble so it sits above a window added after it. */
    fun bringToFront() {
        val current = view ?: return
        val pinyinVisible = current.findViewById<View>(R.id.toggle_pinyin).tag as? Boolean ?: true
        hide()
        show(pinyinVisible)
    }

    private fun defaultPosition(screen: Rect, root: View): Point {
        val margin = (MARGIN_DP * service.resources.displayMetrics.density).toInt()
        return Point(screen.width() - root.measuredWidth - margin, screen.height() * 2 / 5)
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
                        params.x = (startX + dx.toInt()).coerceIn(0, maxOf(0, screen.width() - root.width))
                        params.y = (startY + dy.toInt()).coerceIn(0, maxOf(0, screen.height() - root.height))
                        windowManager.updateViewLayout(root, params)
                        position = Point(params.x, params.y)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    v.isPressed = false
                    if (!dragging) v.performClick()
                }
                MotionEvent.ACTION_CANCEL -> v.isPressed = false
            }
            return true
        }
    }

    private companion object {
        const val MARGIN_DP = 12
    }
}
