package com.rubify.pinyin

/**
 * Splits a run of hanzi into words by maximum probability under a unigram
 * model (the approach of jieba without its HMM): every split is scored by
 * the sum of its words' log probabilities and the best one wins.
 *
 * Needed for polyphonic characters (多音字): 我的确定 is 我|的|确定
 * (de), not 我|的确|定 (dí), which plain longest-match would pick.
 */
class WordSegmenter(private val dictionary: PinyinDictionary) {

    /**
     * Returns the length, in elements, of each word of [chars], in order.
     * Each element of [chars] is one character. The lengths sum to
     * `chars.size`.
     */
    fun segment(chars: List<String>): List<Int> {
        val n = chars.size
        val bestScore = DoubleArray(n + 1)
        val bestLength = IntArray(n)
        val candidate = StringBuilder()
        for (i in n - 1 downTo 0) {
            var score = dictionary.charLogProb(chars[i].codePointAt(0)) + bestScore[i + 1]
            var length = 1
            candidate.setLength(0)
            candidate.append(chars[i])
            for (len in 2..minOf(MAX_WORD_LENGTH, n - i)) {
                candidate.append(chars[i + len - 1])
                val word = candidate.toString()
                val logProb = dictionary.wordLogProb(word)
                if (logProb == null) {
                    if (!dictionary.hasWordWithPrefix(word)) break
                    continue
                }
                val total = logProb + bestScore[i + len]
                // >= prefers the longer word on a tie.
                if (total >= score) {
                    score = total
                    length = len
                }
            }
            bestScore[i] = score
            bestLength[i] = length
        }

        val lengths = ArrayList<Int>()
        var i = 0
        while (i < n) {
            lengths.add(bestLength[i])
            i += bestLength[i]
        }
        return lengths
    }

    companion object {
        /** Must match MAX_WORD_LENGTH in tools/pinyin-data/build_pinyin_assets.py. */
        const val MAX_WORD_LENGTH = 8
    }
}
