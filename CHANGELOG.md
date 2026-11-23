# Changelog

All notable changes to OneHZ are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project adheres to
[Semantic Versioning](https://semver.org/).

## [1.3.0] - 2026-11-23

Local release candidate. The stable tag follows the screen-off and Samsung validation gate.

### Added

- One-hour master pause next to the master switch, with a deadline, a resume action and
  automatic resume. Pausing restores the captured system values.
- Quick Settings tile for enable, pause and resume, with a Shizuku-unavailable state.
- Foreground notification with the active profile and rate, pause/resume and disable.
- Launcher shortcuts for pause, Economy and diagnostics.
- System changes screen with the keys OneHZ may write, current and original values,
  restore-all, an external-change warning and a sanitized panel report export.
- Usage screen with time per rate, Always On Display and uncontrolled time, plus the
  profile split, for today and the last seven days.
- Activation rules: low-battery guard with hysteresis, charging and a time window, each
  pointing at a saved profile.
- Per-app fixed rates and a system-default target, with schema 2 profile and backup files
  that still read schema 1.
- Suggested filter for unassigned games, players and camera apps.
- Explicit automation broadcasts for pause, resume, profile selection and toggle.
- Samsung AOD night brightness schedule and AOD rate reapply after a Doze maintenance
  window.

### Changed

- Profile duplication and a short undo for profile deletion, including app assignments.
- The foreground notification updates from state transitions instead of showing a static
  message.
- Activation rules, app targets and usage are included in Diagnostics or the existing
  settings flow without adding new permissions.

### Fixed

- The foreground notification is removed when the service stops.
- Leaving a Doze maintenance window reapplies the AOD rate instead of waiting for another
  screen cycle.
- App targets are applied when the app comes to the foreground.

## [1.2.2-beta.1] - 2026-11-22

### Added

- **Doze with music** switch in Sleep. When enabled, Doze ignores audio playback and
  stays active; when disabled, Doze pauses with music. The option is off by default and
  warns that audio may fail on some devices.
- Diagnostics now report the foreground app, its source (task-stack listener or touch
  probe), the active profile, the active layer and the app layer.

### Changed

- Sleep now keeps Doze active while charging by default. The old "Pause while charging"
  switch became **Doze while charging**; explicit 1.2.1 choices are preserved when
  upgrading, and new installs start with it on.
- Switch descriptions now explain the effect of the current state across Home, AOD, Sleep,
  video, profiles, apps, and update settings. Titles stay stable.
- Sleep idle commands prefer `cmd deviceidle` and fall back to `dumpsys`, cutting the
  common command path in half.
- Idle confirmation runs at 0/400/1200 ms, stops at the first success, and skips the large
  state dump when the public deep-idle check already confirms it.

### Fixed

- Sleep re-evaluates charging, music, pause, and preference changes while the screen is
  off. Music stopping or the charger being unplugged now forces Doze without another screen
  cycle; music starting or the charger being plugged in leaves idle.
- Music playback is observed through the platform callback instead of being read only at
  screen-off, with a short debounce and no polling.
- Leaving idle after a guard change now also sends `deviceidle motion`; `unforce` alone
  could keep the device in IDLE while the screen stayed off.
- A delayed idle verification can no longer overwrite the state after screen-on or after a
  guard change.
- The old pause-with-music preference and 1.2.x backups migrate to the positive Doze with
  music switch without changing the user's previous choice.
- Diagnostics is always available from Settings. The hidden seven-tap unlock and the
  one-way hide toggle were removed, so a fresh install can reach the engine report without
  a gesture.
- Per-app profiles are applied when an app comes to the foreground, even before the first
  touch inside it. The privileged service follows task-stack changes; if the platform
  rejects that listener, the engine falls back to one uncached foreground read after
  touch-up.
- The touch monitor stays active while app assignments exist, so a fixed single-step app
  profile can be left without losing foreground detection.

## [1.2.1] - 2026-11-22

First stable release of the 1.2.1 patch. Includes the AOD/Sleep fixes from 1.2.1-beta.1
and a broad internal modernization: storage, UI state, icons, edge-to-edge, Baseline
Profile, and release tooling.

### Added

- Baseline Profile module, generator, and `profileinstaller` integration.
- `scripts/validate-version.py` and `scripts/bump-version.py`, also used by CI.
- Secret scanning (gitleaks) and dependency review in CI.
- `scripts/generate-material-symbols.py` for the bundled icon set.

### Changed

- Preferences moved to DataStore behind a synchronous facade, with a one-time
  migration from the legacy `onehz_prefs.xml`. Downgrading after the migration
  restores defaults, so export a backup before installing this release.
- Screens collect state through small ViewModels with
  `collectAsStateWithLifecycle`.
- Edge-to-edge layout, predictive back, and theme-driven system bars.
- Text fields use `TextFieldState`; sliders use `SliderState`/`RangeSliderState`.
- Compiled against SDK 37.1 with AGP 9.5.2, Gradle 9.9.2, and Kotlin 2.4.21.
- Package queries use the typed `ApplicationInfoFlags` APIs.
- Bottom sheets use explicit `SheetState`s instead of the deprecated helper.
- The Sleep detail screen uses the shared "How it works" card, a compact note only when
  Sleep is off, a status line only for active/paused/failure states, one dismissible
  warning, consistent icons on its switches, and less repeated copy.
- Primary switches now use icons consistently; sub-options stay icon-free.
- The video card now separates the minimum rate from the detection rules. The rules and
  the global forced-app list open in a focused sheet, and the card shows the active rule
  count plus a warning when no positive signal is enabled.

### Fixed

- The AOD rate switch is available on every supported device. Devices without the
  Samsung AOD brightness key now show an explanation instead of hiding the option.

### Removed

- `compose-material-icons-extended`; the 57 used icons are bundled as vector
  drawables.

## [1.2.1-beta.1] - 2026-11-22

First beta of the 1.2.1 patch. Fixes the AOD switch hierarchy and keeps the Sleep
options available while the feature is off.

### Fixed

- The Home AOD switch is now the only AOD master. Turning it off pauses rate and
  brightness without resetting their choices; the detail screen no longer duplicates the
  master.
- AOD rate and brightness are independent again: turning the rate off no longer hides
  brightness or its restore action.
- AOD master migration derives the new switch from the existing rate/brightness choices.
  Backups without the new field remain compatible.
- Sleep detail no longer duplicates the Home master switch. With Sleep off, behavior
  options, wake-up apps and historical measurements remain visible and editable; test and
  pause actions require Sleep on.
- The Home Sleep subtitle now says "Off" when Sleep is disabled instead of describing
  Doze.

### Changed

- Backup includes the AOD master switch (`aodMasterEnabled`).
=======
## [Unreleased]

Modernization pass preparing 1.3.0. No user-facing feature changes yet.

### Added

- Baseline Profile module, generator, and `profileinstaller` integration.
- `scripts/validate-version.py` and `scripts/bump-version.py`, also used by CI.
- Secret scanning (gitleaks) and dependency review in CI.
- `scripts/generate-material-symbols.py` for the bundled icon set.

### Changed

- Preferences moved to DataStore behind a synchronous facade, with a one-time migration
  from the legacy `onehz_prefs.xml`.
- Screens collect state through small ViewModels with
  `collectAsStateWithLifecycle`.
- Edge-to-edge layout, predictive back, and theme-driven system bars.
- Text fields use `TextFieldState`; sliders use `SliderState`/`RangeSliderState`.
- Compiled against SDK 37.1 with AGP 9.5.2, Gradle 9.9.2, and Kotlin 2.4.21.
- Package queries use the typed `ApplicationInfoFlags` APIs.
- Bottom sheets use explicit `SheetState`s instead of the deprecated helper.

### Removed

- `compose-material-icons-extended`; the 57 used icons are bundled as vector drawables.


## [1.2.0] - 2026-11-22

First stable release after 1.1.0-beta.1. Adds reusable profiles, backup and Sleep, and
reviews the Home hierarchy, naming and content.

### Added

- AOD and Sleep now open dedicated detail screens; Home keeps a compact status summary.
- “Automatic Economy” toggle in **Manage profiles**.
- First-enable confirmation for Sleep, with the notification/VoIP warning before the
  feature is turned on.
- “Restore system brightness” action in the AOD detail screen.
- “Video” shortcut in the profile card; per-profile video rules stay in the editor and
  the forced-app list is clearly global.
- Migration banner explaining Normal/ECO → Equilibrado/Economia and the new Desempenho
  profile.
- Dedicated **Backup and restore** screen with explicit “Includes” / “Does not include”
  lists and a localized import result dialog.

### Changed

- Home now shows the real control state, the selected profile and, when different, the
  profile in use.
- Portuguese names are **Economia / Equilibrado / Desempenho**; “ECO” and “Normal” are no
  longer used in the UI.
- AOD no longer says “screen off (AOD)”; it always uses the panel minimum and explains it.
- Sleep status and measurement labels are clearer: “In Doze for…”, “Last sleep session”,
  “Battery use”, “Partial data”.
- Apps explain that an assignment references a profile and that editing it affects every
  app that uses it.
- Setup explains that the Doze exception is for OneHZ only and is not the Sleep feature,
  and links to the Shizuku setup guide.
- Fastlane descriptions and screenshots updated for 1.2.0; Spanish store listing added.

### Fixed

- Importing a backup created before Sleep existed no longer disables/reset Sleep or
  wrongly reports “Sleep settings restored”.
- Import errors are localized instead of showing `invalid-backup` / `file-too-large`.
- The profile card no longer overflows when the automatic-Economy badge is shown (POCO).
- Plural handling for profile deletion and backup counts (including `many` in PT/ES).
- Unused legacy strings removed; the AOD profile picker strings are gone.

### Feature set

- Economy, Balanced and Performance profiles generated from the device panel.
- Manual active-profile selection with automatic Economy while the system battery saver
  is on.
- Custom profiles with create, rename, edit and delete actions.
- “Restore default” for the three built-in profiles.
- Per-app assignments that reference a saved profile instead of copying its staircase.
- Per-profile video settings: enabled flag, minimum refresh rate and detection rules.
- AOD now always uses the lowest rate exposed by the panel.
- Backup and restore through a versioned JSON file using the Storage Access Framework.
- Configuration backup now includes the Sleep settings; the system Doze whitelist is not
  included.
- Import adapts unsupported refresh rates to the current panel and reports the result.
- Migration from the legacy Normal/ECO profiles and copied per-app staircases.
- Sleep: optional forced Doze on screen-off, with deep/light idle, pause, a cross-midnight
  time window, charging and music guards, a wake-up app list, and local 24-hour
  measurements (screen-off time, Doze time, rest periods and battery rate).

### Changed

- The Home profiles section now shows the active profile with a quick switcher and a
  separate management screen.
- Per-profile enable switches were replaced by one master switch and an active-profile
  selection.
- The AOD frequency picker was removed; the minimum is shown instead.
- The default video rules enable MediaSession, paused-session blocking and the video codec
  rule, leaving the media-audio rule off.
- The per-app ECO exception and the active-profile badge now use the Economy name.

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

[1.2.1-beta.1]: https://github.com/neveSZ/onehz/releases/tag/v1.2.1-beta.1
[1.2.0]: https://github.com/neveSZ/onehz/releases/tag/v1.2.0
[1.1.0-beta.1]: https://github.com/neveSZ/onehz/releases/tag/v1.1.0-beta.1
[1.0.1]: https://github.com/neveSZ/onehz/releases/tag/v1.0.1
[1.0.0]: https://github.com/neveSZ/onehz/releases/tag/v1.0.0
