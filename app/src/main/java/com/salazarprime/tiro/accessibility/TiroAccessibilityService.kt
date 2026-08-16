package com.salazarprime.tiro.accessibility

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import android.widget.Toast
import com.salazarprime.tiro.R
import com.salazarprime.tiro.history.TranscriptHistoryStore
import com.salazarprime.tiro.ime.TranscriptInsertion
import com.salazarprime.tiro.recognition.RecognitionRequest
import com.salazarprime.tiro.ui.dp

class TiroAccessibilityService : AccessibilityService(), RecognitionListener {
    private enum class RecognitionState {
        IDLE,
        PREPARING,
        LISTENING,
        TRANSCRIBING,
    }

    private lateinit var windowManager: WindowManager
    private lateinit var historyStore: TranscriptHistoryStore
    private val handler = Handler(Looper.getMainLooper())
    private val gestureMachine = OverlayGestureMachine()

    private var overlayButton: TiroOverlayIcon? = null
    private var overlayAttached = false
    private var targetWindowId = -1
    private var recognizer: SpeechRecognizer? = null
    private var recognitionState = RecognitionState.IDLE
    private var refreshRunnable: Runnable? = null
    private var resultResetRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        historyStore = TranscriptHistoryStore(this)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (!OverlayConsent.isGranted(this)) {
            toast("Open Tiro and review focused-field access before using the floating control.")
            return
        }
        scheduleOverlayRefresh()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!OverlayConsent.isGranted(this)) {
            hideOverlay()
            return
        }

        when (event?.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            -> scheduleOverlayRefresh()
            else -> Unit
        }
    }

    override fun onInterrupt() {
        hideOverlay()
    }

    override fun onDestroy() {
        refreshRunnable?.let(handler::removeCallbacks)
        resultResetRunnable?.let(handler::removeCallbacks)
        hideOverlay()
        super.onDestroy()
    }

    private fun scheduleOverlayRefresh() {
        refreshRunnable?.let(handler::removeCallbacks)
        refreshRunnable = Runnable {
            refreshRunnable = null
            val focused = findFocusedEditableNode()
            if (focused == null) {
                hideOverlay()
            } else {
                targetWindowId = focused.windowId
                showOverlay()
            }
        }.also { handler.postDelayed(it, 90) }
    }

    private fun findFocusedEditableNode(): AccessibilityNodeInfo? {
        val orderedWindows = runCatching {
            windows
                .filter { window -> window.isActive || window.isFocused }
                .sortedBy { window -> if (window.id == targetWindowId) 0 else 1 }
        }.getOrDefault(emptyList())

        orderedWindows.forEach { window ->
            val focused = window.root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused.isUsableTextTarget()) return focused
        }

        val activeFocus = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        return activeFocus?.takeIf { it.isUsableTextTarget() }
    }

    private fun AccessibilityNodeInfo?.isUsableTextTarget(): Boolean =
        this != null && isFocused && isEditable && isEnabled && !isPassword

    private fun showOverlay() {
        if (overlayAttached) return
        val button = overlayButton ?: TiroOverlayIcon(this).also { created ->
            created.onPress = { eventTime ->
                created.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                handleGestureActions(gestureMachine.press(eventTime))
            }
            created.onRelease = { eventTime ->
                handleGestureActions(gestureMachine.release(eventTime))
            }
            created.onCancel = {
                handleGestureActions(gestureMachine.cancel())
            }
            created.onAccessibilityClick = {
                if (recognitionState == RecognitionState.IDLE) {
                    handleGestureActions(gestureMachine.press(SystemClock.uptimeMillis()))
                    handleGestureActions(
                        gestureMachine.release(SystemClock.uptimeMillis() + 1),
                    )
                } else {
                    gestureMachine.reset()
                    finishRecognition()
                }
            }
            overlayButton = created
        }

        runCatching {
            windowManager.addView(button, overlayLayoutParams())
            overlayAttached = true
            updateOverlayVisual()
        }.onFailure {
            overlayAttached = false
            toast("Tiro could not show the floating control.")
        }
    }

    private fun hideOverlay() {
        cancelRecognition()
        if (!overlayAttached) return
        overlayButton?.let { button ->
            runCatching { windowManager.removeView(button) }
        }
        overlayAttached = false
        targetWindowId = -1
    }

    private fun overlayLayoutParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            dp(72),
            dp(72),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = dp(12)
            y = -dp(64)
            title = "Tiro voice control"
        }

    private fun handleGestureActions(actions: List<OverlayGestureMachine.Action>) {
        actions.forEach { action ->
            when (action) {
                OverlayGestureMachine.Action.BEGIN_RECORDING -> beginRecognition()
                OverlayGestureMachine.Action.KEEP_RECORDING -> updateOverlayVisual()
                OverlayGestureMachine.Action.FINISH_RECORDING -> finishRecognition()
            }
        }
    }

    private fun beginRecognition() {
        resultResetRunnable?.let(handler::removeCallbacks)
        resultResetRunnable = null

        if (findFocusedEditableNode() == null) {
            fail("Focus an editable text field.")
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            fail("Allow microphone access in Tiro.")
            return
        }
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            fail("On-device speech recognition is unavailable.")
            return
        }

        destroyRecognizer()
        recognitionState = RecognitionState.PREPARING
        updateOverlayVisual()
        runCatching {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(this).also { created ->
                recognizer = created
                created.setRecognitionListener(this)
                created.startListening(RecognitionRequest.create())
            }
        }.onFailure {
            fail("On-device speech recognition could not start.")
        }
    }

    private fun finishRecognition() {
        if (recognitionState != RecognitionState.PREPARING &&
            recognitionState != RecognitionState.LISTENING
        ) {
            return
        }
        recognitionState = RecognitionState.TRANSCRIBING
        updateOverlayVisual()
        recognizer?.stopListening()
    }

    private fun cancelRecognition() {
        recognizer?.cancel()
        destroyRecognizer()
        gestureMachine.reset()
        recognitionState = RecognitionState.IDLE
        overlayButton?.setLevel(0f)
        updateOverlayVisual()
    }

    private fun destroyRecognizer() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun completeRecognition(rawTranscript: String?) {
        val transcript = rawTranscript?.trim().orEmpty()
        destroyRecognizer()
        gestureMachine.reset()
        recognitionState = RecognitionState.IDLE
        overlayButton?.setLevel(0f)

        if (transcript.isEmpty()) {
            fail("No speech was recognized.")
            return
        }

        historyStore.add(transcript)
        if (insertIntoFocusedField(transcript)) {
            overlayButton?.showResult(success = true)
            scheduleIdleVisual()
        } else {
            fail("The transcript was saved, but this field rejected insertion.")
        }
    }

    private fun insertIntoFocusedField(transcript: String): Boolean {
        val node = findFocusedEditableNode() ?: return false
        val currentText = node.text?.toString().orEmpty()
        val start = node.textSelectionStart.takeIf { it >= 0 } ?: currentText.length
        val end = node.textSelectionEnd.takeIf { it >= 0 } ?: start
        val replacement = TranscriptInsertion.replaceSelection(
            rawTranscript = transcript,
            currentText = currentText,
            selectionStart = start,
            selectionEnd = end,
        ) ?: return false

        val textArguments = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                replacement.text,
            )
        }
        val inserted = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, textArguments)
        if (!inserted) return false

        val selectionArguments = Bundle().apply {
            putInt(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT,
                replacement.caret,
            )
            putInt(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT,
                replacement.caret,
            )
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionArguments)
        return true
    }

    private fun fail(message: String) {
        destroyRecognizer()
        gestureMachine.reset()
        recognitionState = RecognitionState.IDLE
        overlayButton?.setLevel(0f)
        overlayButton?.showResult(success = false)
        toast(message)
        scheduleIdleVisual()
    }

    private fun scheduleIdleVisual() {
        resultResetRunnable?.let(handler::removeCallbacks)
        resultResetRunnable = Runnable {
            resultResetRunnable = null
            updateOverlayVisual()
        }.also { handler.postDelayed(it, 1_400) }
    }

    private fun updateOverlayVisual() {
        val visualState = when (recognitionState) {
            RecognitionState.IDLE -> TiroOverlayIcon.State.IDLE
            RecognitionState.PREPARING,
            RecognitionState.LISTENING,
            -> TiroOverlayIcon.State.RECORDING
            RecognitionState.TRANSCRIBING -> TiroOverlayIcon.State.TRANSCRIBING
        }
        overlayButton?.setState(visualState, gestureMachine.isSustained)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onReadyForSpeech(params: Bundle?) {
        recognitionState = RecognitionState.LISTENING
        updateOverlayVisual()
    }

    override fun onBeginningOfSpeech() = Unit

    override fun onRmsChanged(rmsdB: Float) {
        overlayButton?.setLevel((rmsdB / 10f).coerceIn(0f, 1f))
    }

    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() {
        recognitionState = RecognitionState.TRANSCRIBING
        gestureMachine.reset()
        updateOverlayVisual()
    }

    override fun onError(error: Int) {
        val message = when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Allow microphone access in Tiro."
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "English recognition is not supported."
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Prepare the offline English language."
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech was recognized."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognition is busy. Try again."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech was heard."
            SpeechRecognizer.ERROR_AUDIO -> "The microphone is unavailable."
            else -> "Transcription stopped. Try again."
        }
        fail(message)
    }

    override fun onResults(results: Bundle?) {
        completeRecognition(
            results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull(),
        )
    }

    override fun onPartialResults(partialResults: Bundle?) = Unit

    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}

private class TiroOverlayIcon(context: Context) : ImageView(context) {
    enum class State {
        IDLE,
        RECORDING,
        TRANSCRIBING,
    }

    var onPress: (Long) -> Unit = {}
    var onRelease: (Long) -> Unit = {}
    var onCancel: () -> Unit = {}
    var onAccessibilityClick: () -> Unit = {}

    private var state = State.IDLE
    private var sustained = false

    init {
        setImageResource(R.mipmap.ic_launcher)
        scaleType = ScaleType.FIT_CENTER
        setPadding(context.dp(5), context.dp(5), context.dp(5), context.dp(5))
        elevation = context.dp(12).toFloat()
        isClickable = true
        isFocusable = true
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        setState(State.IDLE, false)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isPressed = true
                onPress(event.eventTime)
                return true
            }
            MotionEvent.ACTION_UP -> {
                isPressed = false
                onRelease(event.eventTime)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isPressed = false
                onCancel()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        onAccessibilityClick()
        return true
    }

    fun setState(value: State, isSustained: Boolean) {
        state = value
        sustained = isSustained
        val ring = when (state) {
            State.IDLE -> 0x9963D9CC.toInt()
            State.RECORDING -> 0xFFFF6B5E.toInt()
            State.TRANSCRIBING -> 0xFF70D5D1.toInt()
        }
        background = ringDrawable(
            ring,
            if (state == State.IDLE) context.dp(2) else context.dp(4),
        )
        contentDescription = context.getString(
            when (state) {
                State.IDLE -> R.string.overlay_idle_description
                State.RECORDING -> R.string.overlay_recording_description
                State.TRANSCRIBING -> R.string.overlay_transcribing_description
            },
        )
        alpha = if (state == State.TRANSCRIBING) 0.82f else 1f
        scaleX = if (state == State.RECORDING && sustained) 1.08f else 1f
        scaleY = scaleX
    }

    fun setLevel(level: Float) {
        if (state != State.RECORDING || sustained) return
        val scale = 1f + level.coerceIn(0f, 1f) * 0.08f
        scaleX = scale
        scaleY = scale
    }

    fun showResult(success: Boolean) {
        background = ringDrawable(
            color = if (success) 0xFF70D5D1.toInt() else 0xFFFF6B5E.toInt(),
            width = context.dp(5),
        )
        alpha = 1f
        scaleX = 1f
        scaleY = 1f
    }

    private fun ringDrawable(color: Int, width: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke(width, color)
        }
}
