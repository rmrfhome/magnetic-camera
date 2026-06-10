# Magnetic Camera

Android prototype that turns a phone magnetometer into a manual magnetic-field scanner. It reads the best available magnetic sensor, lets the user set a local baseline, captures a manual grid scan, renders a heatmap, optionally overlays it on a reference photo, and saves local PNG/JSON/CSV exports.

The app is intentionally honest about the physics: a phone has one magnetometer point, not a sensor array. Heatmaps are reconstructed from many physical positions.

## Current MVP Path

1. Open the app on a Pixel 8 or another Android device with a magnetometer.
2. Remove magnetic cases, wallet plates, MagSafe-style rings, and metal accessories.
3. Open `Live Meter` and confirm live X/Y/Z and magnitude values change.
4. Tap `Set Baseline` away from magnets, speakers, laptop hinges, chargers, and metal tables.
5. Start a `New Surface Scan`.
6. Choose `5x5` or `7x7`, optionally take or import a reference photo, and drag the scan-area corners.
7. Capture each highlighted cell while keeping the same phone point, height, and orientation.
8. Review the heatmap, adjust palette/normalization/opacity, then save PNG, JSON, and CSV.
9. Reopen saved scans from `Gallery`.

If you leave during a scan, the partial scan is saved as a local draft and can be resumed or discarded from Home.

## Build Setup

Required local tooling:

- JDK 17
- Android Studio with Android SDK installed
- Android SDK platform 35 or newer compatible platform
- Android build tools installed by Android Studio

Open this repository in Android Studio and let it sync Gradle dependencies. If building from a terminal, use the included Gradle wrapper:

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

If Android Studio installs the SDK in the default Windows location and Gradle cannot find it, create `local.properties`:

```properties
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

## Architecture

- `core/sensors`: magnetometer abstraction, Android sensor reader, fake sensor
- `core/math`: magnitude, vector delta, low-pass filter, median filter, rolling stats
- `core/graphics`: heatmap palette, bilinear interpolation, overlay renderer
- `core/export`: PNG, JSON, CSV export helpers
- `data/db`: Room entities and DAO
- `domain/calibration`: baseline calibration
- `domain/scan`: scan models, processing, cell capture
- `ui`: Compose screens for home, live meter, scan, result, gallery, settings
- `camera`: CameraX preview and still capture

## Manual Test Scenarios

- Ambient room field baseline
- Small fridge magnet under paper
- Speaker or headphone driver
- Laptop hinge area
- Electric toothbrush or small motor
- Metal table as negative test
- Phone case on/off comparison

For the full Pixel 8 acceptance pass, use [docs/manual-test-checklist.md](docs/manual-test-checklist.md).

## Safety Limits

Magnetic Camera is not an X-ray, wall-wire detector, safety instrument, medical device, or professional diagnostic tool. It cannot infer object identity from magnetic data alone.

## Verified Locally

- `.\gradlew.bat test assembleDebug lintDebug`
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- APK permissions: camera and high-sampling-rate sensors, plus AndroidX internal receiver permission
- Camera and compass are declared as optional hardware features
