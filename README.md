<p align="center">
  <img src="docs/app-icon.svg" width="144" alt="Tiro mint fountain-pen nib and voice waveform icon">
</p>

<h1 align="center">Tiro for Android</h1>

<p align="center">Hold one floating button, speak, and put the transcript at your cursor without closing your keyboard.</p>

<p align="center">
  <img src="https://img.shields.io/badge/status-source_preview-2f6b5d" alt="Source preview">
  <img src="https://img.shields.io/badge/Android-12%2B-1f2937" alt="Android 12 or newer">
  <img src="https://img.shields.io/badge/transcription-on_device_only-1f2937" alt="On-device transcription only">
  <a href="https://github.com/Salazar-Prime/tiro-android/actions/workflows/android.yml"><img src="https://github.com/Salazar-Prime/tiro-android/actions/workflows/android.yml/badge.svg" alt="Android CI status"></a>
</p>

Tiro adds one floating voice button beside the active text field in Android apps. Your usual keyboard stays selected and visible. Hold the Tiro logo and release for press-to-talk, or quick-tap it to keep recording until the next tap. Tiro uses Android's explicit on-device speech recognizer and does not request Internet access.

This repository is an early source preview. There is no published APK or Google Play listing yet. You can try it by [building the debug APK](#build-from-source).

## Before you install

You need:

- Android 12 or newer.
- A device that provides Android's on-device speech-recognition service.
- An offline English language pack supplied by that service. British English is Tiro's default.
- **Microphone** permission.
- Tiro's **Accessibility Service** enabled after reviewing and accepting the in-app focused-field disclosure.

There is no API key, account, subscription, or transcription charge. On-device recognition and language-pack availability vary by device and system speech provider. Tiro reports an unavailable or missing language model instead of silently switching to online recognition.

The Accessibility Service is required to detect focused editable fields, show the Tiro button above other apps, and insert text at the cursor. Tiro declares itself as a non-accessibility-tool because its primary purpose is voice input, not disability support.

## Use Tiro

1. Open Tiro and allow **Microphone** access.
2. Read the **Focused-field access** disclosure, select the consent checkbox, and tap **Continue to Accessibility settings**.
3. Enable **Tiro floating voice control** in Android's Accessibility settings.
4. Under **System Permissions**, tap **Set language** to choose an English variant. Android's system speech component manages any required offline-model download.
5. Optionally adjust **Icon size** from 1–100%, **Opacity**, and the numeric **Release delay** on the main screen.
6. Keep your preferred keyboard selected and focus an editable, non-password text field in any app.
7. Use the single floating Tiro logo:

| Gesture | Result |
| --- | --- |
| Press and hold without moving | Recording begins after a brief gesture check; release adds a short audio tail, then transcribes and inserts |
| Quick tap | Start hands-free recording; tap the logo again to capture the short audio tail, then transcribe and insert |
| Drag before recording starts | Move the control without starting a new recording; Tiro remembers its position across apps |
| Drag while recording | Move the control while the microphone keeps listening; release a held recording normally |

The button is a non-focusable Android accessibility overlay. It waits briefly to distinguish a still hold from a drag. Moving past the drag threshold before voice capture begins selects relocation without starting the microphone. Once voice capture is active, dragging changes only the button position and recording continues. After a normal release or stop tap, Tiro keeps listening for the configured release delay so the system recognizer can receive the final word before transcription begins. The editable delay accepts 0–2,000 milliseconds and defaults to 450 milliseconds. The button uses Tiro's rounded-square app-icon shape rather than Android's device-dependent launcher mask. It does not replace, dismiss, or switch your keyboard. Its glass outline changes from teal to amber while the microphone is active. Android's speech service may still end a hands-free session after silence.

Tiro does not request Android's generic **Display over other apps** permission. The floating button uses `TYPE_ACCESSIBILITY_OVERLAY` and is available only while Tiro's Accessibility Service is enabled.

## Distribution

Tiro for Android is not currently distributed through Google Play, an alternative store, or a signed direct-download channel. CI and local builds produce a debug APK for development only. Do not treat that artifact as a production release.

## Build from source

Install JDK 17 and the Android SDK with Android API 36, then run from the repository root:

```sh
./gradlew test assembleDebug lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The first command is the verified build, unit-test, and lint path. The resulting debug APK is under `app/build/outputs/apk/debug/`.

## Privacy

- Focus detection begins after you grant in-app consent and enable **Tiro floating voice control**. The service looks for the currently focused editable node so it can decide whether to show the button. It ignores password fields.
- Microphone access begins after a still hold passes the brief gesture check or after a quick tap. Following a normal release or hands-free stop tap, Tiro keeps the microphone session active for the configured 0–2,000 millisecond release delay to capture the end of the final word. It stops sooner if Android ends the speech session, focus leaves the editable field, recognition fails, or the Accessibility Service stops.
- Tiro creates Android's on-device `SpeechRecognizer`. Microphone input is handled by that local system speech service and recognized text is returned to Tiro. Tiro does not create or retain audio files on success, failure, cancellation, or focus changes.
- Tiro has no `INTERNET` permission and never creates Android's normal potentially network-backed recognizer.
- When you choose an English variant with **Set language**, Android's system speech component—not Tiro—may contact its provider to download or update that offline language pack. The provider and its policies depend on the device.
- When inserting a transcript, Tiro temporarily reads the focused field's current text and selection so it can replace the selection without erasing surrounding text. It then uses Android accessibility text and selection actions. Tiro does not save or share the field text it reads.
- Tiro's enabled state, selected language, icon size, opacity, release delay, and normalized screen position remain in Tiro's private app preferences. Clear Tiro's app data or uninstall Tiro to remove these settings. Disabling Tiro in **Privacy** immediately pauses the floating control without changing Android's Accessibility Service permission.
- Up to 25 completed transcripts remain in Tiro's private app preferences. Delete individual entries, use **Clear history**, clear Tiro's app data, or uninstall Tiro to remove them. App backup and device-transfer rules exclude this history.
- No service credential or account is required or stored.
- Tiro does not send analytics, telemetry, crash reports, or application logs. Android and the installed system speech service may produce their own operational diagnostics.
- Tiro has no updater and makes no background update checks.
- Tiro does not read or write the clipboard, so insertion does not expose transcripts to clipboard-history tools.

## Current scope and limitations

- British, American, Australian, Canadian, and Indian English are selectable in this preview; British English is the default.
- On-device service and language support depend on the Android build and device vendor.
- Android controls speech endpoint detection, so recording can stop after a pause, including in hands-free mode.
- Tiro deliberately does not appear for password fields.
- Some apps, web fields, protected fields, or device policies do not expose editable text through Accessibility Service or may reject `ACTION_SET_TEXT`.
- The Accessibility Service can retrieve focused-window content. Review the in-app disclosure and the [Privacy](#privacy) section before enabling it.
- A Google Play release would require an Accessibility Service declaration, policy review, prominent disclosure and affirmative consent, a privacy policy, and accurate Data safety answers.
- There is no whisper.cpp fallback, OpenAI mode, cloud post-processing, or custom vocabulary prompt yet.

## Development

```sh
./gradlew test
./gradlew assembleDebug
./gradlew lintDebug
```

The app uses Kotlin and Android platform APIs: `AccessibilityService`, `AccessibilityNodeInfo`, `TYPE_ACCESSIBILITY_OVERLAY`, and the explicit on-device `SpeechRecognizer`. It does not currently include AndroidX, analytics, networking, advertising, or third-party runtime dependencies.

Release preparation is documented in [APP_PUBLISH.md](APP_PUBLISH.md).

## Acknowledgements

The Tiro wordmark uses Dancing Script under the [SIL Open Font License 1.1](docs/licenses/DancingScript-OFL.txt).

## License

No license has been selected yet. The repository is public, but public visibility alone does not grant reuse or redistribution rights.
