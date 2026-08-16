package com.salazarprime.tiro.accessibility

internal class OverlayTouchArbiter {
    enum class Action {
        START_VOICE_GESTURE,
        RELEASE_VOICE_GESTURE,
        CANCEL_VOICE_GESTURE,
        START_DRAG,
        END_DRAG,
    }

    private enum class State {
        IDLE,
        PENDING,
        VOICE,
        DRAGGING,
    }

    private var state = State.IDLE

    val isDragging: Boolean
        get() = state == State.DRAGGING

    fun down() {
        state = State.PENDING
    }

    fun holdTimeout(): List<Action> = when (state) {
        State.PENDING -> {
            state = State.VOICE
            listOf(Action.START_VOICE_GESTURE)
        }
        State.IDLE, State.VOICE, State.DRAGGING -> emptyList()
    }

    fun move(beyondDragThreshold: Boolean): List<Action> {
        if (!beyondDragThreshold) return emptyList()
        return when (state) {
            State.PENDING, State.VOICE -> {
                state = State.DRAGGING
                listOf(Action.START_DRAG)
            }
            State.IDLE, State.DRAGGING -> emptyList()
        }
    }

    fun up(): List<Action> = when (state) {
        State.PENDING -> {
            state = State.IDLE
            listOf(Action.START_VOICE_GESTURE, Action.RELEASE_VOICE_GESTURE)
        }
        State.VOICE -> {
            state = State.IDLE
            listOf(Action.RELEASE_VOICE_GESTURE)
        }
        State.DRAGGING -> {
            state = State.IDLE
            listOf(Action.END_DRAG)
        }
        State.IDLE -> emptyList()
    }

    fun cancel(): List<Action> = when (state) {
        State.PENDING -> {
            state = State.IDLE
            emptyList()
        }
        State.VOICE -> {
            state = State.IDLE
            listOf(Action.CANCEL_VOICE_GESTURE)
        }
        State.DRAGGING -> {
            state = State.IDLE
            listOf(Action.END_DRAG)
        }
        State.IDLE -> emptyList()
    }
}
