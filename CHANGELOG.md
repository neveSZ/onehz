# Changelog

All notable changes to OneHZ are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project adheres to
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Optional GitHub update check, enabled only after explicit consent.
- Automatic checks happen only when the app opens, respecting a user-selected minimum
  interval (24 hours, 3 days, 7 days, or 30 days).
- Update channel setting: stable only, or stable plus pre-releases.
- Dismissible Home banner and a Settings status card with a manual "Check now" action.
- SemVer comparison with pre-release precedence, ETag caching, and release-link
  validation.
- Manual GitHub Releases links in Settings and About.

### Changed

- Added the `INTERNET` permission, used only by the opt-in update checker.
- Updated privacy documentation and README permissions.

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

[1.0.0]: https://github.com/neveSZ/onehz/releases/tag/v1.0.0
