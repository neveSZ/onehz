# Features

**English** · [Português (Brasil)](FEATURES.pt-BR.md)

This page describes what each OneHZ feature does and how it behaves. For install and
update instructions see [INSTALL](INSTALL.md); for devices and limitations see
[COMPATIBILITY](COMPATIBILITY.md).

All profiles apply only while OneHZ is enabled and connected to Shizuku.

---

## 1. Refresh-rate staircase (1 to 5 tiers)

**What it does:** each profile has one to five tiers. Every tier has a refresh rate and,
except the last one, a hold time.

**How it works:**

- a touch sets the first tier immediately;
- each hold time elapses and the next tier is applied;
- the last tier is the resting rate and has no time limit;
- a single-tier profile behaves as a fixed rate and does not start the touch monitor;
- tiers must descend (for example 120 → 60 → 30);
- hold times range from 0.5 s to 60 s in 0.5 s steps;
- while a finger is on the screen, the step-down is deferred (up to 20 rechecks of
  100 ms) so the UI does not drop while you are interacting.

## 2. Profiles: Economy, Balanced and Performance

**What it does:** stores reusable configurations and applies the selected one.

**How it works:**

- a fresh install creates three built-in profiles:
  - **Economy** — the lowest bands, generated from the panel minimum;
  - **Balanced** — the default profile, using the highest rate above 60 Hz when available;
  - **Performance** — preserves a 60 Hz-or-higher resting floor;
- the active profile is selected by the user on Home;
- built-in profiles can be edited and restored to the device defaults;
- custom profiles can be created, renamed, edited and deleted;
- every profile stores its own video settings;
- per-app assignments reference a saved profile instead of copying its staircase.

On a panel with only two rates, the three profiles use the same two bands and differ only
by how long the maximum is held. The time at the minimum is the resting time until the
next touch, so there is only one adjustable hold on such panels.

## 3. Battery saver and the active profile

**What it does:** keeps the current behavior of switching to Economy while the system
battery saver is active.

**How it works:**

- the app listens to the system battery-saver broadcast and reapplies the policy;
- while battery saver is on, Economy is applied automatically unless the foreground app
  opts out with **“Ignore ECO”**;
- when battery saver turns off, the selected profile returns;
- the behavior can be disabled so the selected profile is always used;
- while Economy is active, OneHZ disables Samsung's own battery-saver refresh-rate caps
  and **restores the original values** when the session ends. On Xiaomi, the HyperOS rate
  key is snapshotted and restored the same way. This prevents the system settings from
  staying modified after a crash or uninstall.

## 4. AOD: screen-off refresh rate

**What it does:** applies a fixed rate while the screen is off / Always On Display is
active.

**How it works:**

- the AOD switch is independent from the profiles;
- the AOD always uses the **lowest rate exposed by the panel**;
- when the screen turns off, timers and the touch monitor stop, so the device can enter
  Doze normally;
- turning the screen on resumes the active profile and applies the wake pulse.

## 5. AOD brightness

**What it does:** on Samsung, sets the AOD brightness from a percentage of the screen
brightness at lock time, with a safety band.

**How it works:**

- the app writes Samsung's `debug_aod_brightness` system setting (0–255);
- the AOD service reads that key when the AOD starts;
- OneHZ pre-writes the target while the screen is on, so it does not need to win a race
  during screen-off;
- one write per transition; no polling during AOD;
- a minimum/maximum percentage band keeps the AOD readable;
- “Restore system brightness” removes the key and returns control to the system.

This feature is **Samsung-only** and is hidden on Xiaomi, where AOD brightness is
controlled by the vendor's own process. If you uninstall OneHZ, disable AOD brightness
first. If the key stays behind, remove it with:

```powershell
adb shell settings delete system debug_aod_brightness
```

## 6. Per-app profiles

**What it does:** assigns a saved profile to an installed app.

**How it works:**

- an app references one of the saved profiles (built-in or custom);
- the foreground app is read only when at least one assignment exists;
- on touch and on resume, the app asks the Shizuku service for the foreground package;
- there is no periodic polling;
- editing the referenced profile updates every app that uses it;
- deleting a profile reassigns its apps to Balanced;
- if the app has no assignment, the active profile applies;
- during automatic Economy, an app only keeps its profile when **“Ignore ECO”** is on.

## 7. Video mode

**What it does:** keeps the refresh rate at the floor configured in the active profile
while a video is playing.

**How it works:**

- the foreground app must hold a video decoder in Codec2;
- a MediaSession that exists but is paused is authoritative: it is not treated as
  playing;
- without a MediaSession, a started `USAGE_MEDIA` audio player confirms playback;
- audio-only apps, the camera, and games do not hold a video decoder, so they do not
  trigger the floor;
- a probe runs ~1.8 s after the finger lifts and a slow recheck runs every 12 s while a
  video is active;
- the video floor has priority over the profile while playback lasts.

The defaults enable MediaSession, the paused-session veto, and the decoder rule; the
media-audio rule is off. Every rule can be changed per profile in the profile editor. The
forced-app list is global. **PiP is not handled yet** (see the [backlog](BACKLOG.md)).

## 8. Wake pulse

**What it does:** avoids rendering the lock screen and unlock animation at the resting
rate.

**How it works:** on screen-on/unlock, the first tier is applied for about one second.
The pulse is debounced (1.2 s) because `SCREEN_ON` and `USER_PRESENT` usually arrive
together.

## 9. Device-aware defaults

**What it does:** creates sensible profiles on a fresh install.

**How it works:**

- reads the panel rates from the static Samsung feature list or from the Xiaomi
  `Display.supportedModes` fallback;
- on a panel with two rates, all three profiles use the maximum and minimum and differ
  only by the hold at the maximum (1 s / 2 s / 5 s);
- on a panel with more rates:
  - **Economy**: maximum 1 s → 60 Hz (or the highest rate below 60) 6 s → minimum;
  - **Balanced**: maximum 2 s → highest rate above 60 Hz (or 60) 5 s → minimum;
  - **Performance**: maximum 5 s → highest rate above 60 Hz 8 s → 60 Hz or higher;
- Economy video floor is 30 Hz; Balanced and Performance use 60 Hz;
- unsupported rates are rejected in the editor;
- on the tested S25+ (10/20/24/27/30/40/48/60/80/120 Hz), the generated profiles are
  Economy 120 → 60 → 10, Balanced 120 → 80 → 10 and Performance 120 → 80 → 60.

Existing users keep their Normal/ECO staircases as Balanced/Economy and receive a new
Performance profile. The old video rules are preserved, while the video minimum follows
the new 30/60/60 defaults.

## 10. Guided setup

- vendor/display compatibility check;
- Shizuku status and permission request;
- optional battery-optimization exemption;
- optional Doze whitelist (through Shizuku);
- notification permission;
- persistent foreground-service notification.

## 11. No wake locks

The optional foreground service keeps the engine alive without holding a wake lock. The
phone still enters Doze. Screen state changes are handled by system broadcasts, not by
polling.

## 12. Diagnostics

- in-memory log (up to 500 entries), not uploaded and not persisted;
- filters by tag and text;
- engine state, applied/observed Hz, last write, video detail, touch monitor state,
  screen brightness/mode, battery-saver state, and the active rate route;
- test buttons for the first/resting tier and a full cycle;
- “Verify display control” and “Restore system brightness” safety actions;
- diagnostic report can be copied manually for a bug report.

## 13. Appearance

System, light, dark, and AMOLED themes. AMOLED uses true black on OLED panels.

## 14. Backup and restore

**What it does:** exports and imports the complete behavior configuration.

**How it works:**

- Settings → **Backup and restore** writes a versioned JSON file through the system file
  picker, without storage permissions;
- the file includes all profiles, the active profile, app assignments, video settings,
  AOD settings, the master switch, and the forced-video app list;
- theme, language, diagnostics, update cache, and internal system-restore snapshots are
  not included;
- import replaces the current behavior configuration after a confirmation;
- profiles whose rates are not supported by the current panel are adapted to the nearest
  supported rate, and the result summary reports how many were adapted;
- app assignments pointing to a missing profile fall back to Balanced.

## 15. Languages

Portuguese (Brazil), English, and Spanish. See [translation status](TRANSLATIONS.md).
