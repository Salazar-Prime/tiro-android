package com.salazarprime.tiro.accessibility

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.PixelFormat
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
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import android.widget.Toast
import com.salazarprime.tiro.R
import com.salazarprime.tiro.history.TranscriptHistoryStore
import com.salazarprime.tiro.ime.TranscriptInsertion
import com.salazarprime.tiro.recognition.RecognitionRequest
import com.salazarprime.tiro.recognition.SpeechLanguages
import com.salazarprime.tiro.ui.dp
import com.salazarprime.tiro.ui.glassBackground
import com.salazarprime.tiro.ui.tiroPalette
import kotlin.math.hypot
import kotlin.math.roundToInt

class TiroAccessibilityService : AccessibilityService(), RecognitionListener {
    private enum class RecognitionState {
        IDLE,
        PREPARING,
        LISTENING,
        TRANSCRIBING,
    }

    private lateinit var windowManager: WindowManager
    private lateinit var historyStore: TranscriptHistoryStore
    private lateinit var overlaySettingsStore: OverlaySettingsStore
    private val handler = Handler(Looper.getMainLooper())
    private val gestureMachine = OverlayGestureMachine()
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        val settings = overlaySettingsStore.load()
        if (settings.appEnabled) {
            applyOverlaySettings(settings)
            if (!overlayAttached) scheduleOverlayRefresh()
        } else {
            hideOverlay()
        }
    }

    private var overlayButton: TiroOverlayIcon? = null
    private var overlayAttached = false
    private var dragOrigin = OverlayPlacement.Point(0, 0)
    private var targetWindowId = -1
    private var recognizer: SpeechRecognizer? = null
    private var recognitionState = RecognitionState.IDLE
    private var refreshRunnable: Runnable? = null
    private var resultResetRunnable: Runnable? = null
    private var finishRecognitionRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        historyStore = TranscriptHistoryStore(this)
        overlaySettingsStore = OverlaySettingsStore(this)
        overlaySettingsStore.registerListener(settingsListener)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (!OverlayConsent.isGranted(this)) {
            toast("Open Tiro and review focused-field access before using the floating control.")
            return
        }
        if (!overlaySettingsStore.load().appEnabled) return
        scheduleOverlayRefresh()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!OverlayConsent.isGranted(this) || !overlaySettingsStore.load().appEnabled) {
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
        clearPendingRecognitionFinish()
        overlaySettingsStore.unregisterListener(settingsListener)
        hideOverlay()
        super.onDestroy()
    }

    private fun scheduleOverlayRefresh() {
        refreshRunnable?.let(handler::removeCallbacks)
        refreshRunnable = Runnable {
            refreshRunnable = null
            if (!overlaySettingsStore.load().appEnabled) {
                hideOverlay()
                return@Runnable
            }
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
        if (!overlaySettingsStore.load().appEnabled) return
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
                cancelRecognition()
            }
            created.onDragStart = {
                if (gestureMachine.isSustained) {
                    handleGestureActions(gestureMachine.cancel())
                }
                val params = created.layoutParams as? WindowManager.LayoutParams
                dragOrigin = OverlayPlacement.Point(params?.x ?: 0, params?.y ?: 0)
            }
            created.onDragMove = { deltaX, deltaY ->
                moveOverlay(
                    x = dragOrigin.x + deltaX.roundToInt(),
                    y = dragOrigin.y + deltaY.roundToInt(),
                )
            }
            created.onDragEnd = {
                persistOverlayPosition()
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
            val settings = overlaySettingsStore.load()
            button.applyAppearance(
                sizePx = dp(settings.sizeDp),
                opacity = settings.opacityPercent / 100f,
            )
            windowManager.addView(button, overlayLayoutParams(settings))
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

    private fun overlayLayoutParams(
        settings: OverlaySettingsStore.Settings,
    ): WindowManager.LayoutParams {
        val size = dp(settings.sizeDp)
        val point = OverlayPlacement.fromFractions(
            area = overlayArea(),
            size = size,
            xFraction = settings.xFraction,
            yFraction = settings.yFraction,
        )
        return WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = point.x
            y = point.y
            title = "Tiro voice control"
        }
    }

    private fun overlayArea(): OverlayPlacement.Area {
        val metrics = windowManager.currentWindowMetrics
        val bounds = metrics.bounds
        val bars = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
        val margin = dp(10)
        return OverlayPlacement.Area(
            left = bounds.left + bars.left + margin,
            top = bounds.top + bars.top + margin,
            right = (bounds.right - bars.right - margin).coerceAtLeast(bounds.left + margin),
            bottom = (bounds.bottom - bars.bottom - margin).coerceAtLeast(bounds.top + margin),
        )
    }

    private fun applyOverlaySettings(
        settings: OverlaySettingsStore.Settings = overlaySettingsStore.load(),
    ) {
        val button = overlayButton ?: return
        val size = dp(settings.sizeDp)
        button.applyAppearance(sizePx = size, opacity = settings.opacityPercent / 100f)
        if (!overlayAttached) return

        val params = button.layoutParams as? WindowManager.LayoutParams ?: return
        val point = OverlayPlacement.fromFractions(
            area = overlayArea(),
            size = size,
            xFraction = settings.xFraction,
            yFraction = settings.yFraction,
        )
        params.width = size
        params.height = size
        params.x = point.x
        params.y = point.y
        runCatching { windowManager.updateViewLayout(button, params) }
    }

    private fun moveOverlay(x: Int, y: Int) {
        val button = overlayButton ?: return
        if (!overlayAttached) return
        val params = button.layoutParams as? WindowManager.LayoutParams ?: return
        val size = params.width.coerceAtLeast(1)
        val point = OverlayPlacement.clamp(overlayArea(), size, x, y)
        params.x = point.x
        params.y = point.y
        runCatching { windowManager.updateViewLayout(button, params) }
    }

    private fun persistOverlayPosition() {
        val button = overlayButton ?: return
        val params = button.layoutParams as? WindowManager.LayoutParams ?: return
        val fractions = OverlayPlacement.toFractions(
            area = overlayArea(),
            size = params.width.coerceAtLeast(1),
            point = OverlayPlacement.Point(params.x, params.y),
        )
        overlaySettingsStore.setPosition(fractions.x, fractions.y)
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

        if (!overlaySettingsStore.load().appEnabled) {
            hideOverlay()
            return
        }

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
                val language = SpeechLanguages.resolve(
                    overlaySettingsStore.load().languageTag,
                )
                created.startListening(
                    RecognitionRequest.create(language.tag),
                )
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
        if (finishRecognitionRunnable != null) return

        val releaseDelayMillis = overlaySettingsStore.load().releaseDelayMillis.toLong()
        finishRecognitionRunnable = Runnable {
            finishRecognitionRunnable = null
            if (recognitionState == RecognitionState.PREPARING ||
                recognitionState == RecognitionState.LISTENING
            ) {
                recognitionState = RecognitionState.TRANSCRIBING
                updateOverlayVisual()
                recognizer?.stopListening()
            }
        }.also { handler.postDelayed(it, releaseDelayMillis) }
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
        clearPendingRecognitionFinish()
        recognizer?.destroy()
        recognizer = null
    }

    private fun clearPendingRecognitionFinish() {
        finishRecognitionRunnable?.let(handler::removeCallbacks)
        finishRecognitionRunnable = null
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
        val replacement = TranscriptInsertion.replaceAccessibilityText(
            rawTranscript = transcript,
            exposedText = node.text?.toString().orEmpty(),
            hintText = node.hintText?.toString(),
            isShowingHintText = node.isShowingHintText,
            selectionStart = node.textSelectionStart,
            selectionEnd = node.textSelectionEnd,
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
        clearPendingRecognitionFinish()
        recognitionState = RecognitionState.TRANSCRIBING
        gestureMachine.reset()
        updateOverlayVisual()
    }

    override fun onError(error: Int) {
        val language = SpeechLanguages.resolve(overlaySettingsStore.load().languageTag).label
        val message = when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Allow microphone access in Tiro."
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "$language is not supported."
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Set up $language in Tiro."
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
    var onDragStart: () -> Unit = {}
    var onDragMove: (Float, Float) -> Unit = { _, _ -> }
    var onDragEnd: () -> Unit = {}
    var onAccessibilityClick: () -> Unit = {}

    private var state = State.IDLE
    private var sustained = false
    private var iconSizePx = context.dp(OverlaySettingsStore.DEFAULT_SIZE_DP)
    private var userOpacity = 1f
    private var resultColor: Int? = null
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchDownTime = 0L
    private val touchArbiter = OverlayTouchArbiter()
    private val dragThreshold = maxOf(
        ViewConfiguration.get(context).scaledTouchSlop * 2f,
        context.dp(14).toFloat(),
    )
    private val holdRunnable = Runnable {
        dispatchTouchActions(
            actions = touchArbiter.holdTimeout(),
            eventTime = SystemClock.uptimeMillis(),
        )
    }

    init {
        setImageResource(R.drawable.ic_tiro_overlay_foreground)
        scaleType = ScaleType.FIT_CENTER
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
                touchDownX = event.rawX
                touchDownY = event.rawY
                touchDownTime = event.eventTime
                touchArbiter.down()
                isPressed = true
                removeCallbacks(holdRunnable)
                postDelayed(
                    holdRunnable,
                    OverlayGestureMachine.DEFAULT_TAP_THRESHOLD_MILLIS,
                )
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.rawX - touchDownX
                val deltaY = event.rawY - touchDownY
                val actions = touchArbiter.move(
                    beyondDragThreshold = hypot(deltaX, deltaY) > dragThreshold,
                )
                if (OverlayTouchArbiter.Action.START_DRAG in actions) {
                    removeCallbacks(holdRunnable)
                }
                dispatchTouchActions(actions, event.eventTime)
                if (touchArbiter.isDragging) onDragMove(deltaX, deltaY)
                return true
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(holdRunnable)
                isPressed = false
                dispatchTouchActions(touchArbiter.up(), event.eventTime)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(holdRunnable)
                isPressed = false
                dispatchTouchActions(touchArbiter.cancel(), event.eventTime)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun dispatchTouchActions(
        actions: List<OverlayTouchArbiter.Action>,
        eventTime: Long,
    ) {
        actions.forEach { action ->
            when (action) {
                OverlayTouchArbiter.Action.START_VOICE_GESTURE -> onPress(touchDownTime)
                OverlayTouchArbiter.Action.RELEASE_VOICE_GESTURE -> onRelease(eventTime)
                OverlayTouchArbiter.Action.CANCEL_VOICE_GESTURE -> onCancel()
                OverlayTouchArbiter.Action.START_DRAG -> {
                    isPressed = false
                    onDragStart()
                }
                OverlayTouchArbiter.Action.END_DRAG -> onDragEnd()
            }
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(holdRunnable)
        super.onDetachedFromWindow()
    }

    override fun performClick(): Boolean {
        super.performClick()
        onAccessibilityClick()
        return true
    }

    fun setState(value: State, isSustained: Boolean) {
        state = value
        sustained = isSustained
        resultColor = null
        contentDescription = context.getString(
            when (state) {
                State.IDLE -> R.string.overlay_idle_description
                State.RECORDING -> R.string.overlay_recording_description
                State.TRANSCRIBING -> R.string.overlay_transcribing_description
            },
        )
        updateTile()
        scaleX = if (state == State.RECORDING && sustained) 1.08f else 1f
        scaleY = scaleX
    }

    fun applyAppearance(sizePx: Int, opacity: Float) {
        iconSizePx = sizePx.coerceAtLeast(1)
        userOpacity = opacity.coerceIn(0f, 1f)
        updateTile()
    }

    fun setLevel(level: Float) {
        if (state != State.RECORDING || sustained) return
        val scale = 1f + level.coerceIn(0f, 1f) * 0.08f
        scaleX = scale
        scaleY = scale
    }

    fun showResult(success: Boolean) {
        val palette = context.tiroPalette()
        resultColor = if (success) palette.aqua else palette.amber
        updateTile()
        scaleX = 1f
        scaleY = 1f
    }

    private fun updateTile() {
        val palette = context.tiroPalette()
        val strokeColor = resultColor ?: when (state) {
            State.IDLE -> palette.stroke
            State.RECORDING -> palette.amber
            State.TRANSCRIBING -> palette.aqua
        }
        background = context.glassBackground(
            radius = iconSizePx * 0.215f,
            strokeColor = strokeColor,
            strokeWidth = context.dp(if (state == State.IDLE && resultColor == null) 2 else 4),
        )
        alpha = userOpacity * if (state == State.TRANSCRIBING) 0.82f else 1f
    }
}
