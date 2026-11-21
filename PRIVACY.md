# Privacy Policy

**Last updated:** 2026-11-21

OneHZ is a local-only Android app. This document explains what the app does with your
data.

## Summary

- OneHZ **does not request the `INTERNET` permission** in version 1.0.0.
- There is **no analytics, advertising, telemetry, crash reporting, or account system**.
- Configuration and diagnostics stay on your device.

## Data stored on the device

OneHZ stores its settings in Android `SharedPreferences`:

- profiles (normal, ECO, AOD, and per-app);
- feature toggles and theme/language preferences;
- session markers used to restore system settings after a crash;
- a small backup of Samsung battery-saver settings that OneHZ changed.

This data is private to the app. The app has `allowBackup="false"` and excludes its
preferences from cloud backup and device transfer.

## Diagnostics

The diagnostics screen keeps up to 500 log entries **in memory only**. Nothing is
uploaded or written to storage. You can copy the report manually and choose whether to
share it in a bug report. Remove any personal information before posting.

## Permissions

| Permission | Purpose |
|---|---|
| `QUERY_ALL_PACKAGES` | List installed apps so you can assign per-app profiles. |
| `RECEIVE_BOOT_COMPLETED` | Reapply the profile after a reboot. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Keep the optional engine service alive. |
| `POST_NOTIFICATIONS` | Show the persistent service notification. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Optional battery exemption requested by you. |
| Shizuku permission | Perform privileged display and settings operations. |

OneHZ does not use AccessibilityService, overlay, storage, contacts, location, camera,
microphone, or network permissions.

## External links

The About screen links to GitHub, Shizuku, Adaptive Hz, and LivePix. Opening a link hands
the URL to your browser; OneHZ does not send data with it. LivePix has its own privacy
policy.

## Future changes

A future version may add an optional update checker using the GitHub Releases API. That
change will require the `INTERNET` permission, will be opt-in, will not include
telemetry, and will be documented here before release.

## Contact

For privacy questions, open an issue at
<https://github.com/neveSZ/onehz/issues>. Do not include secrets or personal data.
