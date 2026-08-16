package com.salazarprime.tiro.accessibility

import com.salazarprime.tiro.accessibility.OverlayGestureMachine.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayGestureMachineTest {
    @Test
    fun holdBeginsImmediatelyAndReleaseFinishes() {
        val machine = OverlayGestureMachine(tapThresholdMillis = 250)

        assertEquals(listOf(Action.BEGIN_RECORDING), machine.press(atMillis = 1_000))
        assertEquals(listOf(Action.FINISH_RECORDING), machine.release(atMillis = 1_400))
        assertFalse(machine.isSustained)
    }

    @Test
    fun quickTapKeepsRecordingUntilNextTap() {
        val machine = OverlayGestureMachine(tapThresholdMillis = 250)

        assertEquals(listOf(Action.BEGIN_RECORDING), machine.press(atMillis = 1_000))
        assertEquals(listOf(Action.KEEP_RECORDING), machine.release(atMillis = 1_100))
        assertTrue(machine.isSustained)

        assertEquals(emptyList<Action>(), machine.press(atMillis = 2_000))
        assertEquals(listOf(Action.FINISH_RECORDING), machine.release(atMillis = 2_080))
        assertFalse(machine.isSustained)
    }

    @Test
    fun cancelledHoldFinishesButCancelledStopKeepsSustainedSession() {
        val hold = OverlayGestureMachine()
        hold.press(atMillis = 1_000)
        assertEquals(listOf(Action.FINISH_RECORDING), hold.cancel())

        val sustained = OverlayGestureMachine()
        sustained.press(atMillis = 1_000)
        sustained.release(atMillis = 1_050)
        sustained.press(atMillis = 1_500)
        assertEquals(emptyList<Action>(), sustained.cancel())
        assertTrue(sustained.isSustained)
    }
}

