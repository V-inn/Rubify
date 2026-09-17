package com.rubify.overlay

import kotlin.math.roundToInt

/**
 * Where the control bubble sits, stored as fractions of the free space
 * (0 = left/top, 1 = right/bottom) so it keeps its relative place across
 * rotations and screen sizes. Pure Kotlin.
 */
data class BubblePlacement(val x: Float, val y: Float) {

    /** Top-left pixel position for a [width] x [height] bubble on a screen of the given size. */
    fun toPixels(screenWidth: Int, screenHeight: Int, width: Int, height: Int, margin: Int): Pair<Int, Int> =
        axisToPixels(x, screenWidth, width, margin) to axisToPixels(y, screenHeight, height, margin)

    companion object {
        /** Right edge, a bit above the middle: clear of most reading content. */
        val DEFAULT = BubblePlacement(1f, 0.4f)

        fun fromPixels(
            left: Int,
            top: Int,
            screenWidth: Int,
            screenHeight: Int,
            width: Int,
            height: Int,
            margin: Int,
        ) = BubblePlacement(
            axisToFraction(left, screenWidth, width, margin),
            axisToFraction(top, screenHeight, height, margin),
        )

        /** Pixel range the bubble's top-left may take on one axis. */
        fun range(screen: Int, size: Int, margin: Int): IntRange {
            val low = minOf(margin, maxOf(0, screen - size))
            return low..maxOf(low, screen - size - margin)
        }

        private fun axisToPixels(fraction: Float, screen: Int, size: Int, margin: Int): Int {
            val range = range(screen, size, margin)
            return (range.first + fraction.coerceIn(0f, 1f) * (range.last - range.first)).roundToInt()
        }

        private fun axisToFraction(position: Int, screen: Int, size: Int, margin: Int): Float {
            val range = range(screen, size, margin)
            if (range.last == range.first) return 0f
            return ((position - range.first).toFloat() / (range.last - range.first)).coerceIn(0f, 1f)
        }
    }
}
