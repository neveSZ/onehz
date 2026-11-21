# Testing

## Automated checks

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleRelease
```

Unit tests cover the pure logic:

- `FrequencyProfile` serialization, validation, defaults;
- `VideoSignals` parsers (captured real-device fixtures);
- `MtTouchParser` MT-B edges;
- `TouchDeviceSelector` device filtering;
- `AodBrightnessMath`;
- `PolicyResolver` precedence.

CI runs unit tests and lint on every push/PR.

## Manual test checklist (release)

Test on the target device with a clean install of the signed release APK.

> `adb shell input tap` injects events at the InputManager level, not at the kernel
> `evdev` level. OneHZ reads raw events with `getevent`, so injected taps do **not**
> exercise the touch monitor. Use a physical touch (or `sendevent` with the correct input
> device) for touch tests.

### Install and setup

- [ ] Uninstall any debug build first (`adb uninstall io.github.nevesz.onehz`).
- [ ] Install the release APK.
- [ ] LTPO compatibility shows OK.
- [ ] Shizuku permission is requested and granted.
- [ ] Battery exemption is optional and works.
- [ ] Doze whitelist toggle works.
- [ ] Notification permission and persistent notification work.

### Basic engine

- [ ] Enable OneHZ.
- [ ] Touch the screen and confirm the first tier is applied.
- [ ] Confirm the staircase descends after each hold.
- [ ] Confirm the final tier stays at rest.
- [ ] Put a finger on the screen while a hold expires; the step is deferred.
- [ ] Turn the master switch off; the system adaptive default returns.
- [ ] Disable the normal profile; control stops with the expected message.

### ECO

- [ ] Turn on system battery saver; the ECO profile is applied.
- [ ] Turn battery saver off; the normal profile returns.
- [ ] Mark an app “Ignore ECO”; its profile stays while battery saver is on.
- [ ] Disable the ECO profile; control stops during battery saver.
- [ ] Confirm Samsung ECO settings are restored after turning OneHZ off.

### AOD

- [ ] Enable AOD frequency; lock the screen and confirm the AOD rate.
- [ ] Enable AOD brightness; lock and check `adb shell settings get system debug_aod_brightness`.
- [ ] Change screen brightness while awake; the target updates.
- [ ] Use “Restore system brightness”; the key is removed.
- [ ] Confirm no timers/touch monitor run with the screen off.
- [ ] Confirm the device still enters Doze.

### Per-app profiles

- [ ] Add a custom profile for a test app.
- [ ] Open the app; the profile is applied on touch/resume.
- [ ] Remove the profile; the normal profile returns.
- [ ] Confirm no foreground polling when no app profiles exist.

### Video mode

- [ ] Play a video in the foreground; the floor is applied.
- [ ] Pause; the floor is released.
- [ ] Switch apps; the old app's video state is cleared.
- [ ] Play audio only; it does not trigger the floor.
- [ ] Open the camera; it does not trigger the floor.
- [ ] Document that PiP is not detected.

### Wake, screen, and reboot

- [ ] Screen off → AOD profile.
- [ ] Screen on/unlock → wake pulse and staircase.
- [ ] Reboot → OneHZ reconnects when Shizuku is available.
- [ ] Kill Shizuku → OneHZ fails closed and warns.
- [ ] Reconnect Shizuku → OneHZ resumes.

### UI

- [ ] Switch PT-BR, English, and Spanish.
- [ ] Switch system/light/dark/AMOLED themes.
- [ ] Diagnostics: filters, pause, clear, copy report.
- [ ] Test first/resting/full cycle buttons.
- [ ] About: links, donate link, third-party licenses, translation note.

### Release artifacts

- [ ] `versionName=1.0.0`, `versionCode=10000`.
- [ ] APK signed (v3; minSdk 31).
- [ ] Certificate SHA-256 matches the documented fingerprint.
- [ ] SHA-256 file matches the APK.
- [ ] APK size is reasonable (~3–4 MB).

## Device matrix

| Device | Android | One UI | Build | Status |
|---|---|---|---|---|
| SM-S936B (S25+) | 17 / API 37 | 9.0 | `S936BXXUDDZJB` | Tested |

Other devices are **untested**. Report results with the bug report template.
