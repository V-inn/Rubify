package com.rubify.service

import com.rubify.ocr.PixelBox
import com.rubify.overlay.AnnotatedChar
import com.rubify.overlay.AnnotatedLine
import com.rubify.overlay.OverlayContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingSessionTest {

    private class FakeHost : ReadingSession.Host {
        val readings = mutableListOf<Pair<Int, Long>>()
        var pinyin: OverlayContent? = null
        var controls = false
        var toggleShowsPinyinVisible: Boolean? = null
        var scheduled: Pair<Long, () -> Unit>? = null

        override fun startReading(generation: Int, settleDelayMs: Long) {
            readings += generation to settleDelayMs
        }
        override fun showPinyin(content: OverlayContent) { pinyin = content }
        override fun hidePinyin() { pinyin = null }
        override fun showControls(pinyinVisible: Boolean) {
            controls = true
            toggleShowsPinyinVisible = pinyinVisible
        }
        override fun hideControls() { controls = false }
        override fun schedule(delayMs: Long, action: () -> Unit) { scheduled = delayMs to action }
        override fun cancelScheduled() { scheduled = null }

        fun runScheduled() {
            val action = scheduled!!.second
            scheduled = null
            action()
        }

        val lastGeneration get() = readings.last().first
    }

    private val host = FakeHost()
    private val session = ReadingSession(host)
    private val page = OverlayContent(
        100, 100, listOf(AnnotatedLine(listOf(AnnotatedChar(PixelBox(0, 0, 10, 10), "nǐ")))),
    )
    private val empty = OverlayContent(100, 100, emptyList())

    private fun startAndShow() {
        session.onButtonClicked()
        session.onReadingFinished(host.lastGeneration, page)
    }

    @Test
    fun tapReadsThenShowsPinyinAndControls() {
        session.onButtonClicked()
        assertTrue(session.isActive)
        assertEquals(ReadingSession.BUTTON_SETTLE_MS, host.readings.single().second)
        assertFalse(host.controls)

        session.onReadingFinished(host.lastGeneration, page)
        assertEquals(page, host.pinyin)
        assertTrue(host.controls)
    }

    @Test
    fun secondTapEndsTheSession() {
        startAndShow()
        session.onButtonClicked()
        assertFalse(session.isActive)
        assertNull(host.pinyin)
        assertFalse(host.controls)
        assertEquals(1, host.readings.size)
    }

    @Test
    fun closeEndsTheSession() {
        startAndShow()
        session.onCloseRequested()
        assertFalse(session.isActive)
        assertNull(host.pinyin)
        assertFalse(host.controls)
    }

    @Test
    fun refreshHidesEverythingWhileReadingAgain() {
        startAndShow()
        session.onRefreshRequested()
        assertNull(host.pinyin)
        assertFalse(host.controls)
        assertEquals(ReadingSession.REFRESH_SETTLE_MS, host.readings.last().second)

        session.onReadingFinished(host.lastGeneration, page)
        assertEquals(page, host.pinyin)
        assertTrue(host.controls)
    }

    @Test
    fun refreshIsIgnoredWhileReading() {
        startAndShow()
        session.onRefreshRequested()
        session.onRefreshRequested()
        assertEquals(2, host.readings.size)
    }

    @Test
    fun refreshWithNothingToAnnotateKeepsTheControls() {
        startAndShow()
        session.onRefreshRequested()
        session.onReadingFinished(host.lastGeneration, empty)
        assertTrue(session.isActive)
        assertNull(host.pinyin)
        assertTrue(host.controls)
    }

    @Test
    fun rotationHidesPinyinAndDropsTheReadingInProgress() {
        startAndShow()
        session.onRefreshRequested()
        val stale = host.lastGeneration
        session.onScreenMoved()
        session.onReadingFinished(stale, page)
        assertNull(host.pinyin)
        assertTrue(host.controls)
    }

    @Test
    fun resultAfterTheSessionEndedIsIgnored() {
        session.onButtonClicked()
        val generation = host.lastGeneration
        session.onButtonClicked()
        session.onReadingFinished(generation, page)
        assertNull(host.pinyin)
        assertFalse(host.controls)
    }

    @Test
    fun nothingToAnnotateOnFirstReadEndsTheSession() {
        session.onButtonClicked()
        session.onReadingFinished(host.lastGeneration, empty)
        assertFalse(session.isActive)
        assertFalse(host.controls)
    }

    @Test
    fun failedReadingsAreRetriedAFewTimes() {
        session.onButtonClicked()
        repeat(ReadingSession.MAX_RETRIES) {
            session.onReadingFailed(host.lastGeneration)
            assertEquals(ReadingSession.RETRY_DELAY_MS, host.scheduled!!.first)
            host.runScheduled()
        }
        session.onReadingFailed(host.lastGeneration)
        assertFalse(session.isActive)
        assertEquals(1 + ReadingSession.MAX_RETRIES, host.readings.size)
    }

    @Test
    fun failedRefreshKeepsTheControlsAfterRetries() {
        startAndShow()
        session.onRefreshRequested()
        repeat(ReadingSession.MAX_RETRIES) {
            session.onReadingFailed(host.lastGeneration)
            host.runScheduled()
        }
        session.onReadingFailed(host.lastGeneration)
        assertTrue(session.isActive)
        assertTrue(host.controls)
    }

    @Test
    fun retryIsDroppedIfTheSessionEnded() {
        session.onButtonClicked()
        session.onReadingFailed(host.lastGeneration)
        val retry = host.scheduled!!.second
        session.onButtonClicked()
        retry()
        assertEquals(1, host.readings.size)
    }

    @Test
    fun toggleHidesAndShowsTheSamePinyinWithoutReading() {
        startAndShow()
        assertEquals(true, host.toggleShowsPinyinVisible)

        session.onTogglePinyinRequested()
        assertNull(host.pinyin)
        assertTrue(host.controls)
        assertEquals(false, host.toggleShowsPinyinVisible)

        session.onTogglePinyinRequested()
        assertEquals(page, host.pinyin)
        assertEquals(true, host.toggleShowsPinyinVisible)
        assertEquals(1, host.readings.size)
    }

    @Test
    fun toggleAfterRotationReadsAgain() {
        startAndShow()
        session.onScreenMoved()
        assertEquals(false, host.toggleShowsPinyinVisible)
        session.onTogglePinyinRequested()
        assertEquals(2, host.readings.size)
        assertFalse(host.controls)
    }

    @Test
    fun refreshWhileHiddenShowsTheNewPinyin() {
        startAndShow()
        session.onTogglePinyinRequested()
        session.onRefreshRequested()
        session.onReadingFinished(host.lastGeneration, page)
        assertEquals(page, host.pinyin)
        assertEquals(true, host.toggleShowsPinyinVisible)
    }

    @Test
    fun controlsAreIgnoredWhileInactive() {
        session.onTogglePinyinRequested()
        session.onRefreshRequested()
        session.onCloseRequested()
        session.onScreenMoved()
        assertTrue(host.readings.isEmpty())
        assertFalse(host.controls)
    }
}
