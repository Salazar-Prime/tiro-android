package com.salazarprime.tiro.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayPlacementTest {
    private val area = OverlayPlacement.Area(left = 12, top = 36, right = 1080, bottom = 2280)

    @Test
    fun resolvesRememberedFractionsInsideTheAvailableScreen() {
        assertEquals(
            OverlayPlacement.Point(x = 1008, y = 1122),
            OverlayPlacement.fromFractions(
                area = area,
                size = 72,
                xFraction = 1f,
                yFraction = 0.5f,
            ),
        )
    }

    @Test
    fun clampsDraggingToEveryScreenEdge() {
        assertEquals(
            OverlayPlacement.Point(x = 12, y = 2208),
            OverlayPlacement.clamp(area, size = 72, x = -500, y = 9_000),
        )
    }

    @Test
    fun persistedFractionsRoundTripToTheDraggedPoint() {
        val point = OverlayPlacement.Point(x = 540, y = 900)
        val fractions = OverlayPlacement.toFractions(area, size = 72, point = point)

        assertEquals(
            point,
            OverlayPlacement.fromFractions(area, size = 72, fractions.x, fractions.y),
        )
    }
}
