package com.rubify.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenshotRetentionTest {

    @Test
    fun keepsNewestAndPrunesOlder() {
        val names = listOf(
            "screenshot-20260916-120000-000.png",
            "screenshot-20260916-120002-000.png",
            "screenshot-20260916-120001-000.png",
        )
        assertEquals(
            listOf("screenshot-20260916-120000-000.png"),
            screenshotsToPrune(names, keep = 2),
        )
    }

    @Test
    fun ignoresUnrelatedFiles() {
        val names = listOf("notes.txt", "screenshot-20260916-120000-000.png", "screenshot-partial.tmp")
        assertEquals(emptyList<String>(), screenshotsToPrune(names, keep = 1))
    }

    @Test
    fun nothingToPruneUnderLimit() {
        assertEquals(
            emptyList<String>(),
            screenshotsToPrune(listOf("screenshot-20260916-120000-000.png"), keep = 5),
        )
    }
}
