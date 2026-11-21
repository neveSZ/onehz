# OneHZ

**English** · [Português (Brasil)](README.pt-BR.md)

[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-12%2B-green.svg)](https://developer.android.com)
[![No root](https://img.shields.io/badge/root-not%20required-brightgreen.svg)](#requirements)
[![No telemetry](https://img.shields.io/badge/telemetry-none-brightgreen.svg)](PRIVACY.md)
[![GitHub Sponsors](https://img.shields.io/badge/sponsor-GitHub%20Sponsors-ea4aaa.svg)](https://github.com/sponsors/neveSZ)
[![LivePix (Brazil)](https://img.shields.io/badge/support-LivePix%20Brazil-ff69b4.svg)](https://livepix.gg/onehz)

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https%3A%2F%2Fgithub.com%2FneveSZ%2Fonehz"><img src="docs/assets/obtainium-badge.png" alt="Get it on Obtainium" height="48"></a>

OneHZ is a refresh-rate controller for **Samsung LTPO displays**. It changes the
display refresh rate through **Shizuku only** — no root, no direct ADB from the app,
no `WRITE_SECURE_SETTINGS`, and **no AccessibilityService**.

> **Personal project.** I built OneHZ for my own daily use. I do not provide a
> maintenance guarantee, but I am open to bug reports and suggestions. Use it at your
> own risk. All features are free and open source; donations never unlock anything.

---

## Project status

- Latest stable release: **1.0.1**.
- Tested on **one device only**: Samsung Galaxy S25+ (`SM-S936B`).
- Runs **only on Samsung phones with an LTPO panel**. The app checks this and fails
  closed (no changes) when the device is not eligible.
- Future beta builds will be published as pre-releases.

## Tested device and software

| Item | Value |
|---|---|
| Device | Samsung Galaxy S25+ (`SM-S936B`, product `pa2q`) |
| Android | 17 (API 37) |
| One UI | 9.0 (`ro.build.version.oneui=90000`) |
| Build | `S936BXXUDDZJB` |
| Security patch | 2026-10-05 |
| Resolution tested | 1080 × 2340 (FHD+) |
| Panel rates exposed by the system | 120, 80, 60, 48, 40, 30, 26.67, 24, 20, 10 Hz |
| Shizuku | `13.7.0-thedjchi` fork |
| OneHZ | 1.0.1 |

**What was not tested:** other Samsung models, other Android/One UI versions, WQHD+,
foldables, cover screens, tablets, and other brands. They may work, but I cannot
guarantee it.

## Requirements

- Samsung phone with an **LTPO panel**.
- **Android 12 (API 31) or newer** (`minSdk 31`; only Android 17 was tested).
- **Shizuku installed and running.**
- OneHZ is distributed as a signed APK on GitHub Releases. It is not on Google Play.
- [Obtainium](https://obtainium.imranr.dev/) is supported for automatic updates.

### Shizuku recommendation

I tested OneHZ with the **thedjchi fork `v13.7.0-thedjchi`**:
<https://github.com/thedjchi/Shizuku/tree/v13.7.0-thedjchi>

The official Shizuku should work, but I have not tested it. If you want to match my
setup, use the same fork version.

### No accessibility service

OneHZ deliberately does **not** use `AccessibilityService`. Accessibility services can
interfere with banking apps and other sensitive applications. Everything privileged goes
through Shizuku, and the app fails closed when Shizuku is unavailable.

---

## Features

### 1. Refresh-rate staircase (1 to 5 tiers)

**What it does:** each profile has one to five tiers. Every tier has a refresh rate and,
except the last one, a hold time.

**How it works:**

- a touch sets the first tier immediately;
- each hold time elapses and the next tier is applied;
- the last tier is the resting rate and has no time limit;
- a single-tier profile behaves as a fixed rate and does not start the touch monitor;
- tiers must descend (for example 120 → 60 → 10);
- hold times range from 0.5 s to 60 s in 0.5 s steps;
- while a finger is on the screen, the step-down is deferred (up to 20 rechecks of
  100 ms) so the UI does not drop while you are interacting.

### 2. Normal profile

**What it does:** the profile used when the system battery saver is **not** active.

**How it works:** it is independent from the ECO profile and can be disabled on its own.
When disabled, OneHZ stops controlling the rate in that situation and restores the
system adaptive behavior.

### 3. ECO profile (battery saver)

**What it does:** the profile used while the system battery saver is active.

**How it works:**

- the app listens to the system battery-saver broadcast and reapplies the policy;
- the default ECO profile caps the top tier at 90 Hz (or the nearest supported rate
  below it) and then uses the device minimum;
- an app can opt out with **“Ignore ECO”**, keeping its own/normal profile while battery
  saver is on;
- while ECO is active, OneHZ disables Samsung's own battery-saver refresh-rate caps and
  **restores the original values** when the session ends. This prevents the system
  settings from staying modified after a crash or uninstall.

### 4. AOD: screen-off refresh rate

**What it does:** applies a dedicated fixed rate while the screen is off / Always On
Display is active.

**How it works:**

- the AOD switch is independent from the normal and ECO profiles;
- the default is 30 Hz (the system default);
- when the screen turns off, timers and the touch monitor stop, so the device can enter
  Doze normally;
- turning the screen on resumes the normal staircase and applies the wake pulse.

### 5. AOD brightness

**What it does:** sets the AOD brightness from a percentage of the screen brightness at
lock time, with a safety band.

**How it works:**

- the app writes Samsung's `debug_aod_brightness` system setting (0–255);
- the AOD service reads that key when the AOD starts;
- OneHZ pre-writes the target while the screen is on, so it does not need to win a race
  during screen-off;
- one write per transition; no polling during AOD;
- a minimum/maximum percentage band keeps the AOD readable;
- “Restore system brightness” removes the key and returns control to the system.

> If you uninstall OneHZ, disable AOD brightness first. If the key stays behind, remove
> it with:
>
> ```powershell
> adb shell settings delete system debug_aod_brightness
> ```

### 6. Per-app profiles

**What it does:** assigns a profile to an installed app.

**How it works:**

- apps can start from the normal profile, the ECO profile, or a custom profile;
- the foreground app is read only when at least one app profile exists;
- on touch and on resume, the app asks the Shizuku service for the foreground package;
- there is no periodic polling;
- if the app has no profile, the normal/ECO policy applies.

### 7. Video mode

**What it does:** keeps the refresh rate at a configurable floor while a video is
playing.

**How it works:**

- the foreground app must hold a video decoder in Codec2;
- a MediaSession that exists but is paused is authoritative: it is not treated as
  playing;
- without a MediaSession, a started `USAGE_MEDIA` audio player confirms playback;
- audio-only apps, the camera, and games do not hold a video decoder, so they do not
  trigger the floor;
- a probe runs ~1.8 s after the finger lifts and a slow recheck runs every 12 s while a
  video is active;
- the video floor has priority over the app/normal/ECO profile while playback lasts.
- **PiP is not handled yet** (see the backlog).

### 8. Wake pulse

**What it does:** avoids rendering the lock screen and unlock animation at the resting
rate.

**How it works:** on screen-on/unlock, the first tier is applied for about one second.
The pulse is debounced (1.2 s) because `SCREEN_ON` and `USER_PRESENT` usually arrive
together.

### 9. Device-aware defaults

**What it does:** creates sensible profiles on a fresh install.

**How it works:**

- reads the static Samsung feature list for the panel rates, falling back to
  `Display.supportedModes`;
- the default normal profile is **panel maximum → ~60 Hz → panel minimum** (on the
  tested S25+: 120 → 60 → 10);
- the ECO default caps the top rate at 90 Hz when supported;
- unsupported rates are rejected in the editor.

### 10. Guided setup

- LTPO compatibility check;
- Shizuku status and permission request;
- optional battery-optimization exemption;
- optional Doze whitelist (through Shizuku);
- notification permission;
- persistent foreground-service notification.

### 11. No wake locks

The optional foreground service keeps the engine alive without holding a wake lock. The
phone still enters Doze. Screen state changes are handled by system broadcasts, not by
polling.

### 12. Diagnostics

- in-memory log (up to 500 entries), not uploaded and not persisted;
- filters by tag and text;
- engine state, applied/observed Hz, last write, video detail, touch monitor state;
- test buttons for the first/resting tier and a full cycle;
- “Verify display control” and “Restore system brightness” safety actions;
- diagnostic report can be copied manually for a bug report.

### 13. Appearance

System, light, dark, and AMOLED themes. AMOLED uses true black on OLED panels.

### 14. Languages

Portuguese (Brazil), English, and Spanish. See [translation status](#translations) below.

---

## How it works (technical summary)

1. The app binds a **Shizuku UserService** (`IOneHzService`) running in the
   `onehz_core` process with shell/root privileges.
2. The service reads raw touch events with `getevent -lt` and notifies the app through a
   one-way AIDL callback. `MtTouchParser` understands MT-B tracking ids and slots.
3. `OneHzEngine` runs on its own thread, keeps the staircase, and schedules one timer
   per tier.
4. `PolicyResolver` chooses the situation: **screen off → AOD**, **battery saver → ECO**,
   otherwise **normal**, with the app profile when configured.
5. `SessionArbiter` writes:
   - `settings put secure refresh_rate_mode 2`;
   - `settings put system peak_refresh_rate <hz>`;
   - `settings put system min_refresh_rate <hz>`.
   Writes are deduplicated per epoch, so reapplying the same value costs zero privileged
   calls.
6. On release, OneHZ sets the adaptive mode and deletes peak/min so the system returns to
   its stock adaptive range.
7. If Shizuku dies, the app fails closed: it stops controlling and shows a warning.
8. The UserService exits with the app through a binder death recipient, so no orphan
   process is left after updates or process death.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for details.

---

## Install

1. Install Shizuku (the tested fork is linked above).
2. Start Shizuku via wireless debugging or root.
3. Install the OneHZ APK from [GitHub Releases](../../releases).
4. Open OneHZ and allow the Shizuku permission.
5. Follow the setup screen: compatibility, Shizuku, optional battery/Doze/notification
   permissions.
6. Turn on the master switch.

> If you are coming from a debug build signed with a different key, uninstall it first:
> `adb uninstall io.github.nevesz.onehz`.

### Automatic updates with Obtainium

[Obtainium](https://obtainium.imranr.dev/) can track OneHZ releases and notify you when a
new version is available:

1. Install Obtainium.
2. Add `https://github.com/neveSZ/onehz` as an app source, or use the badge at the top.
3. Let Obtainium check for updates and install new releases.

GitHub Releases and Obtainium use the same signed APK and signing key. The optional
in-app update checker is separate: it only opens the GitHub release page when you enable
it. Obtainium is the recommended way to receive automatic updates.

## Update

Install the new APK over the old one. Updates require the same signing key. Your
settings are kept. If Shizuku is running, the app reconnects automatically.

OneHZ can optionally check GitHub for a newer release when you open the app. The check
is **off by default**: enable it in **Settings → Updates**, accept the explanation, and
choose the interval (24 hours, 3 days, 7 days, or 30 days) and the channel (stable only
or stable plus pre-releases). It never runs in the background.

For automatic updates, use [Obtainium](#automatic-updates-with-obtainium). The in-app
checker only notifies you about a release; it never downloads or installs an APK.

## Uninstall

1. Turn off AOD brightness and use “Restore system brightness”.
2. Uninstall the app.
3. If the AOD key remains:
   `adb shell settings delete system debug_aod_brightness`.

---

## Privacy and permissions

OneHZ requests the `INTERNET` permission **only for the optional update checker**, which
is off by default. There is no analytics, no advertising, no telemetry, and no account.
Configuration is stored locally in `SharedPreferences`; diagnostics live in memory only.

| Permission | Why |
|---|---|
| `QUERY_ALL_PACKAGES` | List installed apps for per-app profiles. |
| `RECEIVE_BOOT_COMPLETED` | Reapply the profile after a reboot. |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | Keep the optional engine service alive. |
| `POST_NOTIFICATIONS` | Show the persistent service notification. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Optional battery exemption requested by you. |
| `INTERNET` | Optional GitHub update check, off by default. |
| Shizuku permission | Perform the privileged display/settings operations. |

The app does **not** use AccessibilityService, overlay, storage, contacts, location,
camera, or microphone. The network is used only when you enable the update checker.

The update checker contacts the public GitHub API only when you open OneHZ, respects the
chosen interval, and never runs in the background. No token, account, or identifier is
sent. See [PRIVACY.md](PRIVACY.md).

---

## Known limitations

- **Samsung only.** Other manufacturers are rejected by the compatibility gate.
- **LTPO only.** The gate requires a Samsung LTPO panel with a minimum rate ≤ 30 Hz.
- **One device tested.** Only the S25+ `SM-S936B` on Android 17 / One UI 9.0.
- Other Android/One UI versions and WQHD+ are untested.
- Shizuku must be running. After a reboot, it may need to be restarted through wireless
  debugging/ADB.
- Display detection depends on Samsung firmware settings and `dumpsys` output; a future
  firmware update may change them.
- AOD brightness uses a hidden Samsung key and may be ignored/reset by an update.
- Video detection depends on MediaSession, Codec2, and AudioService behavior.
- PiP video is not detected yet.
- If Android kills the app while a non-default rate is applied (for example, due to
  aggressive battery management), the last rate may remain until OneHZ starts again.
  Grant the optional battery exemption or reopen the app to restore the system default.
- Do not run another refresh-rate controller at the same time.
- No warranty. This is a personal project.

## Backlog

- PiP support in video detection;
- more Samsung devices/brands (unlikely: I have no other test devices);
- removing the LTPO dependency (unlikely: it would need a different control path).

Donations are optional. Supporters outside Brazil can use
[GitHub Sponsors](https://github.com/sponsors/neveSZ); supporters in Brazil can use
[LivePix](https://livepix.gg/onehz) (Pix/BRL). They **never** unlock features.

---

## Build

Requirements: **JDK 25**, Android SDK **37**, build-tools **36.0.0**, and the Gradle
wrapper included in the repository.

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

Release build with R8 + resource shrinking:

```powershell
.\gradlew.bat assembleRelease
```

Signing is read from `keystore.properties` (never committed), from
`%USERPROFILE%\keys\onehz\keystore.properties`, or from the `ONEHZ_*` environment
variables. See [docs/BUILDING.md](docs/BUILDING.md) and
[keystore.properties.example](keystore.properties.example).

Current release size: about **3.2 MB**.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Building](docs/BUILDING.md)
- [Testing](docs/TESTING.md)
- [Releasing](docs/RELEASING.md)
- [Compatibility](docs/COMPATIBILITY.md)
- [FAQ](docs/FAQ.md)
- [Backlog](docs/BACKLOG.md)
- [Translations](docs/TRANSLATIONS.md)
- [Privacy](PRIVACY.md)
- [Security](SECURITY.md)

## Verify the APK

Every release includes a `.sha256` file. You can also inspect the signer:

```powershell
apksigner verify --print-certs OneHZ-1.0.1.apk
```

Certificate SHA-256:
`D8:E8:24:B2:5F:6B:1D:29:75:BD:72:D6:89:5F:C4:7F:7B:EB:C2:05:78:41:F7:83:3C:25:67:0A:C2:04:F8:0A`

---

## Support

All features are free and open source. Donations are optional and never unlock anything.

- **International:** [GitHub Sponsors](https://github.com/sponsors/neveSZ) — monthly or
  one-time.
- **Brazil:** [LivePix](https://livepix.gg/onehz) — Pix in BRL.

Choose the option that works best for you. Both links are also available in the app under
**About → Support OneHZ**.

Bug reports and suggestions are welcome through GitHub Issues. Please include your
device model, Android/One UI build, Shizuku version, OneHZ version, and the diagnostic
report. Remove any personal data before posting.

## License and credits

OneHZ is released under the **MIT License**. See [LICENSE](LICENSE) and the
[unofficial Portuguese translation](LICENSE.pt-BR.md).

Credits:

- **Author:** [neveSZ](https://github.com/neveSZ).
- **LLM assistance:** developed with help from LLMs through the **OpenCode** coding agent
  harness, then reviewed and tested by the author.
- **Adaptive Hz** by Mahmut Alperen Ünal (MIT): OneHZ includes portions derived from it.
  See [NOTICE](NOTICE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
- **Shizuku** by RikkaApps (Apache 2.0) and the **thedjchi fork** (Apache 2.0).
- **AndroidX / Jetpack Compose / Kotlin / kotlinx.coroutines** (Apache 2.0).
- **JUnit 4** (test only, Eclipse Public License 1.0; not distributed in the app).

No code or assets from closed-source applications are included.

### Translations

- **Português (Brasil):** reviewed by the author.
- **English:** AI-generated, **not reviewed by a native speaker**.
- **Español:** AI-generated, reviewed for consistency, **not reviewed by a native
  speaker**.

Corrections are welcome. See [docs/TRANSLATIONS.md](docs/TRANSLATIONS.md).

## Disclaimer

OneHZ is not affiliated with, endorsed by, or sponsored by Samsung Electronics.
Samsung, Galaxy, and One UI are trademarks of Samsung Electronics. Android is a
trademark of Google LLC. Use OneHZ at your own risk; the software is provided “as is”,
without warranty of any kind.
