package io.github.v_inn.rubify.overlay

import io.github.v_inn.rubify.ocr.OcrChar
import io.github.v_inn.rubify.ocr.OcrLine
import io.github.v_inn.rubify.ocr.OcrPage
import io.github.v_inn.rubify.ocr.PixelBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinyinLayoutTest {

    private val identity = ScreenMapping(1f, 1f, 0f, 0f)
    private val style = LabelStyle(minTextSize = 10f, maxTextSize = 40f, ascent = 1f, descent = 0.25f)

    /** Every character is 1 unit wide per character of text at size 1. */
    private val unitWidth: (String) -> Float = { it.length * 0.5f }

    private fun char(left: Int, top: Int, size: Int, pinyin: String) =
        AnnotatedChar(PixelBox(left, top, left + size, top + size), pinyin)

    private fun layout(vararg lines: AnnotatedLine, top: Float = 0f, bottom: Float = 10_000f) =
        PinyinLayout.layout(
            OverlayContent(1600, 2560, lines.toList()), identity, style, top, bottom, unitWidth,
        )

    @Test
    fun mappingScalesAndOffsets() {
        val mapping = ScreenMapping.create(800, 1280, 1600, 2560, viewLeft = 0, viewTop = 50)!!
        assertEquals(200f, mapping.x(100))
        assertEquals(150f, mapping.y(100))
    }

    @Test
    fun mappingRefusesARotatedDisplay() {
        assertNull(ScreenMapping.create(1600, 2560, 2560, 1600, 0, 0))
    }

    @Test
    fun labelSitsCenteredAboveItsCharacter() {
        val label = layout(AnnotatedLine(listOf(char(100, 500, 50, "nǐ")))).single()
        assertEquals(20f, label.textSize) // 0.4 of the 50 px character
        assertEquals(125f, label.centerX)
        assertTrue(label.background.bottom < 500f)
        assertTrue(label.baseline < label.background.bottom)
        assertTrue(label.background.top < label.baseline - label.textSize)
    }

    @Test
    fun crowdedLabelsShrinkUntilTheyFit() {
        // Centers 45 px apart; "zhuàng" is 3 units wide at size 1.
        val line = AnnotatedLine(listOf(char(0, 500, 50, "zhuàng"), char(45, 500, 50, "nǐ")))
        val labels = layout(line)
        assertEquals(0.92f * 45 / 3f, labels[0].textSize, 0.001f)
        assertTrue(labels.all { it.textSize == labels[0].textSize })
        // The texts themselves (without padding) don't overlap.
        val (first, second) = labels
        val firstTextRight = first.centerX + unitWidth(first.text) * first.textSize / 2
        val secondTextLeft = second.centerX - unitWidth(second.text) * second.textSize / 2
        assertTrue(firstTextRight <= secondTextLeft)
    }

    @Test
    fun textNeverShrinksBelowTheMinimum() {
        val line = AnnotatedLine(listOf(char(0, 500, 50, "zhuàng"), char(2, 500, 50, "zhuàng")))
        assertTrue(layout(line).all { it.textSize == style.minTextSize })
    }

    @Test
    fun sizeFollowsTheMedianCharacterHeight() {
        val line = AnnotatedLine(
            listOf(char(0, 500, 50, "a"), char(100, 500, 50, "a"), char(200, 400, 200, "a")),
        )
        assertTrue(layout(line).all { it.textSize == 20f })
    }

    @Test
    fun charactersInSystemBarsAreSkipped() {
        val statusBar = AnnotatedLine(listOf(char(100, 10, 30, "rì")))
        val body = AnnotatedLine(listOf(char(100, 500, 50, "nǐ")))
        val labels = layout(statusBar, body, top = 60f, bottom = 2400f)
        assertEquals(listOf("nǐ"), labels.map { it.text })
    }

    @Test
    fun contentKeepsOnlyAnnotatedHanzi() {
        val box = PixelBox(0, 0, 10, 10)
        val page = OcrPage(
            100, 200,
            listOf(
                OcrLine("B鸡", box, listOf(OcrChar("B", box, 1f), OcrChar("鸡", box, 1f))),
                OcrLine("3", box, listOf(OcrChar("3", box, 1f))),
            ),
        )
        val content = overlayContentOf(page, listOf(listOf(null, "jī"), listOf(null)))
        assertEquals(OverlayContent(100, 200, listOf(AnnotatedLine(listOf(AnnotatedChar(box, "jī"))))), content)
    }
}
