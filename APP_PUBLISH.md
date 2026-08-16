# Android publishing directive

Tiro for Android is currently a source preview. This document is a release checklist, not authorization to publish an APK, create a GitHub release, or submit to Google Play.

## Before the first public binary

1. Select and add a software license, then review all artwork and notices for compatible terms.
2. Replace the development version with an approved version name and monotonically increasing version code.
3. Run `./gradlew test assembleDebug lintDebug` from a clean clone and confirm CI passes.
4. Test press-and-hold, quick-tap recording, drag before recording, drag while recording, remembered position, position reset, icon sizing, opacity, selection replacement, transcript deletion, focus changes, rotation, light/dark mode, process recreation, and service disable/re-enable on physical Android 12, 13, 14, 15, and 16 devices where available.
5. Verify with Gboard and at least one other normal keyboard that the IME remains visible and selected while the floating logo is used. Test multiple native apps and web fields, plus fields that reject accessibility insertion.
6. Confirm that the button appears only for focused editable fields, never for password fields, and disappears when focus leaves the field or consent/service access is revoked.
7. Verify behavior both with a ready English language pack and with the pack missing. Never add a silent network-recognizer fallback.
8. Re-check the merged release manifest and Accessibility Service metadata. Expected sensitive capabilities are `RECORD_AUDIO`, a service protected by `BIND_ACCESSIBILITY_SERVICE`, focused-window content retrieval, and `TYPE_ACCESSIBILITY_OVERLAY`. The app should not contain `INTERNET`, `SYSTEM_ALERT_WINDOW`, clipboard, advertising, analytics, screenshot, or gesture-control capabilities unless implementation and public disclosures are deliberately revised first.
9. Keep `android:isAccessibilityTool="false"` unless the product's primary purpose genuinely changes to disability support. Do not use that flag to avoid disclosure requirements.
10. Review the separate in-app **Focused-field access** disclosure against the shipping implementation. It must identify the data accessed, its use and sharing, appear before opening Accessibility settings, and require affirmative consent. Verify both consent and refusal/re-entry flows.
11. Complete Google Play's Accessibility Service declaration and provide the requested demonstration video. The video should show app launch, the full disclosure, consent and non-consent flows, enabling the service, and the floating-button feature in use.
12. Publish a complete privacy policy in the app and Play Console, then complete the Data safety form from observed release behavior. Distinguish Tiro's behavior from the installed system speech provider and its language-pack download policy.
13. Prepare an Android App Bundle using a maintainer-controlled upload key. Do not commit keystores, passwords, aliases, certificate exports, service-account files, or local SDK paths.
14. Confirm the current Google Play target API requirement, device catalog, content rating, screenshots, support URL, Accessibility API policy, and availability state immediately before submission.
15. Publish human-written release notes focused on user-visible behavior and important compatibility or privacy limits.

Current policy references:

- [Google Play AccessibilityService API policy](https://support.google.com/googleplay/android-developer/answer/10964491)
- [Google Play prominent disclosure and consent guidance](https://support.google.com/googleplay/android-developer/answer/11150561)
- [Android `TYPE_ACCESSIBILITY_OVERLAY` reference](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_ACCESSIBILITY_OVERLAY)

Do not describe the app as downloadable until the intended store listing or signed public asset is actually live.
