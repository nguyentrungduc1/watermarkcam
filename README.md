# Lucid Camera

Android camera app with photo and video capture, ePass Nhat Le 2 timestamp
watermarks, and on-device selfie retouching.

## Build

Requires JDK 17, Android SDK 34 and Build Tools 34.0.0. Configure `ANDROID_HOME`
or set the SDK path in an untracked `local.properties` file.

Windows:

```powershell
.\gradlew.bat assembleDebug lintDebug
```

Linux/macOS:

```sh
bash ./gradlew assembleDebug lintDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`. Requires Android 7.0 or later.

## GitHub Actions

The **Build APK** workflow runs on pushes and pull requests, or manually from
the Actions tab. Download **Lucid-Camera-1.2-debug** from a successful run's
Artifacts section and extract `app-debug.apk`.

Builds are debug-signed for testing. Signing keys may differ between builds;
installing an update can require uninstalling the previous version, which removes
app-private data and settings. Private signing keys must not be committed.

## Dependencies

CameraX, Camera2 and MediaPipe. Model source information is available in
[models/README.md](app/src/main/assets/models/README.md).
