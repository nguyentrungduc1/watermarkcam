# Lucid Camera 1.2

Android camera app with photo/video capture, ePass Nhat Le 2 timestamp watermark,
orange-red controls, and offline skin segmentation for saved selfies.

## Build APK on GitHub

1. Extract the source ZIP and push its contents to the repository root. Include
   `.github`, `gradle`, `gradlew`, `gradlew.bat`, and the model in `app/src/main/assets`.
   Uploading only the ZIP does not run the workflow.
2. Open **Actions > Build APK**. It runs on pushes and can also run manually.
3. After a successful run, download **Lucid-Camera-1.2-debug** under **Artifacts**.
4. Extract the artifact and install `app-debug.apk` on Android 7.0 or newer.

This workflow builds a debug-signed APK for testing, not a Play Store release.
GitHub's debug signing key differs from the local development key and may change
between runs. Updating an existing installation can require uninstalling it first,
which removes app-private settings/data. No signing keys are included in this ZIP.

## Local build

Use JDK 17, Android SDK platform 34 and build-tools 34.0.0.
Set ANDROID_HOME or create an untracked `local.properties` with your SDK location.

```sh
bash ./gradlew assembleDebug lintDebug
```

Windows: `gradlew.bat assembleDebug lintDebug`.
Output: `app/build/outputs/apk/debug/app-debug.apk`.

## Included assets

The skin segmentation model is bundled and runs on-device. See
`app/src/main/assets/models/README.md` for its source. Test photos/videos, device
screenshots, build caches, local SDK paths, and private keys are excluded.
