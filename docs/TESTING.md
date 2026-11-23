# Testing

## Automated checks

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleRelease
```

Unit tests cover the pure logic:

- `FrequencyProfile` serialization, validation, defaults;
- `ProfileDefaults` for two-rate and multi-rate panels;
- `ProfileCodec` serialization and frequency adaptation;
- `BackupCodec` round trip, schema/app validation, forced packages and Sleep settings
  (including a missing Sleep block);
- `VideoSignals` parsers and rule decisions (captured real-device fixtures);
- `VideoConfig` encoding, validation, and defaults;
- `MtTouchParser` MT-B edges;
- `TouchDeviceSelector` device filtering;
- `AodBrightnessMath`;
- `PolicyResolver` precedence, automatic Economy and profile video;
- `DozeState`, `DozeSchedule`, `DozeDecision`, `DozeWhitelist` and `SleepLedger` (sleep
  decisions, cross-midnight windows, exception-list parsing, 24-hour clipping, sleep sessions);
- `SemanticVersion` and pre-release precedence;
- `ReleaseSelector` channel filtering and URL validation;
- update interval gating.

CI runs unit tests and lint on every push/PR.

## Performance baseline

Recorded before the 1.3 work on the POCO X7 Pro (release build, screen on, app enabled and
connected to Shizuku). Use the same device and a release build for comparisons.

| Metric | 1.2.2-beta.1 | 1.3.0 |
|---|---|---|
| Cold start, median of 5 (`am start -W -S`) | 258 ms | 257 ms |
| Idle CPU after 45 s (app processes) | 0% | 0% |
| Wake locks held by the app | 0 | 0 |
| Periodic alarms scheduled by the app | 0 | 0 |
| Scroll: total frames / janky | 262 / 0% | 212 / 0.47% |
| Scroll frame time p50 / p90 / p99 | 7 / 8 / 12 ms | 7 / 8 / 14 ms |
| APK size | 3.81 MiB | 3.91 MiB |

How to measure:

```powershell
adb shell am start -W -S -n io.github.nevesz.onehz/.ui.MainActivity   # repeat 5x, use median
adb shell dumpsys cpuinfo | Select-String onehz
adb shell dumpsys power | Select-String "Wake Locks|mWakefulness"
adb shell dumpsys alarm | Select-String io.github.nevesz
adb shell dumpsys gfxinfo io.github.nevesz.onehz reset
adb shell input swipe 610 2000 610 900 220                      # repeat a few times
adb shell dumpsys gfxinfo io.github.nevesz.onehz
```

Budget for changes: start-up +5% at most, no new wake lock, no new periodic alarm (except
the pause expiry), and no polling that shows up in idle CPU.

## Manual test checklist (release)

Test on the target device with a clean install of the signed release APK.

> `adb shell input tap` injects events at the InputManager level, not at the kernel
> `evdev` level. OneHZ reads raw events with `getevent`, so injected taps do **not**
> exercise the touch monitor. Use a physical touch (or `sendevent` with the correct input
> device) for touch tests.

### Install and setup

- [ ] Install the signed release from Shizu Store, GitHub Releases or Obtainium; the
  package and signing key must match.
- [ ] Upgrade from the previous stable/beta and confirm the DataStore migration keeps
  profiles, app assignments, AOD, Sleep and video settings.
- [ ] Uninstall any debug build first (`adb uninstall io.github.nevesz.onehz`).
- [ ] Install the release APK.
- [ ] Vendor/display compatibility shows OK.
- [ ] Shizuku permission is requested and granted.
- [ ] Battery exemption is optional and works.
- [ ] Doze exception toggle works.
- [ ] Notification permission and persistent notification work.

### Basic engine

- [ ] Enable OneHZ.
- [ ] Touch the screen and confirm the first tier is applied.
- [ ] Confirm the staircase descends after each hold.
- [ ] Confirm the final tier stays at rest.
- [ ] Put a finger on the screen while a hold expires; the step is deferred.
- [ ] Turn the master switch off; the system adaptive default returns.
- [ ] Select Economy, Balanced, Performance and a custom profile; each applies.

### Notification

- [ ] With the pin off (default), swipe the OneHZ notification away; it disappears and does
      not return on rate/profile changes, but returns after opening OneHZ.
- [ ] Open Settings → Advanced → **Pin notification**; the warning explains the Android 14+
      swipe and that the service and battery behavior do not change.
- [ ] Confirm; the notification returns and the row says it stays visible.
- [ ] Swipe it away; it returns within about two seconds. Pause/resume and Turn off still
      work.
- [ ] Turn the pin off; a swipe keeps it hidden until OneHZ is opened.
- [ ] Repeat with “Clear all” and on the lock screen; record any OEM difference.
- [ ] Confirm no new wake lock or periodic alarm appears in `dumpsys power`/`dumpsys alarm`.

### Profiles and battery saver

- [ ] Edit a built-in profile; it is marked as modified.
- [ ] Restore a built-in profile; it returns to the device defaults.
- [ ] Create, rename, edit and delete a custom profile.
- [ ] Delete a custom profile used by an app; the app moves to Balanced.
- [ ] Turn on system battery saver; Economy is applied and the card shows it.
- [ ] Turn battery saver off; the selected profile returns.
- [ ] Add an app, turn on "Keep this app's profile during battery saver" in the add sheet;
      its profile stays while battery saver is on and the row shows "Keeps its profile".
- [ ] Add an app with the switch off; battery saver on applies Economy.
- [ ] Edit an assignment and toggle the switch; the row badge and the applied layer update.
- [ ] Select Economy as the app profile; the switch is disabled in the add and edit sheets.
- [ ] With Automatic Economy off, the hint is hidden and the app profile applies.
- [ ] Confirm Samsung battery-saver settings are restored after turning OneHZ off.
- [ ] On Xiaomi, confirm `secure/miui_refresh_rate` is restored after turning OneHZ off.

### AOD

- [ ] Enable AOD; lock the screen and confirm the panel minimum is applied.
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
- [ ] With "Doze while charging" on (default), plug the device in while asleep; Doze
      stays active or enters idle.
- [ ] Turn "Doze while charging" off; plug the device in while asleep; it leaves idle.
- [ ] Unplug the charger while the screen is still off; Doze is forced again without a new
      screen cycle.
- [ ] Toggle "Doze while charging" while the screen is off; the decision updates
      immediately.
- [ ] Play background music, turn the screen off, and confirm the music guard skips forcing.
- [ ] Turn on **Doze with music** while music is playing and the screen is off; Doze is
      forced without waiting for another screen-off.
- [ ] Stop the music with the option off; Doze is forced without touching the screen.
- [ ] Start music while Doze is active and the option is off; the device leaves idle
      (`mState=INACTIVE`, not just `mForceIdle=false`) and the card shows the music state.
- [ ] Check the diagnostics timings: the idle command takes a few tens of milliseconds and
      confirmation appears at the first retry on a responsive ROM.
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

- [ ] Assign an existing profile to a test app.
- [ ] Open the app from the launcher, recents, and a notification without touching inside
      it; the assigned profile applies on the foreground change.
- [ ] Switch to an app without an assignment; the selected profile returns.
- [ ] With the task-stack listener active, confirm a physical touch does not run an extra
      foreground lookup.
- [ ] Force the listener to be unavailable (or use a platform that rejects it); open the
      app and confirm the touch-up fallback applies the assigned profile.
- [ ] Use a fixed single-step app profile with video disabled; switch away and confirm the
      selected profile returns (the touch monitor stays active).
- [ ] Edit the assigned profile; the app uses the updated staircase.
- [ ] Create a custom profile from the app sheet and confirm it is assigned.
- [ ] Remove the assignment; the active profile returns.
- [ ] Confirm no foreground listener or polling runs when no assignments exist.

### Backup and restore

- [ ] Export a configuration with built-in, custom and per-app assignments.
- [ ] Change several settings, then import the file; the summary is shown.
- [ ] Confirm profiles, active profile, video, AOD, Sleep and assignments are restored.
- [ ] Confirm the system Doze exception list is unchanged by the import.
- [ ] Import a file with unsupported rates; confirm the adapted count is reported.
- [ ] Import a file with a missing profile reference; the app moves to Balanced.
- [ ] Confirm theme and language are unchanged.

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
- [ ] Start PiP; the floor applies while the PiP window is visible and clears after it
      closes.

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

- [ ] Install the new beta over the previous installed version; settings and profiles are
      migrated (Normal → Balanced, ECO → Economy, new Performance profile).
- [ ] The stable update channel never offers the beta.
- [ ] The stable + pre-releases channel offers the beta and labels it as a pre-release.
- [ ] On Xiaomi, the profile editor warns when a step uses 30 Hz.

### AOD and Sleep UI

- [ ] AOD detail shows the rate switch on both a Samsung and a HyperOS device.
- [ ] On HyperOS, AOD brightness is a disabled switch with the "not adjustable" text.
- [ ] Turning the AOD master off on Home pauses rate and brightness but keeps both rows
      visible and editable.
- [ ] Sleep detail with Sleep off shows the compact note, the "How it works" card and the
      behavior options; "Test now" and pause require Sleep on.
- [ ] The warning inside "How it works" can be dismissed with "Got it" and does not reopen
      after reopening the screen.
- [ ] Active/paused/failure status appears as a single line, without a status card.

### Video settings

- [ ] The video card shows the minimum-rate picker and a "Video detection" row with the
      active rule count.
- [ ] The detection sheet opens, the four rules persist, and the global forced-app list
      opens from the same sheet.
- [ ] With zero positive rules, the warning is visible in the card and in the sheet.

### Screenshots and Baseline Profile

- [ ] Regenerate the five store screenshots per locale from the final build and normalize
      them to 1170×2340.
- [ ] Regenerate `app/src/release/generated/baselineProfiles/` on a physical device and
      rebuild the release.

### Release artifacts

- [ ] `versionName` and `versionCode` match `version.properties`.
- [ ] APK signed (v3; minSdk 31).
- [ ] Certificate SHA-256 matches the documented fingerprint.
- [ ] SHA-256 file matches the APK.
- [ ] APK size is reasonable (~3–4 MB).

### Picture-in-picture

- [ ] Play a video in YouTube or VLC, press Home and confirm the PiP window appears.
- [ ] With video mode on, Home shows the video floor and the applied rate follows it.
- [ ] Close the PiP window; the video floor is released on the next probe.
- [ ] Repeat with the app in the foreground and no PiP; behavior is unchanged.

### 1.3.0 release gate

These checks require the user to lock and unlock the device; they are not automated from
ADB. Run the non-interactive parts on both devices in parallel and batch the lock/unlock
steps.

Upgrade and feature checks:

- [ ] Install 1.3.0 over 1.2.2-beta.2 on the Samsung; profiles, assignments, AOD, Sleep and
      activation rules are kept, and Shizuku reconnects.
- [ ] Add an app with battery saver on: the add sheet shows the exception switch, the app
      keeps its profile, and the row badge reads **Keeps its profile**.
- [ ] Notification pin off/on (see Notification).
- [ ] Add the Quick Settings tile on the Samsung; the system prompt appears if it is not
      already on the grid, and the tile turns OneHZ on and off.
- [ ] PiP with YouTube on both devices; the video floor applies while the window is visible
      and clears after it closes.
- [ ] With the stable channel, the update checker reports the installed 1.3.0 as up to date;
      with stable + pre-releases, 1.2.2-beta.2 is not offered as an update over 1.3.0.
- [ ] Export a backup; the JSON contains `rules` and schema 2; import it and confirm the
      configuration is restored.
- [ ] Language and theme: the notification row and warning render in EN/PT/ES and in
      light/dark/AMOLED.

POCO (Android 16):

- [ ] Enable OneHZ, turn the AOD rate on, lock and unlock; the AOD uses the panel minimum
      and the active profile returns after unlock.
- [ ] Pause for one hour, lock and unlock; the pause survives, the system values stay
      restored, and Resume now works.
- [ ] Set a low-battery rule, lower the battery with `dumpsys battery set level`, and
      confirm the guard enters and leaves with hysteresis; reset with
      `dumpsys battery reset`.
- [ ] After a screen-off period, Usage counts AOD and uncontrolled time separately.
- [ ] Sleep/Doze enters and leaves correctly after the AOD reapply change.
- [ ] Run the Doze maintenance gate below and confirm the AOD rate and the counters.

Samsung (Android 17):

- [ ] AOD brightness on, base 10%, night schedule 22:00-07:00 at 15%.
- [ ] Lock inside the window: the debug key uses the scheduled value inside the band.
- [ ] Lock outside the window: the key uses the base value.
- [ ] Restore everything: the key is removed, the rate keys return to the system default
      and OneHZ turns off.
- [ ] Re-enable and confirm the rate and AOD still work after the restore.
- [ ] Run the Doze maintenance gate below and confirm the AOD rate keys.

### Doze maintenance gate

Run with Sleep on and the screen off. The commands are deterministic and do not replace
the user's lock/unlock step. `dumpsys deviceidle step` is available on Android 16 and 17.

POCO (deep idle):

- [ ] Lock the screen; confirm `dumpsys deviceidle get deep` is `IDLE` and
      `dumpsys deviceidle get force` is `true`. `dumpsys power` shows no app wake lock.
- [ ] Run `dumpsys deviceidle step deep`; confirm `IDLE_MAINTENANCE` with `get deep`.
- [ ] Run `dumpsys deviceidle step deep` again; confirm `IDLE` and `get force` still
      `true`.
- [ ] Repeat the cycle twice.
- [ ] Confirm the Diagnostics log shows at most one reapply per cycle and no repeated
      force/AOD writes.
- [ ] Confirm the Sleep session remains one session, the screen-off/Doze totals include
      the maintenance interval, and the maintenance count increments when the platform
      exposes the transition.
- [ ] Confirm the HyperOS AOD rate key (`miui_refresh_rate` or `user_refresh_rate`) is
      still the panel minimum after the cycle.
- [ ] Unlock; confirm `get force` is `false`, `get deep` is `ACTIVE`, the session closes
      and the profile returns.

Samsung (AOD rate):

- [ ] While the AOD is active, record
      `settings get system peak_refresh_rate`, `settings get system min_refresh_rate` and
      `settings get secure refresh_rate_mode`.
- [ ] Repeat the `IDLE → IDLE_MAINTENANCE → IDLE` cycle with Sleep on.
- [ ] Confirm the rate keys return to the panel minimum after the cycle and the Diagnostics
      log shows no write loop.
- [ ] Turn Sleep off and the screen on; confirm the AOD rate is restored and no forced
      state remains.

## Device matrix

| Device | Android | Software | Build | Status |
|---|---|---|---|---|
| SM-S936B (S25+) | 17 / API 37 | One UI 9.0 | `S936BXXUDDZJB` | Tested |
| POCO X7 Pro (`2412DPC0AG`, `rodin`) | 16 / API 36 | HyperOS 3.0 (`OS3.0.304.0.WOJMIXM`) | `BP2A.250605.031.A3` | Tested |

Other devices are **untested**. Report results with the bug report template.
