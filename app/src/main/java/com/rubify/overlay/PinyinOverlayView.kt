package com.rubify.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Insets
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.WindowInsets
import com.rubify.BuildConfig
import com.rubify.LOG_TAG
import com.rubify.R

/**
 * Draws [content]'s pinyin labels. Layout happens once the view knows where
 * it sits on screen, because screenshot pixels are screen coordinates.
 * [screenBounds] returns the display's current bounds and [systemBars] its
 * visible system bar insets, each null if unknown.
 */
// Built in code with its content, never inflated, so tools never need the
// standard constructors.
@SuppressLint("ViewConstructor")
internal class PinyinOverlayView(
    context: Context,
    private val content: OverlayContent,
    private val screenBounds: () -> Rect?,
    private val systemBars: () -> Insets?,
) : View(context) {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.overlay_text)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val measurePaint = Paint(textPaint).apply { textSize = MEASURE_SIZE }
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.overlay_background)
    }

    private var labels: List<PinyinLabel> = emptyList()

    init {
        // Decoration only; screen readers already read the text underneath.
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) labels = computeLabels()
    }

    private fun computeLabels(): List<PinyinLabel> {
        val location = IntArray(2).also { getLocationOnScreen(it) }
        val screen = screenBounds()
            ?: Rect(0, 0, location[0] + width, location[1] + height)
        val mapping = ScreenMapping.create(
            content.sourceWidth, content.sourceHeight,
            screen.width(), screen.height(),
            location[0] - screen.left, location[1] - screen.top,
        )
        if (mapping == null) {
            Log.w(LOG_TAG, "Display changed since the capture, overlay left empty")
            return emptyList()
        }

        // Text in the status and navigation bars is not annotated. This
        // window covers the whole screen, so display insets apply directly.
        val bars = Insets.max(
            systemBars() ?: Insets.NONE,
            rootWindowInsets?.getInsets(WindowInsets.Type.systemBars()) ?: Insets.NONE,
        )
        val metrics = measurePaint.fontMetrics
        val style = LabelStyle(
            minTextSize = sp(MIN_TEXT_SP),
            maxTextSize = sp(MAX_TEXT_SP),
            ascent = -metrics.ascent / MEASURE_SIZE,
            descent = metrics.descent / MEASURE_SIZE,
        )
        val result = PinyinLayout.layout(
            content = content,
            mapping = mapping,
            style = style,
            visibleTop = bars.top.toFloat(),
            visibleBottom = (height - bars.bottom).toFloat(),
            unitWidth = { measurePaint.measureText(it) / MEASURE_SIZE },
        )
        if (BuildConfig.DEBUG) {
            Log.d(
                LOG_TAG,
                "Overlay at ${location.toList()} ${width}x$height, screen $screen, " +
                    "bars $bars, $mapping, ${result.size} labels",
            )
        }
        return result
    }

    override fun onDraw(canvas: Canvas) {
        for (label in labels) {
            val bg = label.background
            val radius = (bg.bottom - bg.top) / 2
            canvas.drawRoundRect(bg.left, bg.top, bg.right, bg.bottom, radius, radius, backgroundPaint)
            textPaint.textSize = label.textSize
            canvas.drawText(label.text, label.centerX, label.baseline, textPaint)
        }
    }

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private companion object {
        const val MEASURE_SIZE = 100f
        const val MIN_TEXT_SP = 9f
        const val MAX_TEXT_SP = 28f
    }
}
