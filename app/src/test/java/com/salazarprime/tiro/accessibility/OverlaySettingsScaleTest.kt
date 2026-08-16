package com.salazarprime.tiro.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlaySettingsScaleTest {
    @Test
    fun percentageScalePreservesPracticalTouchSizes() {
        assertEquals(48, OverlaySettingsStore.sizePercentToDp(1))
        assertEquals(72, OverlaySettingsStore.sizePercentToDp(50))
        assertEquals(96, OverlaySettingsStore.sizePercentToDp(100))
    }

    @Test
    fun existingDpValuesMigrateToReadablePercentages() {
        assertEquals(1, OverlaySettingsStore.sizeDpToPercent(48))
        assertEquals(50, OverlaySettingsStore.sizeDpToPercent(72))
        assertEquals(100, OverlaySettingsStore.sizeDpToPercent(96))
    }
}
