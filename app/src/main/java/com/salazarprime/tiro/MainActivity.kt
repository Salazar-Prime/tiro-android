package com.salazarprime.tiro

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.ModelDownloadListener
import android.speech.SpeechRecognizer
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.EditorInfo
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.salazarprime.tiro.accessibility.OverlayConsent
import com.salazarprime.tiro.accessibility.OverlaySettingsStore
import com.salazarprime.tiro.accessibility.TiroAccessibilityService
import com.salazarprime.tiro.history.TranscriptHistoryStore
import com.salazarprime.tiro.recognition.RecognitionRequest
import com.salazarprime.tiro.recognition.SpeechLanguage
import com.salazarprime.tiro.recognition.SpeechLanguages
import com.salazarprime.tiro.ui.TiroPalette
import com.salazarprime.tiro.ui.TiroWritingView
import com.salazarprime.tiro.ui.actionButton
import com.salazarprime.tiro.ui.dp
import com.salazarprime.tiro.ui.glassBackground
import com.salazarprime.tiro.ui.rippleBackground
import com.salazarprime.tiro.ui.roundedBackground
import com.salazarprime.tiro.ui.tiroPalette

class MainActivity : Activity() {
    private lateinit var historyStore: TranscriptHistoryStore
    private lateinit var overlaySettingsStore: OverlaySettingsStore
    private lateinit var palette: TiroPalette
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isFirstResume = true
    private var hasAnimatedWordmark = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        historyStore = TranscriptHistoryStore(this)
        overlaySettingsStore = OverlaySettingsStore(this)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (isFirstResume) {
            isFirstResume = false
        } else if (::historyStore.isInitialized) {
            render()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MICROPHONE_REQUEST) render()
    }

    private fun render() {
        palette = tiroPalette()
        window.statusBarColor = palette.canvas
        window.navigationBarColor = palette.canvas
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.canvas)
        }
        content.addView(hero())
        content.addView(spacer(30))
        content.addView(appearanceSection())
        content.addView(spacer(28))
        content.addView(historySection())
        content.addView(spacer(28))
        content.addView(accessSection())
        content.addView(spacer(32))
        content.addView(privacySection())
        content.addView(spacer(20))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(
                content,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(
                    dp(22) + bars.left,
                    dp(22) + bars.top,
                    dp(22) + bars.right,
                    dp(16) + bars.bottom,
                )
                insets
            }
        }
        setContentView(scroll)
        window.decorView.windowInsetsController?.setSystemBarsAppearance(
            0,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
        )
        scroll.requestApplyInsets()
    }

    private fun hero(): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER
        minimumHeight = dp(106)
        val animateWordmark = !hasAnimatedWordmark
        hasAnimatedWordmark = true
        addView(
            TiroWritingView(
                context = this@MainActivity,
                palette = palette,
                animateOnAttach = animateWordmark,
            ),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    private fun accessSection(): View {
        val microphoneGranted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        val consentGranted = OverlayConsent.isGranted(this)
        val recognizerAvailable = SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
        val selectedLanguage = SpeechLanguages.resolve(overlaySettingsStore.load().languageTag)

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(sectionTitle("System Permissions"))
            addView(spacer(12))
            addView(
                card().apply {
                    addView(
                        settingActionRow(
                            title = "Microphone",
                            status = if (microphoneGranted) "On" else "Off",
                            ready = microphoneGranted,
                            action = if (microphoneGranted) null else "Allow",
                        ) {
                            requestPermissions(
                                arrayOf(Manifest.permission.RECORD_AUDIO),
                                MICROPHONE_REQUEST,
                            )
                        },
                    )
                    addView(divider())
                    addView(
                        settingActionRow(
                            title = "Offline speech",
                            status = selectedLanguage.label,
                            ready = recognizerAvailable,
                            action = "Set language",
                        ) { showLanguagePicker() },
                    )
                    addView(divider())
                    if (consentGranted) {
                        addView(
                            settingActionRow(
                                title = "Accessibility",
                                status = if (accessibilityEnabled) "On" else "Off",
                                ready = accessibilityEnabled,
                                action = "Manage",
                            ) {
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            },
                        )
                    } else {
                        addView(
                            TextView(this@MainActivity).apply {
                                text = getString(R.string.accessibility_disclosure_title)
                                setTextColor(palette.ink)
                                textSize = 17f
                                typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
                            },
                        )
                        addView(spacer(9))
                        addView(
                            bodyText(
                                "Tiro uses Android Accessibility Service to detect focused " +
                                    "editable fields, show the floating voice button, and insert " +
                                    "transcripts at the cursor. It reads the focused field text " +
                                    "and selection only for insertion and does not store or share it.",
                                13f,
                            ),
                        )
                        addView(spacer(12))
                        val consent = CheckBox(this@MainActivity).apply {
                            text = getString(R.string.accessibility_consent)
                            setTextColor(palette.ink)
                            textSize = 13f
                            buttonTintList = ColorStateList.valueOf(palette.coral)
                            setPadding(0, dp(4), 0, dp(4))
                        }
                        addView(consent)
                        addView(spacer(10))
                        val enableButton = actionButton(
                            text = "Continue to Accessibility settings",
                            fill = palette.ink,
                            foreground = palette.canvas,
                        ).apply {
                            isEnabled = false
                            alpha = 0.42f
                            setOnClickListener {
                                if (!consent.isChecked) return@setOnClickListener
                                OverlayConsent.grant(this@MainActivity)
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }
                        }
                        consent.setOnCheckedChangeListener { _, checked ->
                            enableButton.isEnabled = checked
                            enableButton.alpha = if (checked) 1f else 0.42f
                        }
                        addView(enableButton, matchWidth())
                    }
                },
            )
        }
    }

    private fun appearanceSection(): View {
        val settings = overlaySettingsStore.load()
        var previewSizeDp = settings.sizeDp
        var previewOpacity = settings.opacityPercent / 100f

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                card().apply {
                    val preview = ImageView(this@MainActivity).apply {
                        setImageResource(R.drawable.ic_tiro_overlay_foreground)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        contentDescription = "Tiro floating control preview"
                    }

                    fun updatePreview() {
                        val size = dp(previewSizeDp)
                        preview.layoutParams = LinearLayout.LayoutParams(size, size)
                        preview.background = this@MainActivity.glassBackground(
                            radius = size * 0.215f,
                            strokeColor = palette.aqua,
                            strokeWidth = dp(2),
                        )
                        preview.alpha = previewOpacity
                    }

                    addView(
                        LinearLayout(this@MainActivity).apply {
                            gravity = Gravity.CENTER
                            minimumHeight = dp(132)
                            background = this@MainActivity.glassBackground(dp(18).toFloat())
                            addView(preview)
                        },
                        matchWidth(),
                    )
                    updatePreview()
                    addView(divider())
                    addView(
                        sliderSetting(
                            title = "Icon size",
                            initialValue = "${settings.sizePercent}%",
                            minimum = OverlaySettingsStore.MIN_SIZE_PERCENT,
                            maximum = OverlaySettingsStore.MAX_SIZE_PERCENT,
                            progress = settings.sizePercent,
                        ) { value ->
                            previewSizeDp = OverlaySettingsStore.sizePercentToDp(value)
                            overlaySettingsStore.setSizePercent(value)
                            updatePreview()
                            "$value%"
                        },
                    )
                    addView(divider())
                    addView(
                        sliderSetting(
                            title = "Opacity",
                            initialValue = "${settings.opacityPercent}%",
                            minimum = OverlaySettingsStore.MIN_OPACITY_PERCENT,
                            maximum = OverlaySettingsStore.MAX_OPACITY_PERCENT,
                            progress = settings.opacityPercent,
                        ) { value ->
                            previewOpacity = value / 100f
                            overlaySettingsStore.setOpacityPercent(value)
                            updatePreview()
                            "$value%"
                        },
                    )
                    addView(divider())
                    addView(releaseDelaySetting(settings.releaseDelayMillis))
                },
            )
        }
    }

    private fun sliderSetting(
        title: String,
        initialValue: String,
        minimum: Int,
        maximum: Int,
        progress: Int,
        onChange: (Int) -> String,
    ): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val valueLabel = TextView(this@MainActivity).apply {
            text = initialValue
            setTextColor(palette.aqua)
            textSize = 11f
            typeface = Typeface.MONOSPACE
        }
        addView(
            LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    TextView(this@MainActivity).apply {
                        text = title
                        setTextColor(palette.ink)
                        textSize = 14f
                        typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                )
                addView(valueLabel)
            },
            matchWidth(),
        )
        addView(spacer(5))
        addView(
            SeekBar(this@MainActivity).apply {
                min = minimum
                max = maximum
                this.progress = progress
                progressTintList = ColorStateList.valueOf(palette.aqua)
                progressBackgroundTintList = ColorStateList.valueOf(palette.stroke)
                thumbTintList = ColorStateList.valueOf(palette.coral)
                contentDescription = title
                setOnSeekBarChangeListener(
                    object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(
                            seekBar: SeekBar?,
                            value: Int,
                            fromUser: Boolean,
                        ) {
                            if (!fromUser) return
                            valueLabel.text = onChange(value)
                        }

                        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                    },
                )
            },
            matchWidth(),
        )
    }

    private fun releaseDelaySetting(initialValue: Int): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = dp(54)
                    addView(
                        TextView(this@MainActivity).apply {
                            text = getString(R.string.release_delay)
                            setTextColor(palette.ink)
                            textSize = 14f
                            typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
                        },
                        LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f,
                        ),
                    )

                    val valueField = EditText(this@MainActivity).apply {
                        setText(getString(R.string.integer_value, initialValue))
                        setTextColor(palette.ink)
                        textSize = 14f
                        gravity = Gravity.CENTER
                        inputType = InputType.TYPE_CLASS_NUMBER
                        imeOptions = EditorInfo.IME_ACTION_DONE
                        isSingleLine = true
                        setSelectAllOnFocus(true)
                        setPadding(dp(10), 0, dp(10), 0)
                        background = roundedBackground(
                            fill = palette.field,
                            radius = dp(12).toFloat(),
                            strokeColor = palette.stroke,
                            strokeWidth = dp(1),
                        )
                        contentDescription = "Release delay in milliseconds"
                    }

                    fun commitValue() {
                        val value = valueField.text.toString().toIntOrNull()
                            ?.coerceIn(
                                OverlaySettingsStore.MIN_RELEASE_DELAY_MILLIS,
                                OverlaySettingsStore.MAX_RELEASE_DELAY_MILLIS,
                            )
                            ?: OverlaySettingsStore.DEFAULT_RELEASE_DELAY_MILLIS
                        overlaySettingsStore.setReleaseDelayMillis(value)
                        val normalizedValue = getString(R.string.integer_value, value)
                        if (valueField.text.toString() != normalizedValue) {
                            valueField.setText(normalizedValue)
                            valueField.setSelection(valueField.text.length)
                        }
                    }

                    valueField.setOnFocusChangeListener { _, hasFocus ->
                        if (!hasFocus) commitValue()
                    }
                    valueField.setOnEditorActionListener { _, actionId, _ ->
                        if (actionId == EditorInfo.IME_ACTION_DONE) {
                            commitValue()
                            valueField.clearFocus()
                            true
                        } else {
                            false
                        }
                    }
                    addView(valueField, LinearLayout.LayoutParams(dp(86), dp(48)))
                    addView(
                        TextView(this@MainActivity).apply {
                            text = getString(R.string.milliseconds_abbreviation)
                            setTextColor(palette.ink.withAlpha(0.58f))
                            textSize = 12f
                            typeface = Typeface.MONOSPACE
                            setPadding(dp(8), 0, 0, 0)
                        },
                    )
                },
                matchWidth(),
            )
            addView(
                bodyText(
                    "Keeps listening after release so the last word is not cut off.",
                    12f,
                ).apply {
                    gravity = Gravity.CENTER
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                    setPadding(dp(10), dp(5), dp(10), 0)
                },
                matchWidth(),
            )
        }

    private fun historySection(): View {
        val transcripts = historyStore.all()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(sectionTitle("Voice History"))
            addView(spacer(12))
            addView(
                card().apply {
                    if (transcripts.isEmpty()) {
                        addView(
                            bodyText("Empty", 14f).apply {
                                gravity = Gravity.CENTER
                                textAlignment = View.TEXT_ALIGNMENT_CENTER
                            },
                            matchWidth(),
                        )
                    } else {
                        transcripts.take(5).forEachIndexed { index, transcript ->
                            val row = LinearLayout(this@MainActivity).apply {
                                orientation = LinearLayout.HORIZONTAL
                                gravity = Gravity.CENTER_VERTICAL
                                addView(
                                    bodyText(transcript.text, 14f).apply {
                                        maxLines = 3
                                    },
                                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                                )
                                addView(
                                    smallButton("Delete", "Delete transcript").apply {
                                        setOnClickListener {
                                            historyStore.delete(transcript.id)
                                            render()
                                        }
                                    },
                                )
                            }
                            addView(row)
                            if (index != minOf(4, transcripts.lastIndex)) addView(divider())
                        }
                        addView(spacer(12))
                        addView(historyClearControl(), matchWidth())
                    }
                },
            )
        }
    }

    private fun historyClearControl(): View {
        val interpolator = DecelerateInterpolator()
        val container = FrameLayout(this).apply {
            minimumHeight = dp(52)
            cameraDistance = dp(1_800).toFloat()
        }
        val clearButton = smallButton("Clear history", "Clear all transcript history").apply {
            setTextColor(palette.amber)
        }
        val confirmation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.INVISIBLE
            rotationX = -90f
            alpha = 0f
            setPadding(dp(14), dp(5), dp(5), dp(5))
            background = roundedBackground(
                fill = palette.field,
                radius = dp(14).toFloat(),
                strokeColor = palette.stroke,
                strokeWidth = dp(1),
            )
            addView(
                TextView(this@MainActivity).apply {
                    text = getString(R.string.are_you_sure)
                    setTextColor(palette.ink)
                    textSize = 13f
                    typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
        }

        fun flip(from: View, to: View) {
            from.animate().cancel()
            to.animate().cancel()
            from.animate()
                .rotationX(90f)
                .alpha(0f)
                .setDuration(130)
                .setInterpolator(interpolator)
                .withEndAction {
                    from.visibility = View.INVISIBLE
                    from.rotationX = 0f
                    to.rotationX = -90f
                    to.alpha = 0f
                    to.visibility = View.VISIBLE
                    to.animate()
                        .rotationX(0f)
                        .alpha(1f)
                        .setDuration(170)
                        .setInterpolator(interpolator)
                        .start()
                }
                .start()
        }

        val cancelButton = smallButton("Cancel", "Cancel clearing history").apply {
            setOnClickListener { flip(confirmation, clearButton) }
        }
        val confirmButton = smallButton("Clear", "Confirm clearing history").apply {
            setTextColor(palette.amber)
            setOnClickListener {
                historyStore.clear()
                render()
            }
        }
        confirmation.addView(
            cancelButton,
            LinearLayout.LayoutParams(dp(78), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                marginEnd = dp(5)
            },
        )
        confirmation.addView(
            confirmButton,
            LinearLayout.LayoutParams(dp(68), ViewGroup.LayoutParams.MATCH_PARENT),
        )
        clearButton.setOnClickListener { flip(clearButton, confirmation) }
        container.addView(
            clearButton,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        container.addView(
            confirmation,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        return container
    }

    private fun privacySection(): View {
        val appEnabled = overlaySettingsStore.load().appEnabled
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(sectionTitle("Privacy"))
            addView(spacer(12))
            addView(
                card().apply {
                    addView(
                        settingActionRow(
                            title = "Tiro",
                            status = if (appEnabled) "Enabled" else "Disabled",
                            ready = appEnabled,
                            action = if (appEnabled) "Disable" else "Enable",
                        ) {
                            overlaySettingsStore.setAppEnabled(!appEnabled)
                            render()
                        },
                    )
                    addView(divider())
                    addView(
                        bodyText(
                            "Android’s built-in speech recognition service is used.\n" +
                                "No data is sent to the app creator.",
                            14f,
                        ).apply {
                            gravity = Gravity.CENTER
                            textAlignment = View.TEXT_ALIGNMENT_CENTER
                        },
                        matchWidth(),
                    )
                },
            )
        }
    }

    private fun showLanguagePicker() {
        val settings = overlaySettingsStore.load()
        val options = SpeechLanguages.options
        val selectedIndex = options.indexOfFirst { language ->
            language.tag.equals(settings.languageTag, ignoreCase = true)
        }.coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Set language")
            .setSingleChoiceItems(
                options.map { language -> language.label }.toTypedArray(),
                selectedIndex,
            ) { dialog, index ->
                val language = options[index]
                overlaySettingsStore.setLanguageTag(language.tag)
                dialog.dismiss()
                render()
                requestLanguageModel(language)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun requestLanguageModel(language: SpeechLanguage) {
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            toast("This device does not provide on-device recognition.")
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            toast("${language.label} selected.")
            return
        }

        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
        val request = RecognitionRequest.create(language.tag)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            recognizer.triggerModelDownload(
                request,
                mainExecutor,
                object : ModelDownloadListener {
                    override fun onProgress(completedPercent: Int) {
                        toast("${language.label}: $completedPercent%")
                    }

                    override fun onSuccess() {
                        toast("${language.label} is ready.")
                        recognizer.destroy()
                    }

                    override fun onScheduled() {
                        toast("Android scheduled ${language.label}.")
                        recognizer.destroy()
                    }

                    override fun onError(error: Int) {
                        toast("Android could not prepare ${language.label}.")
                        recognizer.destroy()
                    }
                },
            )
        } else {
            recognizer.triggerModelDownload(request)
            toast("Android started preparing ${language.label}.")
            mainHandler.postDelayed({ recognizer.destroy() }, 1_500)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityEnabled = Settings.Secure.getInt(
            contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0,
        ) == 1
        if (!accessibilityEnabled) return false

        val component = ComponentName(this, TiroAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        return enabledServices.split(':').any { flattened ->
            ComponentName.unflattenFromString(flattened) == component
        }
    }

    private fun sectionTitle(value: String): TextView = TextView(this).apply {
        text = value
        setTextColor(palette.ink)
        textSize = 21f
        typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        letterSpacing = -0.018f
        gravity = Gravity.CENTER
        textAlignment = View.TEXT_ALIGNMENT_CENTER
    }

    private fun settingActionRow(
        title: String,
        status: String,
        ready: Boolean,
        action: String?,
        onAction: () -> Unit,
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(54)
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        TextView(this@MainActivity).apply {
                            text = title
                            setTextColor(palette.ink)
                            textSize = 15f
                            typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
                        },
                    )
                    addView(spacer(3))
                    addView(
                        TextView(this@MainActivity).apply {
                            text = status
                            setTextColor(if (ready) palette.aqua else palette.coral)
                            textSize = 10f
                            typeface = Typeface.MONOSPACE
                        },
                    )
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            if (action != null) {
                addView(
                    smallButton(action, action).apply {
                        setOnClickListener { onAction() }
                    },
                )
            }
        }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        elevation = dp(4).toFloat()
        background = this@MainActivity.glassBackground(dp(20).toFloat())
    }

    private fun bodyText(value: String, size: Float = 15f): TextView = TextView(this).apply {
        text = value
        setTextColor(palette.ink.withAlpha(0.72f))
        textSize = size
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        setLineSpacing(dp(3).toFloat(), 1f)
    }

    private fun divider(): View = View(this).apply {
        setBackgroundColor(palette.stroke)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
            setMargins(0, dp(12), 0, dp(12))
        }
    }

    private fun smallButton(text: String, description: String): Button = Button(this).apply {
        this.text = text
        contentDescription = description
        setTextColor(palette.ink.withAlpha(0.70f))
        textSize = 11f
        typeface = Typeface.DEFAULT_BOLD
        isAllCaps = false
        minHeight = dp(40)
        minimumHeight = dp(40)
        background = rippleBackground(
            fill = palette.field,
            radius = dp(12).toFloat(),
            ripple = palette.ink.withAlpha(0.10f),
        )
    }

    private fun spacer(height: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun matchWidth(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun Int.withAlpha(alpha: Float): Int {
        val clamped = (alpha.coerceIn(0f, 1f) * 255).toInt()
        return (this and 0x00FFFFFF) or (clamped shl 24)
    }

    private companion object {
        const val MICROPHONE_REQUEST = 1001
    }
}
