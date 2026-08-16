package com.salazarprime.tiro.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TiroWritingTimelineTest {
    @Test
    fun writingFinishesBeforeTheSolidWordmarkAppears() {
        assertEquals(0f, TiroWritingTimeline.writingProgress(0f), 0.001f)
        assertEquals(1f, TiroWritingTimeline.writingProgress(0.84f), 0.001f)
        assertEquals(0f, TiroWritingTimeline.fillAlpha(0.72f), 0.001f)
        assertEquals(1f, TiroWritingTimeline.fillAlpha(0.94f), 0.001f)
    }

    @Test
    fun penHandsOffToTheTypingCaret() {
        assertEquals(1f, TiroWritingTimeline.penAlpha(0.84f), 0.001f)
        assertEquals(0f, TiroWritingTimeline.penAlpha(0.94f), 0.001f)
        assertFalse(TiroWritingTimeline.caretReady(0.93f))
        assertTrue(TiroWritingTimeline.caretReady(0.94f))
    }
}
