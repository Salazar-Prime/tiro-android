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
        VOICE_DRAGGING,
    }

    private var state = State.IDLE

    val isDragging: Boolean
        get() = state == State.DRAGGING || state == State.VOICE_DRAGGING

    fun down() {
        state = State.PENDING
    }

    fun holdTimeout(): List<Action> = when (state) {
        State.PENDING -> {
            state = State.VOICE
            listOf(Action.START_VOICE_GESTURE)
        }
        State.IDLE, State.VOICE, State.DRAGGING, State.VOICE_DRAGGING -> emptyList()
    }

    fun move(beyondDragThreshold: Boolean): List<Action> {
        if (!beyondDragThreshold) return emptyList()
        return when (state) {
            State.PENDING -> {
                state = State.DRAGGING
                listOf(Action.START_DRAG)
            }
            State.VOICE -> {
                state = State.VOICE_DRAGGING
                listOf(Action.START_DRAG)
            }
            State.IDLE, State.DRAGGING, State.VOICE_DRAGGING -> emptyList()
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
        State.VOICE_DRAGGING -> {
            state = State.IDLE
            listOf(Action.END_DRAG, Action.RELEASE_VOICE_GESTURE)
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
        State.VOICE_DRAGGING -> {
            state = State.IDLE
            listOf(Action.END_DRAG, Action.CANCEL_VOICE_GESTURE)
        }
        State.IDLE -> emptyList()
    }
}
