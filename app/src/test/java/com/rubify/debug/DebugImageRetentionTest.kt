package com.rubify.debug

import org.junit.Assert.assertEquals
import org.junit.Test

class DebugImageRetentionTest {

    @Test
    fun keepsAllImagesOfNewestCaptures() {
        val names = listOf(
            "20260916-120000-000-screenshot.png",
            "20260916-120000-000-ocr.jpg",
            "20260916-120002-000-screenshot.jpg",
            "20260916-120002-000-ocr.png",
            "20260916-120001-000-screenshot.png",
        )
        assertEquals(
            listOf("20260916-120000-000-screenshot.png", "20260916-120000-000-ocr.jpg"),
            debugImagesToPrune(names, keepCaptures = 2),
        )
    }

    @Test
    fun ignoresFilesOutsideTheNamingScheme() {
        val names = listOf(
            "notes.txt",
            "screenshot-20260916-120000-000.png",
            "20260916-120000-000-ocr.png.tmp",
            "20260916-120000-000-ocr.png",
        )
        assertEquals(emptyList<String>(), debugImagesToPrune(names, keepCaptures = 1))
    }

    @Test
    fun nothingToPruneUnderLimit() {
        assertEquals(
            emptyList<String>(),
            debugImagesToPrune(listOf("20260916-120000-000-screenshot.png"), keepCaptures = 5),
        )
    }
}
