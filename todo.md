Technical Specification: Magnetic Camera for Android
1. Project Summary

Build an Android mobile app called Magnetic Camera.

The app turns an Android phone into a visual magnetic-field scanner. It uses the phone’s magnetometer to read the local magnetic field vector, then visualizes the readings as live values, graphs, and reconstructed heatmaps over a photographed surface.

The target physical test device is Google Pixel 8. Pixel 8 includes an accelerometer, gyrometer, magnetometer, and barometer in its official hardware specifications. Android’s magnetic field sensor exposes three-axis geomagnetic field values in microtesla, and the uncalibrated magnetic field sensor exposes raw field values plus estimated hard-iron bias values.

This is not a true magnetic imaging camera. A phone has one magnetometer sensor point, not a magnetic-field sensor array. The app must communicate this clearly: it reconstructs magnetic maps by scanning many physical positions.

2. Product Goal

Create a technically interesting, visually satisfying Android prototype that lets the user:

See live magnetic-field readings.
Calibrate against the local background field.
Scan a flat surface or object in a manual grid.
Generate a magnetic heatmap.
Overlay the heatmap on a reference photo.
Save and export the scan.

The app should feel like a “scientific instrument” rather than a generic utility.

3. Non-Goals

Do not build these in v1:

Cloud sync.
Accounts.
Social sharing.
AI interpretation.
Wire detection in walls.
Medical/safety claims.
Real-time AR magnetic field reconstruction.
Automatic object recognition.
iOS version.
True 3D magnetic tomography.
Background scanning.
Monetization.

The first version should be local-only, Android-only, and focused on a strong physical demo.

4. Core Concept

The app has three core modes:

4.1 Live Meter

A live instrument view showing:

Magnetic field magnitude.
X/Y/Z vector components.
Delta from baseline.
Sensor accuracy status.
Live scrolling chart.
Visual “magnetic intensity” blob or gauge.
4.2 Surface Scan

A guided manual grid scan.

The user chooses a grid size, for example 5×5, 7×7, or 10×10. The app highlights one cell at a time. The user physically places the phone’s scanning point over that cell and captures a short sample window. After all cells are captured, the app generates a heatmap.

This is the most important v1 mode.

4.3 Magnetic Photo

The app takes or imports a reference photo of the scanned object/surface, then overlays the heatmap on top of the selected scan area.

This produces the “camera” illusion while staying technically honest.

5. Target Platform
5.1 Platform
Android only.
Primary test device: Pixel 8.
Language: Kotlin.
UI: Jetpack Compose + Material 3.
Camera: CameraX.
Architecture: local-first, no backend.

CameraX should be used because it is the recommended Jetpack camera library for new Android camera apps and supports preview, image analysis, image capture, and video capture use cases.

5.2 Recommended SDK Settings

Use the latest stable Android Gradle Plugin and compile SDK available in the development environment.

Recommended baseline:

minSdk: 26 or higher
targetSdk: latest stable available
language: Kotlin
UI: Jetpack Compose
camera: CameraX
storage: Room + app-specific file storage

The app should not require Google Play Services for the MVP.

6. Permissions

Required:

<uses-permission android:name="android.permission.CAMERA" />

Optional:

<uses-permission android:name="android.permission.HIGH_SAMPLING_RATE_SENSORS" />

The high sampling rate permission allows sensor access above 200 Hz and has normal protection level on Android. The app should still work without depending on high-frequency sampling, because magnetometer refresh rates vary by device.

No location, contacts, microphone, network, or background permissions are required for v1.

7. Sensor Requirements
7.1 Sensor Priority

When the app starts, detect available sensors in this priority order:

Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED
Sensor.TYPE_MAGNETIC_FIELD

Use uncalibrated magnetic field data if available, because it avoids some system-level discontinuities and exposes hard-iron bias estimates separately. If unavailable, fall back to calibrated magnetic field.

7.2 Sensor Data

For each magnetic sample, collect:

timestampNanos
sensorType
accuracy
xMicroTesla
yMicroTesla
zMicroTesla
biasXMicroTesla?      // only for uncalibrated sensor if available
biasYMicroTesla?
biasZMicroTesla?
magnitudeMicroTesla
baselineDeltaMicroTesla
baselineVectorDeltaMicroTesla

Magnitude formula:

magnitude = sqrt(x*x + y*y + z*z)

Preferred delta metrics:

magnitudeDelta = magnitude - baselineMagnitude

vectorDelta = sqrt(
  (x - baselineX)^2 +
  (y - baselineY)^2 +
  (z - baselineZ)^2
)

For heatmaps, use vectorDelta as the default intensity metric. It is less ambiguous than signed magnitude delta.

7.3 Sampling

Default mode:

SensorManager.SENSOR_DELAY_GAME

Optional advanced mode:

SensorManager.SENSOR_DELAY_FASTEST

Do not assume the actual sampling rate. Measure it from timestamps and display the observed sample rate in the diagnostics panel.

7.4 Lifecycle

Register sensor listeners only while the relevant screen is active and visible. Unregister listeners on pause/stop.

The app must not perform continuous background sensor collection.

8. Calibration
8.1 Baseline Calibration

Provide a Set Baseline action.

When the user taps it:

Collect samples for 2 seconds.
Ignore the first 200 ms.
Compute mean X/Y/Z.
Compute mean magnitude.
Store the baseline in memory and in the current session.

Baseline data model:

data class MagneticBaseline(
    val createdAtMillis: Long,
    val sampleCount: Int,
    val xMean: Float,
    val yMean: Float,
    val zMean: Float,
    val magnitudeMean: Float,
    val xStdDev: Float,
    val yStdDev: Float,
    val zStdDev: Float,
    val magnitudeStdDev: Float
)
8.2 User Guidance

Before baseline calibration, show:

Move the phone away from magnets, speakers, laptop hinges, chargers, metal tables, and magnetic cases. Hold it still for 2 seconds.

After calibration, show baseline magnitude and noise level.

8.3 Case Warning

The app should tell the user to remove magnetic phone cases, MagSafe-style rings, wallet cases, or cases with metal plates.

9. Data Processing
9.1 Filtering

Implement two simple filters:

Exponential Low-Pass Filter
filtered = previous + alpha * (current - previous)

Default:

alpha = 0.25

Allow changing alpha in developer settings.

Rolling Median Filter

Maintain a small rolling window, default size 5, over magnitude/vector delta.

Use median-filtered values for UI stability.

9.2 Cell Capture

For each scan cell:

Wait until the user taps Capture or until auto-capture conditions are met.
Collect a sample window of 500–800 ms.
Compute mean, median, min, max, and standard deviation.
Store the final cell value.

Recommended default:

captureWindowMs = 700
minimumSamples = 15

If fewer than minimumSamples are available, keep sampling until the minimum is reached or until 2 seconds have passed.

9.3 Stability Indicator

During capture, show a stability ring:

Stable if standard deviation of vector delta over the last 500 ms is below a configurable threshold.
Default threshold: derive dynamically from baseline noise.

Suggested rule:

stable = recentStdDev <= max(1.5 * baselineStdDev, 1.0 µT)

Do not block manual capture if unstable. Just warn visually.

10. Heatmap Generation
10.1 Input

Heatmap input is a rectangular grid of cell measurements:

gridWidth
gridHeight
value[row][col]

Default value:

cell.vectorDeltaMean
10.2 Normalization

Support three normalization modes:

Auto Local
Normalize min/max from the current scan.
Baseline Delta Fixed Scale
User chooses max delta, e.g. 10 µT, 50 µT, 100 µT, 500 µT.
Absolute Field
Uses total field magnitude, not baseline delta.

Default:

Auto Local
10.3 Interpolation

For v1, implement bilinear interpolation from grid cells into a bitmap.

Output heatmap bitmap sizes:

preview: 512 × 512
export: 2048 × 2048
10.4 Color Palette

Implement at least two palettes:

Scientific: dark → blue → green → yellow → red.
Monochrome glow: black → white.

The palette implementation should be isolated behind:

interface HeatmapPalette {
    fun colorFor(normalizedValue: Float): Color
}
10.5 Legend

Every heatmap must show:

min value,
max value,
unit: µT,
metric name: Vector delta from baseline or Absolute magnetic field.
11. Magnetic Photo Workflow
11.1 Flow
User starts New Surface Scan.
User optionally takes a reference photo.
If a photo exists, user selects a rectangular scan area by dragging four corners.
User chooses grid size.
User captures all grid cells.
App renders heatmap.
App overlays heatmap on the selected photo area.
User saves or exports.
11.2 Corner Selection

For v1, implement manual corner selection.

Do not implement automatic plane detection in v1.

Store four normalized corner coordinates:

data class NormalizedPoint(
    val x: Float, // 0.0 to 1.0
    val y: Float  // 0.0 to 1.0
)

data class PhotoOverlayArea(
    val topLeft: NormalizedPoint,
    val topRight: NormalizedPoint,
    val bottomRight: NormalizedPoint,
    val bottomLeft: NormalizedPoint
)
11.3 Overlay

Render heatmap into the selected photo quadrilateral.

For v1, a simple rectangular overlay is acceptable if perspective transform is too slow to implement. However, the data model should already support four corners.

Overlay controls:

opacity slider: 0–100%
palette picker
normalization picker
show/hide grid
show/hide legend
12. User Interface
12.1 Navigation

Screens:

Home
Live Meter
New Surface Scan
Scan Capture
Heatmap Result
Gallery
Session Detail
Settings / Diagnostics
12.2 Home Screen

Show three primary actions:

Live Meter
New Surface Scan
Gallery

Also show small device status:

Magnetometer: available / unavailable
Sensor type: calibrated / uncalibrated
Camera: available / unavailable
12.3 Live Meter Screen

Required UI elements:

Large live magnetic magnitude in µT.
Delta from baseline.
X/Y/Z values.
Accuracy state.
Sampling rate.
Live scrolling graph.
“Set Baseline” button.
“Freeze” button.
“Save Snapshot” button.
“Start Surface Scan” shortcut.

Advanced collapsible section:

sensor name,
vendor,
resolution,
maximum range,
minimum delay,
selected sensor type.
12.4 Surface Scan Setup Screen

Fields:

Scan name
Grid size: 5×5 / 7×7 / 10×10 / custom
Photo: take / skip
Baseline: current / recalibrate
Capture mode: manual / auto when stable

Default:

grid: 7×7
photo: take
capture: manual
12.5 Scan Capture Screen

Display:

grid with current cell highlighted;
current cell index, e.g. Cell 12 of 49;
live µT delta;
stability indicator;
capture button;
redo previous cell;
skip cell;
progress bar.

Scanning order:

row-major by default

Allow snake mode later, but not required for v1.

12.6 Heatmap Result Screen

Display:

heatmap only;
photo + heatmap overlay if photo exists;
legend;
min/max/mean/stddev;
opacity slider;
palette switch;
normalization switch;
save/export actions.
12.7 Gallery

Show saved sessions as cards:

thumbnail
session name
date/time
grid size
max delta
sensor type
12.8 Session Detail

Show:

reference photo;
heatmap overlay;
raw grid values;
metadata;
export buttons.
13. Storage

Use local-only storage.

13.1 Database

Use Room for session metadata.

Entities:

@Entity
data class ScanSessionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAtMillis: Long,
    val deviceManufacturer: String,
    val deviceModel: String,
    val androidVersion: String,
    val sensorName: String?,
    val sensorVendor: String?,
    val sensorType: Int,
    val gridWidth: Int,
    val gridHeight: Int,
    val baselineX: Float,
    val baselineY: Float,
    val baselineZ: Float,
    val baselineMagnitude: Float,
    val photoUri: String?,
    val heatmapImageUri: String?,
    val overlayImageUri: String?,
    val rawDataUri: String?,
    val notes: String?
)

Cell entity:

@Entity
data class GridCellMeasurementEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val row: Int,
    val col: Int,
    val sampleCount: Int,
    val capturedAtMillis: Long,
    val xMean: Float,
    val yMean: Float,
    val zMean: Float,
    val magnitudeMean: Float,
    val vectorDeltaMean: Float,
    val magnitudeDeltaMean: Float,
    val vectorDeltaStdDev: Float,
    val accuracy: Int
)
13.2 Files

Store in app-specific storage:

/photos/{sessionId}.jpg
/heatmaps/{sessionId}.png
/overlays/{sessionId}.png
/raw/{sessionId}.json
/exports/{sessionId}.csv
14. Export
14.1 PNG Export

Export:

heatmap-only PNG;
photo-overlay PNG if reference photo exists.

Include legend unless user disables it.

14.2 JSON Export

JSON structure:

{
  "app": "Magnetic Camera",
  "schemaVersion": 1,
  "session": {
    "id": "...",
    "name": "...",
    "createdAt": "...",
    "device": {
      "manufacturer": "...",
      "model": "...",
      "androidVersion": "..."
    },
    "sensor": {
      "type": 14,
      "name": "...",
      "vendor": "..."
    },
    "baseline": {
      "x": 0.0,
      "y": 0.0,
      "z": 0.0,
      "magnitude": 0.0
    },
    "grid": {
      "width": 7,
      "height": 7
    }
  },
  "cells": [
    {
      "row": 0,
      "col": 0,
      "sampleCount": 32,
      "xMean": 0.0,
      "yMean": 0.0,
      "zMean": 0.0,
      "magnitudeMean": 0.0,
      "vectorDeltaMean": 0.0,
      "vectorDeltaStdDev": 0.0
    }
  ]
}
14.3 CSV Export

CSV columns:

session_id,row,col,sample_count,x_mean,y_mean,z_mean,magnitude_mean,magnitude_delta_mean,vector_delta_mean,vector_delta_stddev,accuracy
15. Architecture

Use MVVM with clean-ish separation.

Recommended packages:

com.example.magneticcamera
  app/
  core/
    sensors/
    math/
    graphics/
    storage/
    export/
  data/
    db/
    repository/
  domain/
    model/
    scan/
    calibration/
  ui/
    home/
    live/
    scan/
    result/
    gallery/
    settings/
  camera/
15.1 Sensor Layer

Create:

interface MagneticSensorReader {
    val samples: Flow<MagneticSample>
    val sensorInfo: StateFlow<MagneticSensorInfo?>
    fun start(config: SensorReadConfig)
    fun stop()
}

Implementation:

class AndroidMagneticSensorReader(
    private val context: Context
) : MagneticSensorReader, SensorEventListener

The implementation must:

detect preferred sensor;
fall back if uncalibrated sensor is missing;
expose sample flow;
handle sensor accuracy changes;
unregister listeners safely.
15.2 Domain Layer

Create:

class BaselineCalibrator
class MagneticSampleProcessor
class GridScanController
class HeatmapGenerator
class ScanSessionRepository
15.3 UI State

Use immutable state classes.

Example:

data class LiveMeterUiState(
    val sensorAvailable: Boolean,
    val sensorInfo: MagneticSensorInfo?,
    val latestSample: MagneticSample?,
    val baseline: MagneticBaseline?,
    val recentSamples: List<MagneticSample>,
    val samplingRateHz: Float,
    val accuracyLabel: String,
    val isRecording: Boolean,
    val errorMessage: String?
)
16. Error Handling

Handle these cases explicitly:

16.1 No Magnetometer

Show:

This device does not expose a magnetic field sensor. Magnetic Camera cannot scan magnetic fields on this device.

App should still open and show an explanation.

16.2 Camera Permission Denied

Surface scanning should still work without a photo.

Show:

Camera permission is needed only for Magnetic Photo overlays. You can still run a grid scan without a reference photo.
16.3 Sensor Accuracy Unreliable

Show non-blocking warning:

Sensor accuracy is currently unreliable. Move the phone in a figure-eight motion or recalibrate baseline.
16.4 Extreme Values

If values spike beyond expected sensor range or become NaN/infinite:

discard invalid sample;
show diagnostic warning;
never crash.
16.5 Interrupted Scan

If the user leaves during a scan:

save partial progress;
allow resume;
allow discard.
17. UX Copy
17.1 First Launch Explanation

Show once:

Magnetic Camera uses your phone’s magnetometer. The phone measures the magnetic field at one sensor point inside the device. To create an image-like heatmap, you scan multiple positions over a surface.

This app is experimental. It is not an X-ray, not a wall-wire detector, and not a safety instrument.
17.2 Scan Instruction
Move the same point of the phone over each highlighted cell. Keep the phone at the same height and orientation during the scan.
17.3 Baseline Instruction
First measure the local background field. Hold the phone still away from magnets, speakers, laptops, chargers, and metal surfaces.
17.4 Result Explanation
Brighter areas show stronger deviation from the measured baseline. The image is reconstructed from grid samples, not captured by the camera sensor.
18. Visual Design Direction

The app should look like a compact field instrument.

Design style:

dark theme first;
high-contrast numeric readout;
oscilloscope-like graph;
scientific heatmap;
minimal chrome;
large physical-action buttons.

Avoid generic Material demo appearance.

Suggested aesthetic keywords:

field instrument
thermal camera
oscilloscope
lab notebook
analog meter
science fiction but restrained
19. Performance Requirements
Live Meter UI should update smoothly at approximately 30 FPS or less.
Do not recompose the entire screen on every sensor event if sampling is high.
Use throttling/debouncing for chart rendering.
Keep raw sensor buffers bounded.
Heatmap generation should run off the main thread.
Camera preview should remain responsive.

Recommended:

sensor processing: coroutine dispatcher Default
database/file IO: coroutine dispatcher IO
UI sampling: throttle to 30 Hz
chart history: last 10–30 seconds
max in-memory sample buffer: configurable, default 10,000
20. Testing Requirements
20.1 Unit Tests

Implement tests for:

magnitude calculation;
vector delta calculation;
baseline mean/stddev;
low-pass filter;
median filter;
grid cell statistics;
heatmap normalization;
bilinear interpolation;
JSON export schema.
20.2 Fake Sensor

Create a fake sensor source for testing:

class FakeMagneticSensorReader : MagneticSensorReader

It should support:

constant baseline field;
synthetic magnetic hotspot;
noisy readings;
missing sensor state;
unreliable accuracy state.
20.3 Manual Test Scenarios

Test with:

Ambient room field.
Small fridge magnet.
Speaker or headphone driver.
Laptop hinge area.
Electric toothbrush / small motor.
Metal table as negative test.
Phone case on/off comparison.
20.4 Acceptance Test on Pixel 8

The app passes v1 if, on Pixel 8:

Live Meter displays changing magnetic values.
Baseline calibration completes.
A 5×5 scan can be completed without crash.
A heatmap is generated.
A photo overlay can be exported as PNG.
JSON and CSV exports contain all captured cell values.
App remains usable if camera permission is denied.
App clearly states that it is a point-sensor reconstruction.
21. Implementation Milestones
Milestone 1: Project Skeleton

Deliver:

Android project setup.
Kotlin + Compose.
Navigation.
Home screen.
Dark theme.
Basic diagnostics screen.

Acceptance:

App builds and runs on Pixel 8.
Home screen shows placeholder sensor status.
Milestone 2: Sensor Engine

Deliver:

Magnetic sensor detection.
Uncalibrated-first fallback logic.
Live sample flow.
Sampling rate calculation.
Sensor diagnostics.

Acceptance:

Live X/Y/Z and magnitude are visible.
Sensor name/vendor/type are visible.
App does not crash if sensor is unavailable.
Milestone 3: Live Meter

Deliver:

Live numeric readout.
Scrolling chart.
Baseline calibration.
Delta values.
Accuracy warning.

Acceptance:

User can set baseline.
Delta changes near a magnet.
Chart updates smoothly.
Milestone 4: Manual Grid Scan

Deliver:

Scan setup.
Grid UI.
Cell-by-cell capture.
Cell statistics.
Partial scan resume/discard.

Acceptance:

User can complete 5×5 and 7×7 scans.
Heatmap data is computed from captured cells.
Milestone 5: Heatmap Rendering

Deliver:

Heatmap generation.
Palette support.
Normalization modes.
Legend.
Result screen.

Acceptance:

Heatmap visually distinguishes magnet/no-magnet areas.
User can switch palette and normalization.
Milestone 6: Camera and Magnetic Photo

Deliver:

CameraX photo capture.
Manual scan-area selection.
Heatmap overlay.
Opacity slider.
PNG export.

Acceptance:

User can take a photo, scan grid, overlay heatmap, and save PNG.
Milestone 7: Persistence and Gallery

Deliver:

Room database.
Save sessions.
Gallery.
Session detail.
JSON/CSV export.

Acceptance:

App can close and reopen saved scans.
Exported files are readable and contain correct data.
Milestone 8: Polish and Safety

Deliver:

First-launch explanation.
Sensor limitations copy.
Error handling.
Visual polish.
Manual test checklist completed.

Acceptance:

The app feels like a coherent prototype, not just a sensor demo.
22. Suggested Repository Structure
MagneticCamera/
  app/
    build.gradle.kts
    src/main/
      AndroidManifest.xml
      java/com/example/magneticcamera/
        MainActivity.kt
        MagneticCameraApp.kt
        app/
          AppNavHost.kt
          AppTheme.kt
        core/
          sensors/
            MagneticSensorReader.kt
            AndroidMagneticSensorReader.kt
            FakeMagneticSensorReader.kt
            MagneticSensorInfo.kt
            MagneticSample.kt
          math/
            MagneticMath.kt
            RollingStats.kt
            LowPassFilter.kt
            MedianFilter.kt
          graphics/
            HeatmapGenerator.kt
            HeatmapPalette.kt
            OverlayRenderer.kt
          export/
            CsvExporter.kt
            JsonExporter.kt
            PngExporter.kt
        data/
          db/
            AppDatabase.kt
            ScanSessionEntity.kt
            GridCellMeasurementEntity.kt
            ScanDao.kt
          repository/
            ScanSessionRepositoryImpl.kt
        domain/
          calibration/
            BaselineCalibrator.kt
          scan/
            GridScanController.kt
            ScanModels.kt
        camera/
          CameraPreview.kt
          PhotoCaptureController.kt
        ui/
          home/
          live/
          scan/
          result/
          gallery/
          settings/
      res/
        values/
        drawable/
23. Important Physical Limitations

The app must not hide these limitations:

The phone measures at one internal sensor point.
The sensor point is not necessarily aligned with the visible camera center.
Very near magnets can produce steep gradients, so a few centimeters of position error matter.
Magnetic cases and accessories distort readings.
Metal surfaces may distort readings.
The app cannot reliably detect electrical wires in walls.
The app cannot infer object identity from magnetic data alone.
Heatmaps are reconstructed measurements, not direct images.
24. Optional Post-MVP Ideas

Do not implement until v1 is complete.

24.1 ARCore Freehand Scan

Use ARCore pose tracking to associate magnetic samples with approximate 3D positions.

Result:

freehand magnetic painting;
points anchored in world space;
camera overlay.

Risk:

magnetometer sensor location differs from camera pose;
phone orientation and distance matter;
more complex UX.
24.2 Sensor Hotspot Calibration

Let the user find the phone’s strongest magnetic sensor point by moving a small magnet around the back of the phone. Store an approximate visual marker for future scans.

24.3 Time-Lapse Magnetic Recording

Record magnetic field changes over time for motors, speakers, chargers, or moving magnets.

24.4 Audio-Haptic Mode

Convert magnetic intensity into sound or vibration.

24.5 Compare Scans

Allow before/after comparison:

object with magnet removed;
motor off/on;
laptop closed/open;
case on/off.
25. Definition of Done for MVP

The MVP is done when all of the following are true:

Android app runs on Pixel 8.
It reads magnetic field data from the best available sensor.
It displays live magnitude and vector values.
It supports baseline calibration.
It supports manual grid scanning.
It generates a heatmap from grid data.
It can take a reference photo.
It can overlay the heatmap on the photo.
It saves sessions locally.
It exports PNG, JSON, and CSV.
It includes clear limitation/safety copy.
It has unit tests for math and heatmap logic.
It does not require internet access.
It does not require accounts.
It does not claim to be a safety, medical, or professional diagnostic tool.
26. Agent Instruction

Implement the app incrementally. Do not overbuild. Prioritize a working Pixel 8 prototype over architectural perfection.

Start with:

Sensor engine.
Live Meter.
Baseline calibration.
Manual 5×5 scan.
Heatmap rendering.
Photo overlay.
Save/export.

Do not implement ARCore, cloud sync, AI, account login, monetization, social features, or background scanning unless explicitly requested later.

The first impressive demo should be:

Open app → set baseline → scan a 5×5 grid over a small magnet hidden under paper → generate a heatm