# LESKOV Android 3.1 — development sources

Kotlin, Jetpack Compose, WebView, Media3. Supports API 26–36 (target API 36).

**No verified installable APK is included.** This environment did not have Android SDK and Gradle. To build a debug APK, see `../BUILD_APK_GITHUB_RU.md` and `.github/workflows/android.yml`.

The exported project does not include `gradle/wrapper/gradle-wrapper.jar`. Until a real wrapper is generated, use system `gradle` 9.3.1 or GitHub Actions. Once Gradle is available, run `gradle wrapper --gradle-version 9.3.1` and then:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

The real Firebase Android client config is optional for a debug build, but **required for native push**. Server-side FCM keys must never be bundled here.

See `docs/RELEASE_CHECKLIST_RU.md`, `../README_RU.md` and `../AUDIT_3_1_RU.md` for test evidence and limitations.
