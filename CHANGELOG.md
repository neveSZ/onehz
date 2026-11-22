# Changelog

All notable changes to OneHZ are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project adheres to
[Semantic Versioning](https://semver.org/).

## [Unreleased]

Beta feature set for reusable profiles. No release tag or publication yet.

### Added

- Economy, Balanced and Performance profiles generated from the device panel.
- Manual active-profile selection with automatic Economy while the system battery saver
  is on.
- Custom profiles with create, rename, edit and delete actions.
- “Restore default” for the three built-in profiles.
- Per-app assignments that reference a saved profile instead of copying its staircase.
- Per-profile video settings: enabled flag, minimum refresh rate and detection rules.
- AOD now always uses the lowest rate exposed by the panel.
- Backup and restore through a versioned JSON file using the Storage Access Framework.
- Import adapts unsupported refresh rates to the current panel and reports the result.
- Migration from the legacy Normal/ECO profiles and copied per-app staircases.

### Changed

- The Home profiles section now shows the active profile with a quick switcher and a
  separate management screen.
- Per-profile enable switches were replaced by one master switch and an active-profile
  selection.
- The AOD frequency picker was removed; the minimum is shown instead.
- The default video rules enable MediaSession, paused-session blocking and the video codec
  rule, leaving the media-audio rule off.

## [1.1.0-beta.1] - 2026-11-21

First beta after 1.0.1. Adds Xiaomi/Redmi/POCO support, removes the LTPO requirement,
and makes video detection configurable.

### Added

- Xiaomi/Redmi/POCO support through `secure/miui_refresh_rate` (HyperOS 2/3) and
  `secure/user_refresh_rate` (HyperOS 1 fallback).
- Capability-based compatibility gate: Samsung or Xiaomi/Redmi/POCO with at least two
  refresh rates.
- Exact snapshot/restore of the HyperOS rate key on release and after a crash.
- HyperOS diagnostics route and observed-rate reporting.
- Default profiles: normal **device maximum → 60 Hz → minimum** (2 s / 5 s) and ECO
  **60 Hz → minimum** (2 s).
- Profile editor warning on Xiaomi when a step uses 30 Hz: the panel can reduce the
  brightness and OneHZ cannot compensate.
- Diagnostics now report screen brightness/mode, battery-saver state, and the active
  rate route.
- Configurable video detection rules: MediaSession, paused-session veto, video decoder,
  and media audio can each be enabled or disabled from the new detection settings sheet.
- Forced app list: packages that are always treated as video while in the foreground.
- The video configuration travels to the Shizuku UserService over the binder, with
  validated package names and a 64-app cap.
- A configuration change re-probes video immediately instead of waiting for a touch.

### Changed

- LTPO is no longer required; a multi-rate panel is enough.
- The AOSP/generic backend was removed. Unsupported manufacturers are blocked and
  warned instead of receiving experimental writes.
- AOD brightness is Samsung-only and is hidden on Xiaomi.
- `BootReceiver` and the keep-alive service respect the strict gate.
- The Home ECO badge follows the system battery-saver broadcast while the app is open,
  instead of waiting for the next resume.
- Version 1.1.0-beta.1 (`versionCode 1010001`).

### Known limitations

- On the tested POCO X7 Pro, 30 Hz is dimmer at the panel level. System brightness,
  framebuffer, and backlight are identical to 60/120 Hz, and the vendor knobs reachable
  through Shizuku do not change it. See [COMPATIBILITY](docs/COMPATIBILITY.md#30-hz-brightness-on-hyperos).

## [1.0.1] - 2026-11-21

### Added

- Optional GitHub update check, enabled only after explicit consent.
- Automatic checks happen only when the app opens, respecting a user-selected minimum
  interval (24 hours, 3 days, 7 days, or 30 days).
- Update channel setting: stable only, or stable plus pre-releases.
- Dismissible Home banner and a Settings status card with a manual "Check now" action.
- SemVer comparison with pre-release support, ETag caching, and release-link
  validation.
- Manual GitHub Releases links in Settings and About.
- GitHub Sponsors and LivePix support links, with clear regional labels in the app and
  README.
- Standardized the About → Credits section with consistent titles and subtitles.

### Changed

- Added the `INTERNET` permission, used only by the opt-in update checker.
- Updated privacy documentation and README permissions.
- Version 1.0.1 (`versionCode 1000200`).

## [1.0.0] - 2026-11-21

First public stable release.

### Added

- Refresh-rate staircase with 1–5 tiers per profile.
- Independent normal, ECO (battery saver), and AOD profiles.
- AOD brightness control with a safety band and system restore action.
- Per-app profiles with optional “Ignore ECO” exception.
- Video mode floor based on MediaSession, Codec2, and AudioService signals.
- Wake/unlock pulse.
- Device-aware default profiles from Samsung's panel rate list.
- Guided setup for LTPO compatibility, Shizuku, battery optimization, Doze, and
  notifications.
- Optional foreground service without wake locks.
- In-memory diagnostics with log filters, test actions, and report copy.
- System, light, dark, and AMOLED themes.
- Portuguese (Brazil), English, and Spanish translations.
- GitHub Actions CI and signed release workflow.
- Bilingual README, MIT license translation, third-party notices, credits, and project
  documentation.

### Changed

- Battery-saver mode is named **ECO** throughout the app and codebase.
- All user-facing text moved to string resources; diagnostics and code use English.
- Release version is now `1.0.0` (`versionCode 10000`).
- Release builds keep R8 and resource shrinking; APK is about 3.1 MB.
- Signing supports environment variables for CI and keeps the keystore outside the repo.
- Documented that OneHZ includes portions derived from Adaptive Hz (MIT), added the
  `NOTICE` file, and completed third-party notices (AndroidX, Compose, Kotlin,
  kotlinx.coroutines).

### Fixed

- Foreground-app lookup no longer risks truncating `dumpsys activity` output.
- Samsung battery-saver refresh-rate settings are snapshotted and restored.
- Foreground service type is guarded for Android 12–13.
- Boot receiver is no longer exported.
- Backup/device-transfer rules were added.
- Removed legacy preference migration and unused token-mode code.

### Known limitations

- Tested only on Samsung Galaxy S25+ (`SM-S936B`), Android 17 / One UI 9.0.
- Samsung LTPO devices only.
- Shizuku must be running; it may need to be restarted after a reboot.
- PiP video is not detected yet.
- Other devices, brands, and WQHD+ are untested.

[1.1.0-beta.1]: https://github.com/neveSZ/onehz/releases/tag/v1.1.0-beta.1
[1.0.1]: https://github.com/neveSZ/onehz/releases/tag/v1.0.1
[1.0.0]: https://github.com/neveSZ/onehz/releases/tag/v1.0.0
