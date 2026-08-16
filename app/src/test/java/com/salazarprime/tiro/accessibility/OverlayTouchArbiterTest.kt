package com.salazarprime.tiro.accessibility

import com.salazarprime.tiro.accessibility.OverlayTouchArbiter.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayTouchArbiterTest {
    @Test
    fun movementBeforeTheHoldTimeoutChoosesDragWithoutVoice() {
        val arbiter = OverlayTouchArbiter()

        arbiter.down()
        assertEquals(listOf(Action.START_DRAG), arbiter.move(beyondDragThreshold = true))
        assertTrue(arbiter.isDragging)
        assertEquals(emptyList<Action>(), arbiter.holdTimeout())
        assertEquals(listOf(Action.END_DRAG), arbiter.up())
    }

    @Test
    fun holdingStillStartsVoiceAndReleaseFinishesIt() {
        val arbiter = OverlayTouchArbiter()

        arbiter.down()
        assertEquals(listOf(Action.START_VOICE_GESTURE), arbiter.holdTimeout())
        assertEquals(listOf(Action.RELEASE_VOICE_GESTURE), arbiter.up())
        assertFalse(arbiter.isDragging)
    }

    @Test
    fun aQuickTapStillProducesTheCompleteVoiceGesture() {
        val arbiter = OverlayTouchArbiter()

        arbiter.down()
        assertEquals(
            listOf(Action.START_VOICE_GESTURE, Action.RELEASE_VOICE_GESTURE),
            arbiter.up(),
        )
    }

    @Test
    fun movingAfterVoiceStartsDragsWhileKeepingVoiceActiveUntilRelease() {
        val arbiter = OverlayTouchArbiter()

        arbiter.down()
        arbiter.holdTimeout()
        assertEquals(listOf(Action.START_DRAG), arbiter.move(beyondDragThreshold = true))
        assertTrue(arbiter.isDragging)
        assertEquals(
            listOf(Action.END_DRAG, Action.RELEASE_VOICE_GESTURE),
            arbiter.up(),
        )
    }

    @Test
    fun cancellingAnActiveVoiceDragEndsTheDragAndCancelsVoice() {
        val arbiter = OverlayTouchArbiter()

        arbiter.down()
        arbiter.holdTimeout()
        arbiter.move(beyondDragThreshold = true)

        assertEquals(
            listOf(Action.END_DRAG, Action.CANCEL_VOICE_GESTURE),
            arbiter.cancel(),
        )
    }
}
