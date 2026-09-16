package com.rubify.pinyin

import java.io.BufferedReader
import kotlin.math.ln

/**
 * Read-only pinyin dictionary built by tools/pinyin-data from assets/pinyin.
 *
 * Holds a default reading and frequency per character, and a frequency (plus
 * an explicit reading when the default would be wrong) per word. Words live in
 * one String searched by binary search, which keeps ~156k entries in a few MB
 * instead of the tens of MB a HashMap would take.
 */
class PinyinDictionary private constructor(
    private val charCodePoints: IntArray,
    private val charReadings: Array<String>,
    private val charLogProbs: FloatArray,
    private val wordText: String,
    private val wordStarts: IntArray,
    private val wordLogProbs: FloatArray,
    private val wordReadings: Array<String?>,
    /** Log probability of a character or word the dictionary has never seen. */
    val unknownLogProb: Float,
) {

    val wordCount: Int get() = wordLogProbs.size

    /** Default reading of a single character, or null if unknown. */
    fun charReading(codePoint: Int): String? {
        val index = charCodePoints.binarySearch(codePoint)
        return if (index >= 0) charReadings[index] else null
    }

    fun charLogProb(codePoint: Int): Float {
        val index = charCodePoints.binarySearch(codePoint)
        return if (index >= 0) charLogProbs[index] else unknownLogProb
    }

    /** Log probability of a word of two or more characters, or null if not a word. */
    fun wordLogProb(word: String): Float? {
        val index = findWord(word)
        return if (index >= 0) wordLogProbs[index] else null
    }

    /** True if some word starts with [prefix] (including [prefix] itself). */
    fun hasWordWithPrefix(prefix: String): Boolean {
        val index = findWord(prefix)
        if (index >= 0) return true
        val insertion = -index - 1
        return insertion < wordCount && wordStartsWith(insertion, prefix)
    }

    /**
     * Readings for [chars], one per element, read as a single word: the
     * word's explicit reading if it has one, otherwise each character's
     * default. Elements with no known reading are null.
     */
    fun readings(chars: List<String>): List<String?> {
        if (chars.size >= 2) {
            val index = findWord(chars.joinToString(""))
            val explicit = if (index >= 0) wordReadings[index] else null
            if (explicit != null) return explicit.split(' ')
        }
        return chars.map { charReading(it.codePointAt(0)) }
    }

    /** Index of [word], or `-(insertion point) - 1` like [IntArray.binarySearch]. */
    private fun findWord(word: String): Int {
        var low = 0
        var high = wordCount - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val cmp = compareWord(mid, word)
            when {
                cmp < 0 -> low = mid + 1
                cmp > 0 -> high = mid - 1
                else -> return mid
            }
        }
        return -(low + 1)
    }

    /** Same order as [String.compareTo]: UTF-16 code units, prefixes first. */
    private fun compareWord(index: Int, other: String): Int {
        val start = wordStarts[index]
        val length = wordStarts[index + 1] - start
        for (i in 0 until minOf(length, other.length)) {
            val diff = wordText[start + i] - other[i]
            if (diff != 0) return diff
        }
        return length - other.length
    }

    private fun wordStartsWith(index: Int, prefix: String): Boolean {
        val start = wordStarts[index]
        val length = wordStarts[index + 1] - start
        return length >= prefix.length && wordText.regionMatches(start, prefix, 0, prefix.length)
    }

    companion object {
        /**
         * Parses `chars.tsv` (char, reading, frequency; ascending code points)
         * and `words.tsv` (word, frequency, optional reading; ascending
         * UTF-16 order). Throws [IllegalArgumentException] on malformed input.
         */
        fun load(chars: BufferedReader, words: BufferedReader): PinyinDictionary {
            val codePoints = IntArrayBuilder()
            val readings = ArrayList<String>()
            val charFreqs = IntArrayBuilder()
            // Few distinct syllables (~1.5k) back ~44k characters.
            val syllables = HashMap<String, String>()
            chars.forEachLine { line ->
                val fields = line.split('\t')
                require(fields.size == 3) { "Bad chars.tsv line: $line" }
                val codePoint = fields[0].codePointAt(0)
                require(codePoints.size == 0 || codePoint > codePoints.last()) {
                    "chars.tsv is not sorted at $line"
                }
                codePoints.add(codePoint)
                readings.add(syllables.getOrPut(fields[1]) { fields[1] })
                charFreqs.add(fields[2].toInt())
            }

            val text = StringBuilder()
            val starts = IntArrayBuilder().apply { add(0) }
            val wordFreqs = IntArrayBuilder()
            val wordReadings = ArrayList<String?>()
            var previous = ""
            words.forEachLine { line ->
                val fields = line.split('\t')
                require(fields.size == 2 || fields.size == 3) { "Bad words.tsv line: $line" }
                val word = fields[0]
                require(word > previous) { "words.tsv is not sorted at $line" }
                previous = word
                text.append(word)
                starts.add(text.length)
                wordFreqs.add(fields[1].toInt())
                wordReadings.add(fields.getOrNull(2))
            }

            val total = (charFreqs.sum() + wordFreqs.sum()).toDouble()
            fun logProb(freq: Int) = ln(maxOf(freq, 1) / total).toFloat()

            return PinyinDictionary(
                charCodePoints = codePoints.toArray(),
                charReadings = readings.toTypedArray(),
                charLogProbs = charFreqs.toArray().let { f -> FloatArray(f.size) { logProb(f[it]) } },
                wordText = text.toString(),
                wordStarts = starts.toArray(),
                wordLogProbs = wordFreqs.toArray().let { f -> FloatArray(f.size) { logProb(f[it]) } },
                wordReadings = wordReadings.toTypedArray(),
                unknownLogProb = logProb(1),
            )
        }
    }
}

/** Growable IntArray without boxing. */
private class IntArrayBuilder {
    private var data = IntArray(1024)
    var size = 0
        private set

    fun add(value: Int) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = value
    }

    fun last(): Int = data[size - 1]

    fun sum(): Long {
        var total = 0L
        for (i in 0 until size) total += data[i]
        return total
    }

    fun toArray(): IntArray = data.copyOf(size)
}
