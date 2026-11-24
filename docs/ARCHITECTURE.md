# Architecture

This document describes how OneHZ works internally. It is written for developers who
want to understand, audit, or modify the app.

## High-level diagram

```text
┌────────────────────────────── OneHZ app process ──────────────────────────────┐
│                                                                              │
│  MainActivity / Compose UI                                                   │
│        │                                                                     │
│        ├── PreferencesStore (DataStore + prefs)                              │
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
- **`OneHzForeground` thread:** task-stack foreground queries in the UserService, debounced
  so an app transition produces one `dumpsys` instead of one per touch.

## UI state and preferences
- **Preferences:** `OneHzPreferences` and `SleepPreferences` keep their synchronous
  `SharedPreferences`-style API, backed by `PreferencesStore`. DataStore is the source of
  truth; an in-memory snapshot serves the engine and the UI. On first start the old
  `onehz_prefs.xml` is migrated once and the XML is cleared.
- **UI state:** screens collect the `StateFlow`s owned by `Diagnostics`,
  `ShizukuGateway`, and `UpdateCenter` through small ViewModels
  (`HomeViewModel`, `DiagnosticsViewModel`, ...) and
  `collectAsStateWithLifecycle`, so collection stops when the screen is not visible.
- **Icons:** Material Symbols Outlined are bundled as vector drawables and exposed by
  `AppIcons` (`scripts/generate-material-symbols.py`), so the app no longer depends on
  `compose-material-icons-extended`. Loaded app icons are kept in a 96-entry LRU.

## Caches and hot paths

The policy path is called on every touch edge, tier change and app switch, so the static
inputs are cached:

- `ProfileStore` keeps the decoded and normalized state keyed by the stored JSON blob and
  the panel rates. Every `save` refreshes it, so edits, imports and resets are immediate.
- `ActivationRulesStore` caches its small JSON the same way.
- `DisplayProfileResolver` caches the vendor route and `DisplayRates` caches the rate list.
  Both are invalidated from `OneHzApp.onConfigurationChanged` (folding/unfolding or a panel
  mode change). An unsupported detection is not cached so a transient boot read can recover.
- `SessionArbiter.audit` runs at most once every 15 seconds; the engine reconciles the
  activity resume (state + foreground/video) at most once every 5 seconds. Screen-on keeps
  the immediate path.
- Video verdicts are cached in the privileged service: 10 seconds for a positive result,
  800 ms for a negative one, so repeated checks reuse a confirmed playback without hiding a
  video that just started. The touch-down probe was removed; the touch-up probe covers the
  app change.

See `docs/PERFORMANCE.md` for the baseline, the remaining work and the measurement protocol.

## AIDL boundary

`IOneHzService` is deliberately narrow and validated on the privileged side:

- device listing and touch monitoring;
- foreground package and task-stack foreground changes;
- Doze exception list and device-idle commands (force, unforce, state);
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

`ProfileStore` owns a versioned JSON blob in the preferences store (DataStore):

- three built-in profiles (`economy`, `balanced`, `performance`);
- custom profiles;
- the active profile id;
- per-app assignments that reference a profile by id;
- the automatic-Economy flag.

Built-in profiles that were never customized are regenerated from the current panel.
Customized built-ins and custom profiles are kept. Deleting a custom profile reassigns its
apps to Balanced. App assignments never copy a staircase, so editing a shared profile
updates every app that references it.

`ProfileCodec` serializes the same model for the preferences store and for the backup file.
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

### Samsung battery-saver keys

While battery saver is active, Samsung may apply its own refresh-rate cap. OneHZ reads
the current values of these firmware keys, writes `0` only where needed, and stores the
original values in preferences:

```text
pms_settings_refresh_rate_enabled
pms_settings_refresh_rate_cover_enabled
psm_refresh_rate_tag
psm_refresh_rate_cover_tag
```

These are literal Samsung firmware names, not OneHZ's profile names. The backup survives
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

Monitoring starts only when the active profile has more than one tier, video mode is
enabled, or at least one app assignment exists. It stops on screen-off, idle, and service
loss.

Per-app assignments also need foreground updates while the screen is on. The UserService
registers a task-stack listener and reports the top package when it changes; the engine
re-evaluates the policy and re-anchors the staircase. When the platform rejects the hidden
listener, the engine falls back to one uncached foreground read after touch-up. There is no
periodic foreground polling.

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
names are validated and the list is capped at 64 entries. The activity dump also reports
the pinned task; `PipSignals` extracts its package and the engine keeps the video floor
while that picture-in-picture window is visible. With no positive signal enabled, video is
not detected except for forced apps.

## Backup

`BackupManager` writes a versioned JSON file through the Storage Access Framework. The
file contains profiles, app assignments and their targets, activation rules, Sleep
settings, the master switch, AOD settings and the forced video list. App targets use
profile schema 2, which still reads schema 1 files. The notification pin is deliberately
excluded, along with internal system-restore snapshots, update caches, logs and
diagnostics. On import, unsupported rates are adapted to the current panel and missing
profile references fall back to Balanced.

The result is cached for a short time in the UserService, keyed by package and config.
When the configuration changes the engine re-probes immediately instead of waiting for
a touch; otherwise it probes after a touch and rechecks while a video is active.

## AOD rate

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

`DozeController` runs on its own handler thread. `SCREEN_OFF` anchors the session after
the AOD brightness key and the AOD rate have been applied; charging, audio, pause and
preference events then re-evaluate the same decision while the screen stays off.

```text
cmd deviceidle force-idle [light]         # deep when no argument is given
cmd deviceidle unforce
cmd deviceidle motion                     # returns to INACTIVE off-screen
dumpsys deviceidle force-idle [light]     # fallback for builds without cmd
dumpsys deviceidle                        # reads mState= and mLightState=
```

The decision is pure (`DozeDecisionResolver`): master switch, feature switch, pause, time
window, charging, Doze with music, and service availability. When the service is not
available, the controller waits and retries once the binder reconnects while the screen is
still off.

An `AudioManager.AudioPlaybackCallback` is registered on the controller thread and
debounced for 200 ms. The plug state and audio state are cached from their events so a
re-evaluation does not repeat binder or sticky-broadcast reads. A guard that starts
blocking runs `unforce` followed by `motion`, because `unforce` alone can leave the state
at IDLE while the screen is off. A guard that stops blocking forces idle without the
screen-off settling delay.

`cmd`/`dumpsys` exit with 0 even for an unknown subcommand, so success is confirmed through
the printed `mState` and `mLightState` values (with `PowerManager.isDeviceIdleMode()` as a
quick deep check). Verification runs at 0/400/1200 ms and stops at the first confirmation.
A failed confirmation shows “The ROM did not enter Doze” and is written to diagnostics.

Whitelist changes use `cmd deviceidle whitelist` and are confirmed by reading the list
back; `dumpsys deviceidle sys-whitelist` exits without changing anything on some builds.

The screen-off read uses the sticky battery broadcast
(`EXTRA_PLUGGED`/`EXTRA_STATUS`), because `BatteryManager.isCharging()` is not updated on
some vendor builds; while the screen is off, the plug state comes from the power
connect/disconnect broadcasts and is cached.

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
- Task-stack listener unavailable → fall back to the touch-up foreground probe.
- Foreground/video dumps fail twice → clear the video minimum rather than getting stuck.
- No positive video signal enabled → no video, except for packages in the forced list.
- Settings write failures → keep the pending restore marker.
- Sleep disabled, engine stopped, or service destroyed → send `unforce`.
- Forced idle not confirmed by the device-idle state → report failure instead of success.

## 1.3.0 components

- `core/RateStats` accumulates applied-rate time from engine transitions in daily buckets
  and persists a small JSON blob on a debounced schedule.
- `SleepLedger` is the single source for screen-off and Doze spans. Usage and the Sleep
  detail read the same records, so the 24-hour rows, the session history and the compact
  summary share one computation.
- `core/ActivationRules` keeps the battery, charging and time-window rules, the hysteresis
  and the pure precedence evaluator used by `PolicyResolver`.
- `core/AppTarget` is a sealed target on every app assignment: saved profile, fixed rate or
  system default. `ProfileCodec` schema 2 reads schema 1.
- `core/PipSignals` parses the pinned task from the activity dump; the engine keeps the
  video floor while that picture-in-picture window is visible.
- `core/SystemChanges` reads the keys OneHZ may write, restores them and builds the panel
  report; `SessionArbiter.audit` marks external changes.
- `ui/tile/OneHzTileService` and `engine/OneHzActionReceiver` reuse the same preference and
  engine actions as the app.
- `OneHzKeepAliveService` builds the notification from `engine/NotificationContent`, which
  maps the diagnostics state to paused, inactive, AOD or active and resolves the profile
  name only when it changes. Rate changes are coalesced to one post per second; state
  transitions are immediate and identical content is not posted again. The service removes
  the notification when it stops. `ui/components/NotificationAccess` is the single check for
  the app notification state (app level plus the OneHZ channel) and is shared with the setup.
- `engine/NotificationPinPolicy` decides whether a dismissed foreground notification may be
  posted again. When pinned, the service restores it; otherwise the dismissal is respected
  until the app is opened again. Both the pin and the optional rate are inactive while the
  system blocks OneHZ notifications, and the stored pin is cleared in that case.

## Security notes

- `INTERNET` is declared only for the opt-in update checker; there is no background
  network use.
- No AccessibilityService.
- Command allowlists for secure/global/system keys.
- Device paths must match `/dev/input/event[0-9]+`.
- Package names must match a package-name regex.
- `exec` has a deadline, a bounded read, and kills the child on timeout.
- The UserService exits with the app.
