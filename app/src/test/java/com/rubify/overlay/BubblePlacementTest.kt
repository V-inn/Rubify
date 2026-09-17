package com.rubify.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class BubblePlacementTest {

    @Test
    fun defaultSitsAtTheRightEdgeInsideTheMargin() {
        val (x, y) = BubblePlacement.DEFAULT.toPixels(1600, 2560, 112, 344, margin = 25)
        assertEquals(1600 - 112 - 25, x)
        assertEquals(25 + (0.4f * (2560 - 344 - 50)).toInt(), y)
    }

    @Test
    fun keepsItsRelativePlaceAcrossARotation() {
        val portrait = BubblePlacement.fromPixels(1463, 891, 1600, 2560, 112, 344, margin = 25)
        val (x, y) = portrait.toPixels(2560, 1600, 112, 344, margin = 25)
        assertEquals(2560 - 112 - 25, x)
        assertEquals(25 + (portrait.y * (1600 - 344 - 50)).toInt(), y, 1)
    }

    @Test
    fun positionsOutsideTheRangeAreClamped() {
        val placement = BubblePlacement.fromPixels(-50, 5000, 1600, 2560, 112, 344, margin = 25)
        assertEquals(BubblePlacement(0f, 1f), placement)
    }

    @Test
    fun screenSmallerThanTheBubbleStillGivesAValidPosition() {
        assertEquals(0 to 0, BubblePlacement(1f, 1f).toPixels(100, 100, 112, 344, margin = 25))
    }

    private fun assertEquals(expected: Int, actual: Int, delta: Int) =
        assertEquals(expected.toDouble(), actual.toDouble(), delta.toDouble())
}
