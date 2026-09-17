package com.rubify.overlay

import com.rubify.ocr.OcrPage
import com.rubify.ocr.PixelBox

/*
 * Overlay geometry, pure Kotlin so it can be unit tested: what to draw and
 * where, given the OCR result and the overlay window's place on screen.
 */

/** One hanzi to annotate: its box in screenshot pixels and its pinyin. */
data class AnnotatedChar(val box: PixelBox, val pinyin: String)

/** The annotated hanzi of one OCR line, in reading order. */
data class AnnotatedLine(val chars: List<AnnotatedChar>)

/** Everything the overlay shows for one screenshot of the given size. */
data class OverlayContent(val sourceWidth: Int, val sourceHeight: Int, val lines: List<AnnotatedLine>) {
    val labelCount: Int get() = lines.sumOf { it.chars.size }
}

/**
 * Keeps the hanzi that have a reading. [pinyin] holds each line's readings,
 * aligned with `page.lines` and their chars.
 */
fun overlayContentOf(page: OcrPage, pinyin: List<List<String?>>): OverlayContent =
    OverlayContent(
        sourceWidth = page.width,
        sourceHeight = page.height,
        lines = page.lines.zip(pinyin) { line, readings ->
            AnnotatedLine(
                line.chars.zip(readings).mapNotNull { (char, reading) ->
                    reading?.let { AnnotatedChar(char.box, it) }
                }
            )
        }.filter { it.chars.isNotEmpty() },
    )

/**
 * Maps screenshot pixels to overlay view coordinates. The screenshot covers
 * the whole display, so a point is scaled to the display's current size and
 * shifted by where the overlay view sits on screen.
 */
data class ScreenMapping(val scaleX: Float, val scaleY: Float, val offsetX: Float, val offsetY: Float) {
    fun x(px: Int): Float = px * scaleX - offsetX
    fun y(px: Int): Float = px * scaleY - offsetY

    companion object {
        /**
         * Returns null when the display orientation no longer matches the
         * screenshot (rotated since the capture), because the boxes would
         * land on the wrong text.
         */
        fun create(
            sourceWidth: Int,
            sourceHeight: Int,
            screenWidth: Int,
            screenHeight: Int,
            viewLeft: Int,
            viewTop: Int,
        ): ScreenMapping? {
            if (sourceWidth <= 0 || sourceHeight <= 0) return null
            if ((sourceWidth > sourceHeight) != (screenWidth > screenHeight)) return null
            return ScreenMapping(
                scaleX = screenWidth / sourceWidth.toFloat(),
                scaleY = screenHeight / sourceHeight.toFloat(),
                offsetX = viewLeft.toFloat(),
                offsetY = viewTop.toFloat(),
            )
        }
    }
}

/** An axis-aligned rectangle in overlay view coordinates. */
data class OverlayRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** One pinyin label, ready to draw with center-aligned text. */
data class PinyinLabel(
    val text: String,
    val centerX: Float,
    val baseline: Float,
    val textSize: Float,
    val background: OverlayRect,
)

/**
 * Label sizing, in pixels or as fractions of the text size.
 * [ascent] and [descent] are the font's, per pixel of text size.
 */
data class LabelStyle(
    val minTextSize: Float,
    val maxTextSize: Float,
    val ascent: Float,
    val descent: Float,
    /** Text size relative to the line's typical character height. */
    val sizeToCharHeight: Float = 0.4f,
    /** Share of the distance between neighbouring characters a label may use. */
    val fill: Float = 0.92f,
    val padding: Float = 0.12f,
    val gapAboveChar: Float = 0.1f,
)

object PinyinLayout {

    /**
     * Places one label above each annotated character. Characters whose
     * center falls outside [visibleTop]..[visibleBottom] (system bars) are
     * skipped. Each line uses one text size: [LabelStyle.sizeToCharHeight]
     * of its median character height, shrunk until no label is wider than
     * the distance to its nearest neighbour, but never below
     * [LabelStyle.minTextSize]. [unitWidth] measures text at size 1.
     */
    fun layout(
        content: OverlayContent,
        mapping: ScreenMapping,
        style: LabelStyle,
        visibleTop: Float,
        visibleBottom: Float,
        unitWidth: (String) -> Float,
    ): List<PinyinLabel> {
        val labels = ArrayList<PinyinLabel>(content.labelCount)
        for (line in content.lines) {
            val chars = line.chars
                .map { MappedChar(it, mapping) }
                .filter { it.centerY in visibleTop..visibleBottom }
            if (chars.isEmpty()) continue

            val charHeight = chars.map { it.height }.sorted()[chars.size / 2]
            var size = (charHeight * style.sizeToCharHeight).coerceIn(style.minTextSize, style.maxTextSize)
            chars.forEachIndexed { i, char ->
                val room = minOf(
                    chars.getOrNull(i - 1)?.let { char.centerX - it.centerX } ?: Float.MAX_VALUE,
                    chars.getOrNull(i + 1)?.let { it.centerX - char.centerX } ?: Float.MAX_VALUE,
                )
                val width = unitWidth(char.pinyin)
                if (room < Float.MAX_VALUE && width > 0f) {
                    size = minOf(size, style.fill * room / width)
                }
            }
            size = maxOf(size, style.minTextSize)

            val padding = style.padding * size
            for (char in chars) {
                val halfWidth = unitWidth(char.pinyin) * size / 2 + padding
                val bottom = char.top - style.gapAboveChar * size
                val baseline = bottom - padding - style.descent * size
                labels += PinyinLabel(
                    text = char.pinyin,
                    centerX = char.centerX,
                    baseline = baseline,
                    textSize = size,
                    background = OverlayRect(
                        left = char.centerX - halfWidth,
                        top = baseline - style.ascent * size - padding,
                        right = char.centerX + halfWidth,
                        bottom = bottom,
                    ),
                )
            }
        }
        return labels
    }

    private class MappedChar(char: AnnotatedChar, mapping: ScreenMapping) {
        val pinyin = char.pinyin
        val top = mapping.y(char.box.top)
        val bottom = mapping.y(char.box.bottom)
        val height = bottom - top
        val centerX = (mapping.x(char.box.left) + mapping.x(char.box.right)) / 2
        val centerY = (top + bottom) / 2
    }
}
