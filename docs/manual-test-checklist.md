# Manual Test Checklist

Last local verification: 2026-06-10.

This checklist separates checks that can run on the workstation from checks that require the target Pixel 8 hardware.

## Workstation Checks

- [x] `.\gradlew.bat test assembleDebug lintDebug` completes successfully.
- [x] Debug APK is generated at `app\build\outputs\apk\debug\app-debug.apk`.
- [x] APK declares `android.permission.CAMERA`.
- [x] APK declares `android.permission.HIGH_SAMPLING_RATE_SENSORS`.
- [x] Camera hardware is optional.
- [x] Compass/magnetometer hardware is optional, so the app can open on devices without a magnetometer and show an explicit explanation.
- [x] No location, contacts, microphone, network, account, or background permissions are declared.
- [x] App data backup is disabled in the manifest, keeping saved scans local unless the user explicitly shares exports.

## Pixel 8 Device Prep

- [ ] Pixel 8 is connected by USB.
- [ ] Developer options and USB debugging are enabled.
- [ ] The computer is authorized on the device.
- [ ] `.\.android-sdk\platform-tools\adb.exe devices` lists the Pixel 8 as `device`.
- [ ] Debug APK installs with:

```powershell
.\.android-sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
```

## Pixel 8 Acceptance

- [ ] App launches and Home states that Magnetic Camera is a point-sensor reconstruction, not an X-ray, wall-wire detector, safety instrument, medical device, or professional diagnostic tool.
- [ ] Home shows magnetometer and camera status without crashing.
- [ ] Live Meter displays changing X/Y/Z and magnitude values.
- [ ] Live Meter displays observed sample rate and sensor accuracy.
- [ ] Baseline calibration completes after holding the phone still away from magnets, chargers, speakers, laptop hinges, metal tables, and magnetic cases.
- [ ] Delta values visibly change near a small magnet.
- [ ] A 5x5 scan can be completed without crash.
- [ ] A 7x7 scan can be completed without crash.
- [ ] Redo and skip controls work during a scan.
- [ ] Leaving mid-scan creates a resumable draft.
- [ ] Discarding a draft removes it from Home.
- [ ] Heatmap generation completes after scan capture.
- [ ] Scientific and monochrome palettes both render.
- [ ] Auto, fixed-scale, and absolute-field normalization modes update the result.
- [ ] A reference photo can be captured.
- [ ] A reference photo can be imported.
- [ ] Scan area corners can be adjusted over the reference photo.
- [ ] Heatmap opacity can be changed over the photo.
- [ ] Heatmap-only PNG is saved.
- [ ] Photo-overlay PNG is saved when a reference photo exists.
- [ ] Saved session appears in Gallery after closing and reopening the app.
- [ ] Session Detail shows metadata, image preview, raw grid values, and export buttons.
- [ ] JSON export contains every captured cell with mean, median, min, max, standard deviation, delta, timestamp, sample count, and accuracy fields.
- [ ] CSV export contains every captured cell with the same full cell statistics.
- [ ] Exported PNG/JSON/CSV files can be shared through Android's share sheet.
- [ ] If camera permission is denied, grid scanning still works without a reference photo.
- [ ] If sensor accuracy is unreliable, the app shows a non-blocking warning.
- [ ] If no magnetometer is exposed, the app shows the no-sensor explanation and does not crash.

## Suggested Physical Scenarios

- [ ] Ambient room field baseline.
- [ ] Small fridge magnet under paper.
- [ ] Speaker or headphone driver.
- [ ] Laptop hinge area.
- [ ] Electric toothbrush or small motor.
- [ ] Metal table as negative test.
- [ ] Phone case on/off comparison.
