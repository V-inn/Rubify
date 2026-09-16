package com.rubify.debug

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.rubify.ocr.OcrPage
import com.rubify.ocr.PixelBox

/**
 * Draws OCR boxes and pinyin over a copy of the screenshot so recognition,
 * readings and per-character alignment can be checked by eye: lines in blue,
 * hanzi in red with their pinyin above, other characters in gray.
 */
internal object OcrDebugRenderer {

    private val linePaint = strokePaint(Color.BLUE, 3f)
    private val hanziPaint = strokePaint(Color.RED, 2f)
    private val otherPaint = strokePaint(Color.GRAY, 1f)
    private val pinyinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(200, 0, 0)
        textAlign = Paint.Align.CENTER
    }

    /**
     * Returns a new bitmap; the caller owns it and must recycle it.
     * [pinyin] holds each line's readings, aligned with `page.lines`.
     */
    fun render(screenshot: Bitmap, page: OcrPage, pinyin: List<List<String?>>): Bitmap {
        val out = screenshot.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        page.lines.forEachIndexed { lineIndex, line ->
            canvas.drawBox(line.box, linePaint)
            line.chars.forEachIndexed { charIndex, char ->
                canvas.drawBox(char.box, if (char.isHanzi) hanziPaint else otherPaint)
                val reading = pinyin.getOrNull(lineIndex)?.getOrNull(charIndex) ?: return@forEachIndexed
                pinyinPaint.textSize = char.box.height * 0.4f
                canvas.drawText(
                    reading,
                    (char.box.left + char.box.right) / 2f,
                    char.box.top - pinyinPaint.descent(),
                    pinyinPaint,
                )
            }
        }
        return out
    }

    private fun Canvas.drawBox(box: PixelBox, paint: Paint) {
        drawRect(box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat(), paint)
    }

    private fun strokePaint(color: Int, width: Float) = Paint().apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = width
    }
}
