package io.github.v_inn.rubify.ocr

import java.util.Locale

/*
 * OCR result model. Pure Kotlin on purpose: android.graphics.Rect is only a
 * stub in JVM unit tests, and the pinyin and overlay phases build on this.
 */

/** Axis-aligned box in screenshot pixels (physical device pixels). */
data class PixelBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    override fun toString() = "[$left,$top,$right,$bottom]"
}

/** One recognized character (an ML Kit `Text.Symbol`). */
data class OcrChar(val text: String, val box: PixelBox, val confidence: Float) {
    val isHanzi: Boolean get() = isHanzi(text)
}

/** One line of text, with its characters in reading order. */
data class OcrLine(val text: String, val box: PixelBox, val chars: List<OcrChar>)

/** Everything recognized in one screenshot of [width] x [height] pixels. */
data class OcrPage(val width: Int, val height: Int, val lines: List<OcrLine>) {
    val hanziCount: Int get() = lines.sumOf { line -> line.chars.count { it.isHanzi } }
}

/**
 * True when [text] is exactly one Han ideograph: CJK Unified Ideographs
 * (all extensions), compatibility ideographs and 〇. Punctuation, kana,
 * hangul, radicals and Latin are not.
 */
fun isHanzi(text: String): Boolean {
    if (text.isEmpty()) return false
    val codePoint = text.codePointAt(0)
    return Character.charCount(codePoint) == text.length &&
        Character.isIdeographic(codePoint) &&
        Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN
}

/**
 * Compact per-character dump for debug logs: text, box and confidence,
 * e.g. `你[10,20,40,52]0.93 好[42,20,72,52]0.88`.
 */
fun OcrLine.describeChars(): String = chars.joinToString(" ") {
    "${it.text}${it.box}${"%.2f".format(Locale.ROOT, it.confidence)}"
}
