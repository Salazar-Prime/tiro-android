package com.salazarprime.tiro.ime

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.salazarprime.tiro.R
import com.salazarprime.tiro.history.TranscriptHistoryStore
import com.salazarprime.tiro.recognition.RecognitionRequest
import com.salazarprime.tiro.ui.TiroPalette
import com.salazarprime.tiro.ui.dp
import com.salazarprime.tiro.ui.rippleBackground
import com.salazarprime.tiro.ui.roundedBackground
import com.salazarprime.tiro.ui.tiroPalette

class TiroInputMethodService : InputMethodService(), RecognitionListener {
    private enum class State {
        IDLE,
        PREPARING,
        LISTENING,
        TRANSCRIBING,
    }

    private lateinit var historyStore: TranscriptHistoryStore
    private lateinit var palette: TiroPalette
    private val handler = Handler(Looper.getMainLooper())

    private var recognizer: SpeechRecognizer? = null
    private var state = State.IDLE
    private var handsFree = false
    private var hasEditableTarget = false
    private var statusReset: Runnable? = null

    private var inputRoot: FrameLayout? = null
    private var overlayPanel: LinearLayout? = null
    private var statusView: TextView? = null
    private var modeView: TextView? = null
    private var micButton: TextView? = null
    private var lockButton: TextView? = null
    private var signalView: SignalRailView? = null

    override fun onCreate() {
        super.onCreate()
        historyStore = TranscriptHistoryStore(this)
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateInputView(): View {
        palette = tiroPalette()
        val root = FrameLayout(this).apply {
            setBackgroundColor(0x00000000)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(118),
            )
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        inputRoot = root

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(9), dp(9), dp(9))
            elevation = dp(10).toFloat()
            background = roundedBackground(
                fill = palette.surface,
                radius = dp(28).toFloat(),
                strokeColor = palette.stroke,
                strokeWidth = dp(1),
            )
        }
        overlayPanel = panel

        val signalAndStatus = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, dp(8), 0)
        }
        modeView = TextView(this).apply {
            text = getString(R.string.overlay_mode)
            setTextColor(palette.aqua)
            textSize = 8.5f
            typeface = Typeface.create("monospace", Typeface.BOLD)
            letterSpacing = 0.05f
            maxLines = 1
        }
        statusView = TextView(this).apply {
            text = getString(R.string.overlay_ready)
            setTextColor(palette.ink)
            textSize = 11f
            typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
            maxLines = 1
        }
        signalView = SignalRailView(this).apply {
            contentDescription = "Microphone level"
        }
        signalAndStatus.addView(modeView)
        signalAndStatus.addView(statusView)
        signalAndStatus.addView(
            signalView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8)).apply {
                topMargin = dp(3)
            },
        )
        panel.addView(
            signalAndStatus,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )

        panel.addView(
            controlButton("↺", "Insert the last transcript").apply {
                setOnClickListener { insertLastTranscript() }
            },
            controlLayout(40),
        )

        micButton = TextView(this).apply {
            text = getString(R.string.overlay_mic)
            contentDescription = "Hold to transcribe"
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 11f
            typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
            isClickable = true
            isFocusable = true
            background = rippleBackground(
                fill = palette.coral,
                radius = dp(24).toFloat(),
                ripple = 0x33FFFFFF,
            )
            // Accessibility click actions use hands-free mode because a hold
            // gesture is not available to every input method user.
            setOnClickListener { toggleHandsFree() }
            setOnTouchListener { view, event -> handleMicTouch(view, event) }
        }
        panel.addView(
            micButton,
            LinearLayout.LayoutParams(dp(52), dp(52)).apply {
                leftMargin = dp(5)
                rightMargin = dp(5)
            },
        )

        lockButton = controlButton("LOCK", "Start hands-free transcription").apply {
            textSize = 8f
            setOnClickListener { toggleHandsFree() }
        }
        panel.addView(lockButton, controlLayout(44))

        panel.addView(
            controlButton("⌨", "Switch keyboard").apply {
                setOnClickListener { switchToNextInputMethod(false) }
            },
            controlLayout(40),
        )

        root.addView(
            panel,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            ).apply {
                leftMargin = dp(8)
                rightMargin = dp(8)
                bottomMargin = dp(8)
            },
        )
        updateUi()
        return root
    }

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        val root = inputRoot ?: return
        val panel = overlayPanel ?: return
        if (root.height == 0 || panel.height == 0) return

        // The transparent input window does not resize the target app. Only the
        // visible signal capsule receives touch, so it behaves like an overlay.
        outInsets.contentTopInsets = root.height
        outInsets.visibleTopInsets = root.height
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
        outInsets.touchableRegion.set(panel.left, panel.top, panel.right, panel.bottom)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        hasEditableTarget = info != null && info.inputType != 0
        setStatus(if (hasEditableTarget) "Hold MIC to speak" else "Focus a text field")
    }

    override fun onFinishInput() {
        cancelRecognition()
        hasEditableTarget = false
        super.onFinishInput()
    }

    override fun onWindowHidden() {
        cancelRecognition()
        super.onWindowHidden()
    }

    override fun onDestroy() {
        cancelRecognition()
        super.onDestroy()
    }

    private fun handleMicTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                if (state == State.IDLE) {
                    handsFree = false
                    startRecognition()
                } else if (handsFree) {
                    finishRecognition()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!handsFree && (state == State.PREPARING || state == State.LISTENING)) {
                    finishRecognition()
                }
                return true
            }
        }
        return false
    }

    private fun toggleHandsFree() {
        lockButton?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (state == State.IDLE) {
            handsFree = true
            startRecognition()
        } else if (handsFree) {
            finishRecognition()
        } else {
            handsFree = true
            updateUi()
            setStatus("Hands-free · tap LOCK to stop")
        }
    }

    private fun startRecognition() {
        clearScheduledStatus()
        if (!hasEditableTarget || currentInputConnection == null) {
            fail("Focus a text field")
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            fail("Allow microphone in Tiro")
            return
        }
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            fail("On-device speech unavailable")
            return
        }

        destroyRecognizer()
        state = State.PREPARING
        updateUi()
        setStatus("Warming up")

        runCatching {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(this).also { created ->
                recognizer = created
                created.setRecognitionListener(this)
                created.startListening(RecognitionRequest.create())
            }
        }.onFailure {
            fail("On-device speech unavailable")
        }
    }

    private fun finishRecognition() {
        if (state != State.PREPARING && state != State.LISTENING) return
        state = State.TRANSCRIBING
        updateUi()
        setStatus("Transcribing")
        recognizer?.stopListening()
    }

    private fun cancelRecognition() {
        recognizer?.cancel()
        destroyRecognizer()
        handsFree = false
        state = State.IDLE
        signalView?.level = 0f
        updateUi()
    }

    private fun destroyRecognizer() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun insertLastTranscript() {
        val transcript = historyStore.latest()?.text
        if (transcript == null) {
            fail("Nothing saved yet")
            return
        }
        if (commitTranscript(transcript)) {
            succeed("Inserted again")
        } else {
            fail("Focus a text field")
        }
    }

    private fun commitTranscript(rawTranscript: String): Boolean {
        val connection = currentInputConnection ?: return false
        val textBeforeCursor = connection.getTextBeforeCursor(1, 0)
        val prepared = TranscriptInsertion.prepare(rawTranscript, textBeforeCursor)
        return prepared.isNotEmpty() && connection.commitText(prepared, 1)
    }

    private fun completeRecognition(rawTranscript: String?) {
        val transcript = rawTranscript?.trim().orEmpty()
        destroyRecognizer()
        handsFree = false
        state = State.IDLE
        signalView?.level = 0f
        updateUi()

        if (transcript.isEmpty()) {
            fail("No speech recognized")
            return
        }

        historyStore.add(transcript)
        if (commitTranscript(transcript)) {
            succeed("Inserted")
        } else {
            succeed("Saved · focus a field")
        }
    }

    private fun succeed(message: String) {
        setStatus(message)
        modeView?.setTextColor(palette.aqua)
        scheduleReadyStatus()
    }

    private fun fail(message: String) {
        destroyRecognizer()
        handsFree = false
        state = State.IDLE
        signalView?.level = 0f
        updateUi()
        modeView?.setTextColor(palette.coral)
        setStatus(message)
        scheduleReadyStatus()
    }

    private fun scheduleReadyStatus() {
        clearScheduledStatus()
        statusReset = Runnable {
            modeView?.setTextColor(palette.aqua)
            setStatus(if (hasEditableTarget) "Hold MIC to speak" else "Focus a text field")
            statusReset = null
        }.also { handler.postDelayed(it, 1_800) }
    }

    private fun clearScheduledStatus() {
        statusReset?.let(handler::removeCallbacks)
        statusReset = null
    }

    private fun setStatus(value: String) {
        statusView?.text = value
    }

    private fun updateUi() {
        val active = state != State.IDLE
        signalView?.isActive = active
        micButton?.apply {
            text = if (state == State.TRANSCRIBING) "•••" else if (handsFree) "STOP" else "MIC"
            contentDescription = when {
                handsFree -> "Stop hands-free transcription"
                active -> "Release to transcribe"
                else -> "Hold to transcribe"
            }
            background = rippleBackground(
                fill = if (active) palette.aqua else palette.coral,
                radius = dp(24).toFloat(),
                ripple = 0x33FFFFFF,
            )
            setTextColor(if (active) palette.deepGreen else 0xFFFFFFFF.toInt())
        }
        lockButton?.apply {
            text = if (handsFree) "STOP" else "LOCK"
            setTextColor(if (handsFree) palette.deepGreen else palette.ink)
            background = rippleBackground(
                fill = if (handsFree) palette.aqua else palette.field,
                radius = dp(18).toFloat(),
                ripple = palette.ink.withAlpha(0.10f),
            )
        }
    }

    private fun controlButton(text: String, description: String): TextView = TextView(this).apply {
        this.text = text
        contentDescription = description
        gravity = Gravity.CENTER
        setTextColor(palette.ink)
        textSize = 15f
        typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        isClickable = true
        isFocusable = true
        background = rippleBackground(
            fill = palette.field,
            radius = dp(18).toFloat(),
            ripple = palette.ink.withAlpha(0.10f),
        )
    }

    private fun controlLayout(width: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(dp(width), dp(40)).apply {
            leftMargin = dp(3)
        }

    private fun Int.withAlpha(alpha: Float): Int {
        val clamped = (alpha.coerceIn(0f, 1f) * 255).toInt()
        return (this and 0x00FFFFFF) or (clamped shl 24)
    }

    override fun onReadyForSpeech(params: Bundle?) {
        state = State.LISTENING
        updateUi()
        setStatus(if (handsFree) "Hands-free · speak" else "Listening · release to insert")
    }

    override fun onBeginningOfSpeech() {
        setStatus(if (handsFree) "Hands-free · speak" else "Listening · release to insert")
    }

    override fun onRmsChanged(rmsdB: Float) {
        signalView?.level = (rmsdB / 10f).coerceIn(0f, 1f)
    }

    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() {
        state = State.TRANSCRIBING
        updateUi()
        setStatus("Transcribing")
    }

    override fun onError(error: Int) {
        val message = when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Allow microphone in Tiro"
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "Language is not supported"
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Prepare the offline language"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer is busy · try again"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard"
            SpeechRecognizer.ERROR_AUDIO -> "Microphone unavailable"
            else -> "Transcription stopped · try again"
        }
        fail(message)
    }

    override fun onResults(results: Bundle?) {
        val transcript = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
        completeRecognition(transcript)
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val partial = partialResults
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
        if (!partial.isNullOrEmpty()) setStatus(partial)
    }

    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}

private class SignalRailView(
    context: android.content.Context,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val activeColor = context.getColor(com.salazarprime.tiro.R.color.tiro_coral)
    private val barBounds = RectF()
    private val weights = floatArrayOf(0.32f, 0.68f, 1f, 0.55f, 0.84f, 0.42f, 0.72f, 0.28f)

    var level: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var isActive: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.color = activeColor
        paint.alpha = if (isActive) 220 else 72
        val gap = width * 0.035f
        val barWidth = (width - gap * (weights.size - 1)) / weights.size
        val centerY = height / 2f
        weights.forEachIndexed { index, weight ->
            val energy = if (isActive) 0.28f + level * 0.72f else 0.22f
            val barHeight = (height * weight * energy).coerceAtLeast(height * 0.18f)
            val left = index * (barWidth + gap)
            barBounds.set(
                left,
                centerY - barHeight / 2f,
                left + barWidth,
                centerY + barHeight / 2f,
            )
            canvas.drawRoundRect(barBounds, barWidth / 2f, barWidth / 2f, paint)
        }
    }
}
