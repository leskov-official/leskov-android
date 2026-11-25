# LESKOV Android 3.4.3

Android 8.0+ (API 26), target/compile API 36. Kotlin, Jetpack Compose and WebView. Application ID: `com.aistudio.leskov.musix`. Version code: 11.

## Release build

Use JDK 21, Android SDK 36 and the checked-in Gradle wrapper. Supply the Firebase Android configuration for this application in `app/google-services.json`. Build and test with:

```sh
./gradlew :app:testDebugUnitTest :app:lintRelease :app:bundleRelease
```

Gradle writes `app/build/outputs/bundle/release/app-release.aab`. Local signing uses `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`. An unconfigured build produces an unsigned candidate.

The [Android workflow](https://github.com/leskov-official/leskov-android/actions/workflows/android.yml) builds a debug APK, runs tests and lint, and archives an AAB. For a Play-signed build, configure repository secrets `UPLOAD_KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` and the existing `FIREBASE_ANDROID_CONFIG_BASE64`. Run the workflow manually on the reviewed release revision with **sign_for_play** selected. It verifies the bundle signature and compares its signer certificate to the configured upload key. Upload the resulting **LESKOV-Android-Play-signed** artifact in Play Console. Confirm the upload certificate and unused version code for the Play listing before uploading.

Device acceptance scenarios are in [the release checklist](docs/RELEASE_CHECKLIST_RU.md).

ADB crash capture helper: `tools/CAPTURE_ANDROID_CRASH_RU.sh`.

**Firebase:** native FCM push requires a real `google-services.json` provided privately through GitHub Actions Secrets and a configured backend. Never commit private keys or credentials. Music notifications use Android MediaSession independently of FCM.
