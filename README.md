<p align="center">
  <img src="docs/app-icon.svg" width="144" alt="Tiro mint fountain-pen nib and voice waveform icon">
</p>

<h1 align="center">Tiro for Android</h1>

<p align="center">Hold the floating control, speak, and put the transcript at your cursor.</p>

<p align="center">
  <img src="https://img.shields.io/badge/status-source_preview-2f6b5d" alt="Source preview">
  <img src="https://img.shields.io/badge/Android-12%2B-1f2937" alt="Android 12 or newer">
  <img src="https://img.shields.io/badge/transcription-on_device_only-1f2937" alt="On-device transcription only">
  <a href="https://github.com/Salazar-Prime/tiro-android/actions/workflows/android.yml"><img src="https://github.com/Salazar-Prime/tiro-android/actions/workflows/android.yml/badge.svg" alt="Android CI status"></a>
</p>

Tiro is a compact voice keyboard for Android. When Tiro is the selected input method, an overlay control appears whenever a text field is active. Hold **MIC**, speak, and release to insert the result. Transcription uses Android's explicit on-device speech recognizer; Tiro does not create a network recognizer or request Internet access.

This repository is an early source preview. There is no published APK or Google Play listing yet. You can try it by [building the debug APK](#build-from-source).

## Before you install

You need:

- Android 12 or newer.
- A device that provides Android's on-device speech-recognition service.
- The English (US) offline language pack supplied by that service.
- Microphone permission.
- Tiro enabled and selected as the current keyboard.

There is no API key, account, subscription, or transcription charge. On-device recognition and language-pack availability vary by device and system speech provider. Tiro reports an unavailable or missing language model instead of silently switching to online recognition.

## Use Tiro

1. Open Tiro and allow **Microphone** access.
2. Tap **Enable Tiro keyboard** and enable **Tiro voice keyboard** in Android Settings.
3. Tap **Choose keyboard** and select Tiro.
4. If requested, tap **Prepare offline language**. Android's system speech component manages that download.
5. Focus a text field in any app.
6. Hold **MIC**, speak, and release to transcribe and insert.

| Overlay control | Result |
| --- | --- |
| Hold **MIC** | Record; release to transcribe and insert |
| **LOCK** | Start hands-free recording; tap **STOP** to finish |
| **↺** | Insert the last saved transcript again |
| **⌨** | Switch to the next enabled keyboard |

The overlay is part of Android's input-method window. It appears only for editable fields while Tiro is selected. It does not request Android's display-over-other-apps or Accessibility permissions.

## Distribution

Tiro for Android is not currently distributed through Google Play, an alternative store, or a signed direct-download channel. CI and local builds produce a debug APK for development only. Do not treat that artifact as a production release.

## Build from source

Install JDK 17 and the Android SDK with Android API 36, then run from the repository root:

```sh
./gradlew test assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The first command is the verified clean build-and-test path. The resulting debug APK is under `app/build/outputs/apk/debug/`.

## Privacy

- Microphone access begins only when you hold **MIC** or start hands-free mode. It stops when you release, tap **STOP**, Android ends the speech session, the input window closes, or recognition fails.
- Tiro creates Android's on-device `SpeechRecognizer`. Microphone input is handled by that local system speech service and the recognized text is returned to Tiro.
- Tiro has no `INTERNET` permission and never creates Android's normal potentially network-backed recognizer.
- If you tap **Prepare offline language**, Android's system speech component—not Tiro—may contact its provider to download or update the English language pack. The provider and its policies depend on the device.
- No service credential or account is required or stored.
- Tiro does not create or retain audio files, including on success, failure, cancellation, or when switching fields.
- Up to 25 completed transcripts remain in Tiro's private app preferences. Delete individual entries, use **Clear history**, clear Tiro's app data, or uninstall Tiro to remove them. App backup and device-transfer rules exclude this history.
- Tiro does not send analytics, telemetry, crash reports, or application logs. Android and the installed system speech service may produce their own operational diagnostics.
- Tiro has no updater and makes no background update checks.
- Tiro does not read or write the clipboard. **↺** inserts through Android's input connection, so it does not expose text to clipboard-history tools.

## Current scope and limitations

- English (US) recognition only in this preview.
- On-device service and language support depend on the Android build and device vendor.
- Speech recognition can stop after a pause because Android controls endpoint detection.
- The overlay is for voice entry and a few session controls, not manual keyboard typing.
- There is no whisper.cpp fallback, OpenAI mode, cloud post-processing, or custom vocabulary prompt yet.
- Some protected fields, applications, or device policies may reject third-party input methods or committed text.

## Development

```sh
./gradlew test
./gradlew assembleDebug
```

The app uses Kotlin and Android platform APIs: `InputMethodService`, `InputConnection`, and the explicit on-device `SpeechRecognizer`. It does not currently include AndroidX, analytics, networking, advertising, or third-party runtime dependencies.

Release preparation is documented in [APP_PUBLISH.md](APP_PUBLISH.md).

## License

No license has been selected yet. The repository is public, but public visibility alone does not grant reuse or redistribution rights.

