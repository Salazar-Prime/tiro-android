# Android publishing directive

Tiro for Android is currently a source preview. This document is a release checklist, not authorization to publish an APK, create a GitHub release, or submit to Google Play.

## Before the first public binary

1. Select and add a software license, then review all artwork and notices for compatible terms.
2. Replace the development version with an approved version name and monotonically increasing version code.
3. Run `./gradlew test assembleDebug` from a clean clone and confirm CI passes.
4. Test press-to-talk, hands-free mode, cancellation, insertion, transcript deletion, keyboard switching, rotation, light/dark mode, and process recreation on physical Android 12, 13, 14, 15, and 16 devices where available.
5. Verify behavior both with a ready English language pack and with the pack missing. Never add a silent network-recognizer fallback.
6. Re-check the merged release manifest. Expected sensitive access is Microphone only; the app must not contain Internet, display-over-other-apps, Accessibility Service, clipboard, advertising, or analytics access unless the README and privacy materials are deliberately revised first.
7. Prepare an Android App Bundle using a maintainer-controlled upload key. Do not commit keystores, passwords, aliases, certificate exports, service-account files, or local SDK paths.
8. Complete the store privacy policy and Data safety form from observed release behavior. Distinguish Tiro's behavior from the device's installed speech provider and its language-pack download policy.
9. Confirm the current Google Play target API requirement, device catalog, content rating, screenshots, support URL, and availability state immediately before submission.
10. Publish human-written release notes focused on user-visible behavior and important compatibility limits.

Do not describe the app as downloadable until the intended store listing or signed public asset is actually live.

