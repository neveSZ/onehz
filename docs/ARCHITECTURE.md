# Architecture

This document describes how OneHZ works internally. It is written for developers who
want to understand, audit, or modify the app.

## High-level diagram

```text
┌────────────────────────────── OneHZ app process ──────────────────────────────┐
│                                                                              │
│  MainActivity / Compose UI                                                   │
│        │                                                                     │
│        ├── OneHzPreferences (SharedPreferences)                              │
│        ├── ProfileStore (profiles, active profile, app assignments)          │
│        ├── Diagnostics (StateFlow, in-memory)                                │
│        └── OneHzEngine (HandlerThread "OneHzEngine")                         │
│                 │                                                            │
│                 ├── PolicyResolver (pure decision)                           │
│                 ├── SessionArbiter (settings dedupe/restore)                 │
│                 ├── AodBrightnessController (HandlerThread)                  │
│                 ├── DozeController (HandlerThread)                           │
│                 └── ShizukuGateway (binder, state, discovery)                │
│                          │                                                   │
│                          │ AIDL: IOneHzService                              │
│                          ▼                                                   │
│             Shizuku UserService process "onehz_core"                         │
│             (shell UID, or root if Shizuku runs as root)                     │
│                 │                                                            │
│                 ├── settings get/put/delete                                  │
│                 ├── getevent -lt (one process per touchscreen)               │
│                 ├── dumpsys activity / media_session / audio                 │
│                 ├── dumpsys media.resource_manager                           │
│                 └── dumpsys deviceidle (whitelist, force-idle, unforce)      │
└──────────────────────────────────────────────────────────────────────────────┘
```

## Processes and threads

- **App process:** UI, preferences, engine, AOD controller, diagnostics.
- **Update checker (`UpdateCenter`):** opt-in, runs on a background dispatcher only when
  the activity starts; no service, worker, or periodic task. It reads the public GitHub
  Releases API, respects the stored interval, caches the newest release in preferences,
  and exposes a `StateFlow` to Settings and Home.
- **`onehz_core` process:** Shizuku UserService. It exits when the app process dies
  through a binder death recipient, so updates/restarts do not leave orphans.
- **`OneHzEngine` thread:** all engine state, timers, policy evaluation, and video
  probes. Touch callbacks are posted to this thread.
- **`OneHzAodBrightness` thread:** content observer, debounce, AOD brightness writes.
- **`OneHzDoze` thread:** sleep decisions, forced-idle commands, verification, ledger.
- **Shizuku executor:** a single-thread executor in `ShizukuGateway` serializes binder
  setup, discovery, and monitor start/stop.
- **Per-device `getevent` threads:** one monitor per accepted touchscreen, owned by the
  UserService.

## AIDL boundary

`IOneHzService` is deliberately narrow and validated on the privileged side:

- device listing and touch monitoring;
- foreground package;
- Doze whitelist and device-idle commands (force, unforce, state);
- refresh-rate settings (allowlisted keys);
- display mode;
- global settings (allowlisted Samsung and HyperOS keys, read/write);
- video detection;
- AOD brightness key;
- client registration and destroy.

Every string/int is validated against a regex or a fixed allowlist before a command is
built. There is no shell interpolation of user input.

`IOneHzService` API changes must increment `API_REVISION` in `ShizukuGateway`. The
UserService version is `VERSION_CODE * 100 + API_REVISION`, which makes Shizuku restart
an outdated service after an app update.

## Profiles and app assignments

`ProfileStore` owns a versioned JSON blob in SharedPreferences:

- three built-in profiles (`economy`, `balanced`, `performance`);
- custom profiles;
- the active profile id;
- per-app assignments that reference a profile by id;
- the automatic-Economy flag.

Built-in profiles that were never customized are regenerated from the current panel.
Customized built-ins and custom profiles are kept. Deleting a custom profile reassigns its
apps to Balanced. App assignments never copy a staircase, so editing a shared profile
updates every app that references it.

`ProfileCodec` serializes the same model for SharedPreferences and for the backup file.
`ProfileDefaults` is pure and generates the three profiles from a rate list; it is covered
by unit tests for two-rate and multi-rate panels.

## Policy precedence

`PolicyResolver.decide()` is pure and unit tested:

1. **Screen off** → AOD at the panel minimum if enabled; otherwise idle.
2. **Battery saver active and automatic Economy enabled**:
   - app assignment with `ignoreEco` → the assigned profile;
   - otherwise → Economy.
3. **Foreground app with an assignment** → the assigned profile.
4. **Selected profile**.
5. Missing/empty profile → idle.

“Idle” means OneHZ releases the session and returns the display to the system adaptive
range.

## Display control

`SessionArbiter` writes:

```text
settings put secure refresh_rate_mode 2
settings put system peak_refresh_rate <hz>
settings put system min_refresh_rate <hz>
```

The logical mode `2` is Samsung's adaptive mode. Writes are deduplicated per epoch:
reapplying the same value performs zero binder calls. `bumpEpoch()` invalidates the
cache after a real state change.

On release:

```text
settings put secure refresh_rate_mode 2
settings delete system peak_refresh_rate
settings delete system min_refresh_rate
```

Deleting peak/min lets the system use its stock adaptive range instead of replaying
captured values (which could be our own AOD state).

### Xiaomi/HyperOS route

HyperOS does not use Samsung's peak/min pair. OneHZ writes a single secure setting:

```text
settings put secure miui_refresh_rate <hz>
```

On HyperOS 1 the key may be `user_refresh_rate`; the resolver picks whichever exists.
Before the first write, the original value (or its absence) is snapshotted, and release
replays that snapshot exactly, deleting the key when it did not exist.

### Samsung ECO keys

While battery saver is active, Samsung may apply its own refresh-rate cap. OneHZ reads
the current values of these firmware keys, writes `0` only where needed, and stores the
original values in preferences:

```text
pms_settings_refresh_rate_enabled
pms_settings_refresh_rate_cover_enabled
psm_refresh_rate_tag
psm_refresh_rate_cover_tag
```

These are literal Samsung firmware names, not OneHZ's ECO naming. The backup survives
process death and is restored on release/reconnect. If a restore write fails, the backup
is kept for the next connection.

## Touch pipeline

- `getevent -lp` lists input devices.
- `TouchDeviceSelector` accepts only devices with `BTN_TOUCH`,
  `ABS_MT_POSITION_X/Y`, and `INPUT_PROP_DIRECT`, rejecting pointers, touchpads,
  proximity nodes, and known non-touch names.
- `getevent -lt <path>` is started per accepted device with one thread.
- `MtTouchParser` tracks MT-B slots and `ABS_MT_TRACKING_ID`; `BTN_TOUCH` is a fallback
  for drivers without tracking ids.
- The UserService emits only DOWN/UP edges; MOVE events are not forwarded.
- `ShizukuGateway` aggregates all devices and calls the engine only when the whole set
  transitions between “no finger” and “touching”.

Monitoring starts only when the active profile has more than one tier or video mode is
enabled. It stops on screen-off, idle, and service loss.

## Video detection

The UserService resolves the foreground package and gathers the signals selected by
`VideoConfig`:

1. `dumpsys media_session` — the app's session state (when MediaSession is enabled);
2. `dumpsys media.resource_manager` — processes holding a video codec (when the codec
   rule is enabled);
3. `dumpsys audio` — started `USAGE_MEDIA` players (only when no playing session already
   confirmed playback).

Default rules, stored per profile and editable in the profile editor:

- a session that exists but is paused is authoritative → not playing;
- the foreground app must hold a video decoder;
- with a playing session, the codec is enough;
- without a session, a started media player confirms playback (off by default);
- audio-only apps, camera, and games do not hold video decoders.

The forced-app list is global. The engine combines the active profile's rules with that
list into `VideoConfig`, which is sent to the privileged service over the binder. Package
names are validated and the list is capped at 64 entries. With no positive signal
enabled, video is not detected except for forced apps.

## Backup

`BackupManager` writes a versioned JSON file through the Storage Access Framework. The
file contains profiles, app assignments, the master switch, AOD settings and the forced
video list. It deliberately excludes internal system-restore snapshots, update caches,
logs and diagnostics. On import, unsupported rates are adapted to the current panel and
missing profile references fall back to Balanced.

The result is cached for a short time in the UserService, keyed by package and config.
When the configuration changes the engine re-probes immediately instead of waiting for
a touch; otherwise it probes after a touch and rechecks while a video is active.

## AOD refresh rate

The AOD is a fixed-rate layer applied while the screen is off. Its rate is always the
lowest rate exposed by the panel; there is no user-selected AOD frequency. If a very low
rate is rejected by the panel, the diagnostic log records the failure and the engine can
fall back to 30 Hz.

## AOD brightness

Samsung's AOD service reads `debug_aod_brightness` (Settings.System, 0–255) when the AOD
starts. OneHZ:

1. observes interactive screen brightness changes (debounced 400 ms);
2. computes the target from a percentage with a safety band;
3. pre-writes the key while the screen is on;
4. rechecks once on `SCREEN_OFF`;
5. removes the key when the feature/master is disabled or the user restores it.

The write is deduplicated by target and a “written” preference allows cleanup after a
crash.

## Sleep (forced Doze)

`DozeController` runs on its own handler thread. Every guard is evaluated once, at
`SCREEN_OFF`, after the AOD brightness key and the AOD rate have been applied:

```text
dumpsys deviceidle force-idle [light]     # deep when no argument is given
dumpsys deviceidle unforce
dumpsys deviceidle                        # reads mState= and mLightState=
```

The decision is pure (`DozeDecisionResolver`): master switch, feature switch, pause, time
window, charging, music playback, and service availability. When the service is not
available, the controller waits and retries once the binder reconnects while the screen is
still off.

`dumpsys` exits with 0 even for an unknown subcommand, so success is confirmed through the
printed `mState` and `mLightState` values (with `PowerManager.isDeviceIdleMode()` as a
quick deep check). A failed confirmation shows “The ROM did not enter Doze” and is written
to diagnostics.

Whitelist changes use `cmd deviceidle whitelist` and are confirmed by reading the list
back; `dumpsys deviceidle sys-whitelist` exits without changing anything on some builds.

Charging comes from the sticky battery broadcast (`EXTRA_PLUGGED`/`EXTRA_STATUS`),
because `BatteryManager.isCharging()` is not updated on some vendor builds.

Screen-on dispatches `unforce` on the controller thread, so the wake pulse and the
refresh-rate write are not delayed by the shell call. The forced flag is persisted:
`onDestroy`, disabling the feature, a service restart, and a reboot all clear it, so a
crash cannot leave the device forced.

`SleepLedger` stores screen-off and idle spans, capped and encoded in preferences. “Last
24 hours” clips every span to the rolling window; rest periods merge screen-on gaps
shorter than two minutes before applying the 30-minute minimum. The battery rate is the
session delta per hour and is omitted when the session is shorter than 15 minutes or the
level increased.

## Fail-closed behavior

- No vendor/display eligibility → no control.
- Shizuku missing/dead → stop control, release settings, show a warning.
- Touch discovery failure → retry with backoff while monitoring is still wanted.
- Foreground/video dumps fail twice → clear the video floor rather than getting stuck.
- No positive video signal enabled → no video, except for packages in the forced list.
- Settings write failures → keep the pending restore marker.
- Sleep disabled, engine stopped, or service destroyed → send `unforce`.
- Forced idle not confirmed by the device-idle state → report failure instead of success.

## Security notes

- `INTERNET` is declared only for the opt-in update checker; there is no background
  network use.
- No AccessibilityService.
- Command allowlists for secure/global/system keys.
- Device paths must match `/dev/input/event[0-9]+`.
- Package names must match a package-name regex.
- `exec` has a deadline, a bounded read, and kills the child on timeout.
- The UserService exits with the app.
