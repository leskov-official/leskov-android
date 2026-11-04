# LESKOV Android 3.3 — source project

Android 8.0+ (API 26), target/compile API 36. Kotlin + Jetpack Compose + WebView + Media3.

## Debug APK

For the latest installable build, use the GitHub workflow `.github/workflows/android.yml`:
`LESKOV Android — build, test and APK`. The last **verified** green workflow is build #5 for version 3.2. New 3.3 changes in this source archive **have not yet been compiled**; they require another push/Actions run.

There is no valid prebuilt APK inside this ZIP. GitHub Actions installs Java 21, Android SDK 36 and Gradle 9.3.1, runs `:app:assembleDebug`, `:app:testDebugUnitTest` and `:app:lintDebug`, and uploads an APK artifact. The local `gradlew` wrapper JAR is not bundled; use `gradle` 9.3.1 or generate the wrapper with `gradle wrapper`.

See `docs/LESKOV_3_3_CHANGES_RU.md` for exact fixes, remaining server-side requirements and what needs Samsung verification. ADB crash capture helper: `tools/CAPTURE_ANDROID_CRASH_RU.sh`.

**Firebase:** native FCM push requires a real `google-services.json` provided privately through GitHub Actions Secrets and a configured backend. Never commit private keys or credentials. Music notifications use Android MediaSession independently of FCM.
