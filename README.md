# LESKOV for Android · 3.4.3

Android client for [LESKOV](https://lesv.online/) — a platform for artists and their communities.

[![Android CI](https://github.com/leskov-official/leskov-android/actions/workflows/android.yml/badge.svg)](https://github.com/leskov-official/leskov-android/actions/workflows/android.yml)

**Release status:** 3.4.3 is a release candidate until the signed APK passes device and security acceptance. Debug artifacts are for developers only. No antivirus or Google Play Protect approval is implied by a successful build.

| | |
|---|---|
| Android | 8.0+ (API 26) |
| Compile / target SDK | 36 |
| Application ID | `com.aistudio.leskov.musix` (unchanged) |
| Version | 3.4.3 / versionCode 11 |
| Architecture | Kotlin, Jetpack Compose, Android WebView, native MediaSession, Firebase Cloud Messaging |
| Languages | Russian, Estonian, English |

## Download and install

- Official website: [lesv.online/install.php](https://lesv.online/install.php)
- Official repository: [leskov-official/leskov-android](https://github.com/leskov-official/leskov-android)
- Published builds: [GitHub Releases](https://github.com/leskov-official/leskov-android/releases), when available.

Use only a verified signed release APK. Compare the SHA-256 checksum with the release notes. Keep Google Play Protect enabled. If installation is blocked as harmful, stop and contact the developer. The web app is available as an alternative.

## What is in 3.4.3

- Dialog and friend search lists share a single scroll container. A selected conversation has its own message scroll area and a return link to the dialogue list.
- The native bottom bar hides for the keyboard; the WebView accounts for IME and system insets.
- The app keeps its existing package, scoped first-party web bridges, TLS checks, session and notification integrations. The site owns music playback; Android MediaSession reflects playback status.
- WebView Safe Browsing remains enabled; file URL access is disabled. File selection is restricted to content URIs on the trusted page. External intent launches from page navigation require a user gesture.
- Signed release candidate workflow verifies the package, version, signing certificate, non-debuggable flag and SHA-256. It does not publish automatically.

## Local development

Install JDK 21, Android SDK platform 36, build tools 36.0.0, Gradle 9.3.1, Python 3, Node.js and PHP CLI for the backend fixture tests. The wrapper JAR is not present in this source distribution; use installed Gradle or generate the wrapper with a trusted Gradle installation.

```bash
python3 -m pip install PyYAML playwright
python3 -m playwright install chromium
python3 tools/validate_source.py
python3 tools/test_mobile_backend.py
python3 tools/test_verify_release.py
python3 tools/test_dialog_layout.py
gradle --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Debug builds can run without Firebase configuration; push delivery will be unavailable. Full FCM requires a real `app/google-services.json` registered for this package and the existing server integration. See [backend contract](docs/BACKEND_FCM_CONTRACT.md). Do not commit credentials.

## Signed release (maintainers)

Use the existing app-signing key for direct APK distribution. Changing the key can prevent existing users from updating. Do not generate a new key merely to get around Play Protect. For Google Play, follow the Play App Signing / upload key requirements separately.

In GitHub repository **Settings → Secrets and variables → Actions**, configure these **Secrets**:

- `FIREBASE_ANDROID_CONFIG_BASE64`: real Firebase Android configuration (base64).
- `SIGNING_KEYSTORE_BASE64`: existing release keystore (base64).
- `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`: existing signing credentials.

Configure the **Variable** `RELEASE_CERT_SHA256` with the independently known SHA-256 fingerprint of the existing app-signing certificate. Obtain it from a trusted original release or key, not from the candidate you are trying to verify. No secret values belong in the README, commits, logs or chat.

Run **Actions → LESKOV Android — signed release candidate → Run workflow → main**. Download `LESKOV-3.4.3-signed-candidate` after all steps succeed. It contains a signed APK, AAB, signature report, package metadata, SHA256SUMS and source commit. Verify locally:

```bash
sha256sum -c SHA256SUMS.txt
apksigner verify --verbose --print-certs LESKOV-3.4.3.apk
```

Do not distribute until [the release acceptance checklist](docs/RELEASE_CHECKLIST_RU.md) passes. If Play Protect blocks a legitimate APK after review, use the [official appeal procedure](https://support.google.com/googleplay/android-developer/contact/protectappeals). Do not instruct users to disable protection. Publish the reviewed APK and its checksum/report to GitHub Releases and the LESKOV admin installation page only after approval of the release by the maintainer.

## Security and privacy

The Android client uses HTTPS and first-party web messaging. Camera and microphone support calls and recording; notifications support messages and calls; media service supports playback controls. The backend and third-party dependencies are also part of the security review. A signature proves integrity and signing identity, not that the software is malware-free.

Report security issues privately to [info@lesv.info](mailto:info@lesv.info), without including account credentials or private messages. [Privacy policy](https://lesv.online/privacy_policy.php) · [Terms](https://lesv.online/terms.php).

## Документация / Documentation

- [3.4.3: выпуск, исправления и ограничения (RU)](docs/ANDROID_3_4_3_RU.md)
- [Приёмочный тест-план (RU)](docs/RELEASE_CHECKLIST_RU.md)
- [Backend FCM contract](docs/BACKEND_FCM_CONTRACT.md)
