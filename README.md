# LESKOV Android 3.4 — source project

Android 8.0+ (API 26), target/compile API 36. Kotlin + Jetpack Compose + WebView + Media3.

## Debug APK

Download `LESKOV-Android-debug-APK` from a successful run of [LESKOV Android — build, test and APK](https://github.com/leskov-official/leskov-android/actions/workflows/android.yml). Check that the run matches the commit you want to install.

GitHub Actions installs Java 21, Android SDK 36 and Gradle 9.3.1, builds the debug APK, runs Robolectric, Android lint and the website audio bridge tests, and uploads APK and diagnostic artifacts. The local `gradlew` wrapper JAR is not bundled; use Gradle 9.3.1 or generate the wrapper with `gradle wrapper`.

Version 3.4 adds adaptive web text scaling, more accessible native screens and integration with both website Premium player engines, including authorized background queues. See [Android 3.4 changes and validation](docs/ANDROID_3_4_RU.md) for the tested scenarios and device acceptance checks. Browser fixtures verify compatibility with the website's player scripts; they do not replace testing with an authenticated Premium account on Android.

ADB crash capture helper: `tools/CAPTURE_ANDROID_CRASH_RU.sh`.

**Firebase:** native FCM push requires a real `google-services.json` provided privately through GitHub Actions Secrets and a configured backend. Never commit private keys or credentials. Music notifications use Android MediaSession independently of FCM.
