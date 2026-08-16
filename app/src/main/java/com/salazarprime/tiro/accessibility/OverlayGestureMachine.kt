package com.salazarprime.tiro.accessibility

internal class OverlayGestureMachine(
    private val tapThresholdMillis: Long = 260,
) {
    enum class Action {
        BEGIN_RECORDING,
        KEEP_RECORDING,
        FINISH_RECORDING,
    }

    private sealed interface State {
        data object Idle : State
        data class PressingNew(val startedAtMillis: Long) : State
        data object Sustained : State
        data object PressingToStop : State
    }

    private var state: State = State.Idle

    val isSustained: Boolean
        get() = state == State.Sustained || state == State.PressingToStop

    fun press(atMillis: Long): List<Action> = when (state) {
        State.Idle -> {
            state = State.PressingNew(atMillis)
            listOf(Action.BEGIN_RECORDING)
        }
        State.Sustained -> {
            state = State.PressingToStop
            emptyList()
        }
        is State.PressingNew, State.PressingToStop -> emptyList()
    }

    fun release(atMillis: Long): List<Action> = when (val current = state) {
        is State.PressingNew -> {
            if (atMillis - current.startedAtMillis < tapThresholdMillis) {
                state = State.Sustained
                listOf(Action.KEEP_RECORDING)
            } else {
                state = State.Idle
                listOf(Action.FINISH_RECORDING)
            }
        }
        State.PressingToStop -> {
            state = State.Idle
            listOf(Action.FINISH_RECORDING)
        }
        State.Idle, State.Sustained -> emptyList()
    }

    fun cancel(): List<Action> = when (state) {
        is State.PressingNew -> {
            state = State.Idle
            listOf(Action.FINISH_RECORDING)
        }
        State.PressingToStop -> {
            state = State.Sustained
            emptyList()
        }
        State.Idle, State.Sustained -> emptyList()
    }

    fun reset() {
        state = State.Idle
    }
}

