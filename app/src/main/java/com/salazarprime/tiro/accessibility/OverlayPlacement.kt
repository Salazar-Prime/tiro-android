package com.salazarprime.tiro.accessibility

import kotlin.math.roundToInt

internal object OverlayPlacement {
    data class Area(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )

    data class Point(val x: Int, val y: Int)

    data class Fractions(val x: Float, val y: Float)

    fun fromFractions(
        area: Area,
        size: Int,
        xFraction: Float,
        yFraction: Float,
    ): Point {
        val maxX = (area.right - size).coerceAtLeast(area.left)
        val maxY = (area.bottom - size).coerceAtLeast(area.top)
        return Point(
            x = area.left +
                ((maxX - area.left) * xFraction.coerceIn(0f, 1f)).roundToInt(),
            y = area.top +
                ((maxY - area.top) * yFraction.coerceIn(0f, 1f)).roundToInt(),
        )
    }

    fun clamp(area: Area, size: Int, x: Int, y: Int): Point {
        val maxX = (area.right - size).coerceAtLeast(area.left)
        val maxY = (area.bottom - size).coerceAtLeast(area.top)
        return Point(
            x = x.coerceIn(area.left, maxX),
            y = y.coerceIn(area.top, maxY),
        )
    }

    fun toFractions(area: Area, size: Int, point: Point): Fractions {
        val clamped = clamp(area, size, point.x, point.y)
        val horizontalRange = (area.right - size - area.left).coerceAtLeast(0)
        val verticalRange = (area.bottom - size - area.top).coerceAtLeast(0)
        return Fractions(
            x = if (horizontalRange == 0) {
                0f
            } else {
                (clamped.x - area.left).toFloat() / horizontalRange
            },
            y = if (verticalRange == 0) {
                0f
            } else {
                (clamped.y - area.top).toFloat() / verticalRange
            },
        )
    }
}
