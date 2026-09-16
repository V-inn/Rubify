package com.rubify.pinyin

import com.rubify.ocr.OcrLine
import com.rubify.ocr.isHanzi

/**
 * Assigns pinyin (tone marks, citation tones) to each character of a line.
 * Consecutive hanzi are segmented into words first so that polyphonic
 * characters get the reading their word calls for; anything that is not a
 * hanzi (Latin, digits, punctuation) breaks the run and gets no pinyin.
 */
class PinyinAnnotator(private val dictionary: PinyinDictionary) {

    private val segmenter = WordSegmenter(dictionary)

    /** Pinyin per character of [line], aligned with `line.chars`. */
    fun annotate(line: OcrLine): List<String?> = annotate(line.chars.map { it.text })

    /**
     * Pinyin per element of [chars] (one character each), or null for
     * elements that are not hanzi or have no known reading.
     */
    fun annotate(chars: List<String>): List<String?> {
        val result = arrayOfNulls<String>(chars.size)
        var start = 0
        while (start < chars.size) {
            if (!isHanzi(chars[start])) {
                start++
                continue
            }
            var end = start
            while (end < chars.size && isHanzi(chars[end])) end++
            annotateRun(chars.subList(start, end), result, start)
            start = end
        }
        return result.asList()
    }

    private fun annotateRun(run: List<String>, result: Array<String?>, offset: Int) {
        var position = 0
        for (length in segmenter.segment(run)) {
            dictionary.readings(run.subList(position, position + length))
                .forEachIndexed { k, reading -> result[offset + position + k] = reading }
            position += length
        }
    }

    /** [text] with each character's pinyin, space separated; for logs and tests. */
    fun pinyinOf(text: String): String {
        val chars = text.codePoints().toArray().map { String(Character.toChars(it)) }
        return annotate(chars).zip(chars) { pinyin, char -> pinyin ?: char }.joinToString(" ")
    }
}
