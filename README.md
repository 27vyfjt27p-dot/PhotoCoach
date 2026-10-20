# PhotoCoach V0.1 (experimental)

Offline Android overlay + MediaProjection frame count + gravity tilt hint + optional Chinese TTS. No internet permission, no paid API. **No computer vision / person detection / AI composition yet.**

## Build without Android Studio
1. Upload all files and folders **inside** this project to the root of a GitHub repository (keep `.github/workflows/build-apk.yml`). GitHub's browser uploader may not preserve empty folders, but these folders contain files.
2. Go to Actions → Build Android APK → Run workflow (or wait for push).
3. When the workflow succeeds, download `PhotoCoach-debug-apk` under Artifacts, unzip, install `app-debug.apk` on your Android device.
4. If the build fails, open the failing step and copy its error log for troubleshooting.

## Test
Grant overlay permission, then tap Start and approve screen capture. Open the stock camera app. The overlay shows captured frame count and an approximate **device** tilt angle. Voice is OFF by default and requires a Chinese TTS engine. Stop via the app.

## Important limitations
- Frame count is not proof that camera content is readable; Android/ColorOS may block capture or show black frames.
- Overlay may appear inside captured frames; no image analysis is performed.
- Rotation, background behavior, capture permission, battery optimization, and ColorOS compatibility require testing on OnePlus 15T.
- Capturing the screen is sensitive: only grant permission when needed. This app does not request Internet permission or upload frames.
- This source was not compiled locally in the authoring environment; GitHub Actions is the compilation test.
