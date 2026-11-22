# Testing

## Automated checks

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleRelease
```

Unit tests cover the pure logic:

- `FrequencyProfile` serialization, validation, defaults;
- `VideoSignals` parsers and rule decisions (captured real-device fixtures);
- `VideoConfig` encoding, validation, and defaults;
- `MtTouchParser` MT-B edges;
- `TouchDeviceSelector` device filtering;
- `AodBrightnessMath`;
- `PolicyResolver` precedence;
- `DozeState`, `DozeSchedule`, `DozeDecision`, `DozeWhitelist` and `SleepLedger` (sleep
  decisions, cross-midnight windows, whitelist parsing, 24-hour clipping, rest sessions);
- `SemanticVersion` and pre-release precedence;
- `ReleaseSelector` channel filtering and URL validation;
- update interval gating.

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
- [ ] Vendor/display compatibility shows OK.
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
- [ ] On Xiaomi, confirm `secure/miui_refresh_rate` is restored after turning OneHZ off.

### AOD

- [ ] Enable AOD frequency; lock the screen and confirm the AOD rate.
- [ ] Enable AOD brightness (Samsung only); lock and check
      `adb shell settings get system debug_aod_brightness`.
- [ ] Change screen brightness while awake; the target updates.
- [ ] Use “Restore system brightness”; the key is removed.
- [ ] Confirm no timers/touch monitor run with the screen off.
- [ ] Confirm the device still enters Doze with Sleep off.

### Sleep (forced Doze)

- [ ] Enable Sleep; the card shows the current state and the 24-hour block.
- [ ] Screen off → `adb shell dumpsys deviceidle | Select-String mState` shows `IDLE`.
- [ ] Screen on → `unforce`; the state returns to `ACTIVE` and the wake pulse is not delayed.
- [ ] Turn the feature off while forced; the device leaves idle.
- [ ] Pause for 1 hour; the next screen-off does not force and the card shows the deadline.
- [ ] Enable the time window with a range that crosses midnight; forcing only happens inside it.
- [ ] Enable the charger guard and plug the device in while asleep; it leaves idle.
- [ ] Play background music, turn the screen off, and confirm the music guard skips forcing.
- [ ] Set light mode; confirm the state still becomes `IDLE` (or report the ROM result).
- [ ] Add an app to **Apps that may wake the device**; confirm it appears in
      `adb shell dumpsys deviceidle whitelist`, then remove it and confirm it is gone.
- [ ] Kill and restart the app while asleep; no forced state is left behind.
- [ ] Reboot while forced; the boot path clears the flag.
- [ ] Check the 24-hour totals and the last rest after a full night (or a long screen-off).
- [ ] A quick screen-on shorter than two minutes does not split the rest session.
- [ ] A charging session shows `—` instead of a battery rate.
- [ ] With no history, the card says there are no measurements instead of zeroes.
- [ ] `Test now` forces for ten seconds and leaves idle again.

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
- [ ] Disable each rule in **Configure detection**; the verdict changes as expected.
- [ ] Add an app to the forced list; the floor applies in that app even when paused.
- [ ] Change the rules or the forced list while a video plays; the floor updates without
      a new touch.
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

### Updates

- [ ] With the option off, confirm no request is made (fresh install).
- [ ] Enable it and accept the consent dialog; the first check runs immediately.
- [ ] Confirm the status reports the installed version as up to date.
- [ ] Change the interval; confirm the next automatic check waits for it.
- [ ] Reopen the app before the interval; no new request is made.
- [ ] Switch to the pre-release channel; confirm the check uses the release list and
      clearly labels pre-releases.
- [ ] Use “Check now”; it bypasses the interval and restarts it.
- [ ] Publish/test a newer release; the Home banner appears with “Ver release”.
- [ ] “Agora não” hides the banner for that version; Settings still reports it.
- [ ] Confirm only `https://github.com/neveSZ/onehz/releases/...` opens.
- [ ] Disable the option; no further checks are made.
- [ ] Airplane mode; the automatic check fails silently and the manual check reports a
      network error.
- [ ] Confirm no background check runs with the app closed.

### Beta and upgrade

- [ ] Install 1.1.0-beta.1 over 1.0.1; settings and profiles are kept.
- [ ] The stable update channel never offers the beta.
- [ ] The stable + pre-releases channel offers the beta and labels it as a pre-release.
- [ ] On Xiaomi, the profile editor warns when a step uses 30 Hz.

### Release artifacts

- [ ] `versionName=1.1.0-beta.1`, `versionCode=1010001`.
- [ ] APK signed (v3; minSdk 31).
- [ ] Certificate SHA-256 matches the documented fingerprint.
- [ ] SHA-256 file matches the APK.
- [ ] APK size is reasonable (~3–4 MB).

## Device matrix

| Device | Android | Software | Build | Status |
|---|---|---|---|---|
| SM-S936B (S25+) | 17 / API 37 | One UI 9.0 | `S936BXXUDDZJB` | Tested |
| POCO X7 Pro (`2412DPC0AG`, `rodin`) | 16 / API 36 | HyperOS 3.0 (`OS3.0.304.0.WOJMIXM`) | `BP2A.250605.031.A3` | Tested |

Other devices are **untested**. Report results with the bug report template.
