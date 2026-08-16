package com.salazarprime.tiro

import android.Manifest
import android.app.Activity
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
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
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
import com.salazarprime.tiro.ui.TiroPalette
import com.salazarprime.tiro.ui.actionButton
import com.salazarprime.tiro.ui.dp
import com.salazarprime.tiro.ui.label
import com.salazarprime.tiro.ui.rippleBackground
import com.salazarprime.tiro.ui.roundedBackground
import com.salazarprime.tiro.ui.tiroPalette

class MainActivity : Activity() {
    private lateinit var historyStore: TranscriptHistoryStore
    private lateinit var overlaySettingsStore: OverlaySettingsStore
    private lateinit var palette: TiroPalette
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        historyStore = TranscriptHistoryStore(this)
        overlaySettingsStore = OverlaySettingsStore(this)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::historyStore.isInitialized) render()
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
        content.addView(spacer(28))
        content.addView(setupSection())
        content.addView(spacer(24))
        content.addView(appearanceSection())
        content.addView(spacer(24))
        content.addView(testSection())
        content.addView(spacer(24))
        content.addView(historySection())
        content.addView(spacer(24))
        content.addView(privacySection())
        content.addView(spacer(28))

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
                    dp(20) + bars.left,
                    dp(24) + bars.top,
                    dp(20) + bars.right,
                    dp(16) + bars.bottom,
                )
                insets
            }
        }
        setContentView(scroll)
        val lightBars = resources.configuration.uiMode and 0x30 != 0x20
        window.decorView.windowInsetsController?.setSystemBarsAppearance(
            if (lightBars) {
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            } else {
                0
            },
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
        )
        scroll.requestApplyInsets()
    }

    private fun hero(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL

        addView(label("Tiro · Android", palette.aqua))
        addView(spacer(12))
        addView(
            TextView(this@MainActivity).apply {
                text = getString(R.string.hero_title)
                setTextColor(palette.ink)
                textSize = 38f
                typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
                setLineSpacing(0f, 0.96f)
            },
        )
        addView(spacer(12))
        addView(
            bodyText(
                "A floating voice control for active text fields. " +
                    "Recognition uses Android’s on-device engine only.",
                16f,
            ),
        )
        addView(spacer(18))
        addView(
            TextView(this@MainActivity).apply {
                text = if (SpeechRecognizer.isOnDeviceRecognitionAvailable(this@MainActivity)) {
                    "●  On-device recognition found"
                } else {
                    "●  On-device recognition unavailable"
                }
                setTextColor(
                    if (SpeechRecognizer.isOnDeviceRecognitionAvailable(this@MainActivity)) {
                        palette.aqua
                    } else {
                        palette.coral
                    },
                )
                textSize = 12f
                typeface = Typeface.create("monospace", Typeface.BOLD)
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = roundedBackground(
                    fill = palette.surface,
                    radius = dp(18).toFloat(),
                    strokeColor = palette.stroke,
                    strokeWidth = dp(1),
                )
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    private fun setupSection(): View {
        val microphoneGranted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        val consentGranted = OverlayConsent.isGranted(this)
        val recognizerAvailable = SpeechRecognizer.isOnDeviceRecognitionAvailable(this)

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label("Set up · three steps", palette.coral))
            addView(spacer(10))
            addView(
                card().apply {
                    addView(statusRow("1", "Microphone", microphoneGranted))
                    addView(divider())
                    addView(
                        statusRow(
                            "2",
                            "Floating control access",
                            accessibilityEnabled && consentGranted,
                        ),
                    )
                    addView(divider())
                    addView(statusRow("3", "On-device speech engine", recognizerAvailable))
                    addView(spacer(14))

                    if (!microphoneGranted) {
                        addView(
                            actionButton(
                                text = "Allow microphone",
                                fill = palette.coral,
                                foreground = 0xFFFFFFFF.toInt(),
                            ).apply {
                                setOnClickListener {
                                    requestPermissions(
                                        arrayOf(Manifest.permission.RECORD_AUDIO),
                                        MICROPHONE_REQUEST,
                                    )
                                }
                            },
                            matchWidth(),
                        )
                        addView(spacer(10))
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        addView(
                            actionButton(
                                text = "Prepare offline language",
                                fill = palette.field,
                                foreground = palette.ink,
                            ).apply {
                                isEnabled = SpeechRecognizer.isOnDeviceRecognitionAvailable(
                                    this@MainActivity,
                                )
                                alpha = if (isEnabled) 1f else 0.45f
                                setOnClickListener { requestLanguageModel() }
                            },
                            matchWidth(),
                        )
                    }
                },
            )
            addView(spacer(14))
            addView(accessibilityDisclosure(accessibilityEnabled, consentGranted))
        }
    }

    private fun accessibilityDisclosure(
        accessibilityEnabled: Boolean,
        consentGranted: Boolean,
    ): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(label("Required accessibility disclosure", palette.coral))
        addView(spacer(10))
        addView(
            card().apply {
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
                        "Tiro uses Android Accessibility Service to detect when an editable " +
                            "field is focused, display the Tiro voice button over other apps, " +
                            "and insert your transcript at that field’s cursor. During insertion, " +
                            "Tiro reads that focused field’s current text and selection so it can " +
                            "replace the selection without erasing surrounding text. It does not " +
                            "collect, store, or share the field text it reads.",
                        14f,
                    ),
                )
                addView(spacer(12))

                val consent = CheckBox(this@MainActivity).apply {
                    text = getString(R.string.accessibility_consent)
                    isChecked = consentGranted
                    setTextColor(palette.ink)
                    textSize = 13f
                    buttonTintList = android.content.res.ColorStateList.valueOf(palette.coral)
                    setPadding(0, dp(4), 0, dp(4))
                }
                addView(consent)
                addView(spacer(10))

                val enableButton = actionButton(
                    text = if (accessibilityEnabled) {
                        "Manage floating control"
                    } else {
                        "Continue to Accessibility settings"
                    },
                    fill = palette.ink,
                    foreground = palette.canvas,
                )
                fun updateButtonState() {
                    enableButton.isEnabled = consent.isChecked
                    enableButton.alpha = if (consent.isChecked) 1f else 0.42f
                }
                consent.setOnCheckedChangeListener { _, isChecked ->
                    if (!isChecked && consentGranted) {
                        OverlayConsent.revoke(this@MainActivity)
                    }
                    updateButtonState()
                }
                enableButton.setOnClickListener {
                    if (!consent.isChecked) return@setOnClickListener
                    OverlayConsent.grant(this@MainActivity)
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                updateButtonState()
                addView(enableButton, matchWidth())
            },
        )
    }

    private fun appearanceSection(): View {
        val settings = overlaySettingsStore.load()
        var previewSizeDp = settings.sizeDp
        var previewOpacity = settings.opacityPercent / 100f

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label("Floating control", palette.amber))
            addView(spacer(10))
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
                        preview.background = roundedBackground(
                            fill = palette.deepGreen,
                            radius = size * 0.215f,
                            strokeColor = palette.aqua,
                            strokeWidth = dp(2),
                        )
                        preview.alpha = previewOpacity
                    }

                    addView(
                        LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            minimumHeight = dp(102)
                            addView(preview)
                            addView(
                                LinearLayout(this@MainActivity).apply {
                                    orientation = LinearLayout.VERTICAL
                                    setPadding(dp(18), 0, 0, 0)
                                    addView(
                                        TextView(this@MainActivity).apply {
                                            text = getString(R.string.overlay_preview_title)
                                            setTextColor(palette.ink)
                                            textSize = 16f
                                            typeface = Typeface.create(
                                                "sans-serif-rounded",
                                                Typeface.BOLD,
                                            )
                                        },
                                    )
                                    addView(spacer(5))
                                    addView(
                                        bodyText(
                                            "Drag to move; a still hold records. Tiro remembers where you leave it.",
                                            13f,
                                        ),
                                    )
                                },
                                LinearLayout.LayoutParams(
                                    0,
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    1f,
                                ),
                            )
                        },
                        matchWidth(),
                    )
                    updatePreview()
                    addView(divider())
                    addView(
                        sliderSetting(
                            title = "Icon size",
                            initialValue = "${settings.sizeDp} dp",
                            minimum = OverlaySettingsStore.MIN_SIZE_DP,
                            maximum = OverlaySettingsStore.MAX_SIZE_DP,
                            progress = settings.sizeDp,
                        ) { value ->
                            previewSizeDp = value
                            overlaySettingsStore.setSizeDp(value)
                            updatePreview()
                            "$value dp"
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
                    addView(spacer(8))
                    addView(
                        smallButton("Reset position", "Reset floating control position").apply {
                            setOnClickListener {
                                overlaySettingsStore.resetPosition()
                                toast("Floating control position reset.")
                            }
                        },
                    )
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

    private fun testSection(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(label("Try the overlay", palette.aqua))
        addView(spacer(10))
        addView(
            card().apply {
                addView(
                    bodyText(
                        "Keep your usual keyboard selected and tap this field. The Tiro logo " +
                            "appears without closing the keyboard. Hold the logo and release to " +
                            "insert, quick-tap for hands-free recording, or drag to move it.",
                        14f,
                    ),
                )
                addView(spacer(14))
                addView(
                    EditText(this@MainActivity).apply {
                        hint = "Your transcript appears here"
                        setHintTextColor(palette.ink.withAlpha(0.38f))
                        setTextColor(palette.ink)
                        textSize = 16f
                        minHeight = dp(112)
                        gravity = Gravity.TOP or Gravity.START
                        setPadding(dp(14), dp(14), dp(14), dp(14))
                        background = roundedBackground(
                            fill = palette.field,
                            radius = dp(12).toFloat(),
                            strokeColor = palette.stroke,
                            strokeWidth = dp(1),
                        )
                    },
                    matchWidth(),
                )
            },
        )
    }

    private fun historySection(): View {
        val transcripts = historyStore.all()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label("Local history · ${transcripts.size}/25", palette.amber))
            addView(spacer(10))
            addView(
                card().apply {
                    if (transcripts.isEmpty()) {
                        addView(bodyText("Your finished transcripts will stay here until you clear them."))
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
                        addView(
                            smallButton("Clear history", "Clear all transcript history").apply {
                                setTextColor(palette.coral)
                                setOnClickListener {
                                    historyStore.clear()
                                    render()
                                }
                            },
                        )
                    }
                },
            )
        }
    }

    private fun privacySection(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(label("Privacy boundary", palette.aqua))
        addView(spacer(10))
        addView(
            card().apply {
                addView(
                    bodyText(
                        "Recording begins only when you press the Tiro logo. " +
                            "Tiro creates Android’s on-device recognizer and has no Internet permission. " +
                            "Its Accessibility Service watches for focused editable fields and reads " +
                            "the current field text and cursor only when inserting your transcript. " +
                            "It does not store or share that field text. Tiro writes no audio files " +
                            "and does not use analytics, crash reporting, an updater, or the clipboard.",
                        14f,
                    ),
                )
            },
        )
    }

    private fun requestLanguageModel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            toast("This device does not provide on-device recognition.")
            return
        }

        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
        val request = RecognitionRequest.create()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            recognizer.triggerModelDownload(
                request,
                mainExecutor,
                object : ModelDownloadListener {
                    override fun onProgress(completedPercent: Int) {
                        toast("Offline language: $completedPercent%")
                    }

                    override fun onSuccess() {
                        toast("Offline language is ready.")
                        recognizer.destroy()
                    }

                    override fun onScheduled() {
                        toast("Android scheduled the language download.")
                        recognizer.destroy()
                    }

                    override fun onError(error: Int) {
                        toast("Android could not prepare this language.")
                        recognizer.destroy()
                    }
                },
            )
        } else {
            recognizer.triggerModelDownload(request)
            toast("Android started the language-model request.")
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

    private fun statusRow(number: String, title: String, ready: Boolean): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
            addView(
                TextView(this@MainActivity).apply {
                    text = number
                    gravity = Gravity.CENTER
                    setTextColor(if (ready) palette.deepGreen else palette.ink)
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    background = roundedBackground(
                        fill = if (ready) palette.aqua else palette.field,
                        radius = dp(16).toFloat(),
                    )
                },
                LinearLayout.LayoutParams(dp(32), dp(32)),
            )
            addView(
                TextView(this@MainActivity).apply {
                    text = title
                    setTextColor(palette.ink)
                    textSize = 14f
                    typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
                    setPadding(dp(12), 0, 0, 0)
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(
                TextView(this@MainActivity).apply {
                    text = if (ready) "READY" else "TO DO"
                    setTextColor(if (ready) palette.aqua else palette.coral)
                    textSize = 10f
                    typeface = Typeface.MONOSPACE
                },
            )
        }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = roundedBackground(
            fill = palette.surface,
            radius = dp(20).toFloat(),
            strokeColor = palette.stroke,
            strokeWidth = dp(1),
        )
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
