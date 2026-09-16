package com.rubify.debug

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.rubify.ocr.OcrPage
import com.rubify.ocr.PixelBox

/**
 * Draws OCR boxes over a copy of the screenshot so recognition and
 * per-character alignment can be checked by eye: lines in blue, hanzi in red,
 * other characters in gray.
 */
internal object OcrDebugRenderer {

    private val linePaint = strokePaint(Color.BLUE, 3f)
    private val hanziPaint = strokePaint(Color.RED, 2f)
    private val otherPaint = strokePaint(Color.GRAY, 1f)

    /** Returns a new bitmap; the caller owns it and must recycle it. */
    fun render(screenshot: Bitmap, page: OcrPage): Bitmap {
        val out = screenshot.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        for (line in page.lines) {
            canvas.drawBox(line.box, linePaint)
            for (char in line.chars) {
                canvas.drawBox(char.box, if (char.isHanzi) hanziPaint else otherPaint)
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
