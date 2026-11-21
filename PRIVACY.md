# Privacy Policy

**Last updated:** 2026-11-21

OneHZ is a local-first Android app. This document explains what the app does with your
data.

## Summary

- OneHZ requests the `INTERNET` permission **only for the optional update checker**,
  which is **off by default**.
- There is **no analytics, advertising, telemetry, crash reporting, or account system**.
- Configuration and diagnostics stay on your device.

## Data stored on the device

OneHZ stores its settings in Android `SharedPreferences`:

- profiles (normal, ECO, AOD, and per-app);
- feature toggles and theme/language preferences;
- session markers used to restore system settings after a crash;
- a small backup of Samsung battery-saver settings that OneHZ changed;
- update-check settings (enabled, interval, channel, last attempt) and the last release
  metadata returned by GitHub.

This data is private to the app. The app has `allowBackup="false"` and excludes its
preferences from cloud backup and device transfer.

## Diagnostics

The diagnostics screen keeps up to 500 log entries **in memory only**. Nothing is
uploaded or written to storage. You can copy the report manually and choose whether to
share it in a bug report. Remove any personal information before posting.

## Update checks (opt-in)

The update checker is disabled until you enable it in **Settings → Updates** and accept
the explanation shown there. Android grants the `INTERNET` permission at install time;
the in-app option controls whether OneHZ uses it.

When enabled, the app contacts the public GitHub API **only when you open OneHZ**, at
most once per the chosen interval:

- stable channel: `https://api.github.com/repos/neveSZ/onehz/releases/latest`;
- stable plus pre-releases: `https://api.github.com/repos/neveSZ/onehz/releases`
  (the latest releases are compared locally).

The request includes the app version in the `User-Agent` header. Like any HTTPS request,
GitHub receives your IP address and that header. OneHZ does **not** send an account,
token, install identifier, device identifier, profile data, or telemetry. Release notes
are not stored; only the tag, URL, name, and pre-release flag of the newest release are
cached locally.

The checker never runs in the background, on a schedule, or from a service. If you
disable it, OneHZ stops making update requests. You can also open the GitHub Releases
page manually from Settings and About; that only hands the URL to your browser.

The channel can be set to stable only or to stable plus pre-releases. Pre-releases may be
less stable and are clearly marked in the app.

## Permissions

| Permission | Purpose |
|---|---|
| `QUERY_ALL_PACKAGES` | List installed apps so you can assign per-app profiles. |
| `RECEIVE_BOOT_COMPLETED` | Reapply the profile after a reboot. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Keep the optional engine service alive. |
| `POST_NOTIFICATIONS` | Show the persistent service notification. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Optional battery exemption requested by you. |
| `INTERNET` | Optional GitHub update check, off by default. |
| Shizuku permission | Perform privileged display and settings operations. |

OneHZ does not use AccessibilityService, overlay, storage, contacts, location, camera, or
microphone. It does not use the network for anything other than the opt-in update check.

## External links

The About and Updates screens link to GitHub, GitHub Releases, GitHub Sponsors, LivePix,
Shizuku, and Adaptive Hz. Opening a link hands the URL to your browser; OneHZ does not
send data with it. Those sites have their own privacy policies.

## Changes

This policy will be updated before any new network feature is added.

## Contact

For privacy questions, open an issue at
<https://github.com/neveSZ/onehz/issues>. Do not include secrets or personal data.
