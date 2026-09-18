package io.github.v_inn.rubify.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HanziTest {

    @Test
    fun acceptsSingleIdeographs() {
        listOf("你", "菜", "〇", "𠀀" /* U+20000, Extension B */, "豈" /* U+F900, compatibility */)
            .forEach { assertTrue(it, isHanzi(it)) }
    }

    @Test
    fun rejectsEverythingElse() {
        listOf("", "你好", "a", "A", "1", "，", "。", "“", "？", "ア", "한", "⺀" /* radical */, "々", " ")
            .forEach { assertFalse(it, isHanzi(it)) }
    }

    @Test
    fun countsOnlyHanziOnPage() {
        val box = PixelBox(0, 0, 10, 10)
        val line = OcrLine(
            text = "B 鸡肉饭",
            box = box,
            chars = listOf("B", "鸡", "肉", "饭").map { OcrChar(it, box, 1f) },
        )
        val page = OcrPage(100, 100, listOf(line, line.copy(chars = listOf(OcrChar("。", box, 1f)))))
        assertEquals(3, page.hanziCount)
    }

    @Test
    fun describesCharsWithBoxes() {
        val line = OcrLine(
            text = "你好",
            box = PixelBox(10, 20, 72, 52),
            chars = listOf(
                OcrChar("你", PixelBox(10, 20, 40, 52), 0.934f),
                OcrChar("好", PixelBox(42, 20, 72, 52), 0.875f),
            ),
        )
        assertEquals("你[10,20,40,52]0.93 好[42,20,72,52]0.88", line.describeChars())
    }
}
