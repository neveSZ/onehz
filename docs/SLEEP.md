# Sleep: forcing Doze on screen off

OneHZ controls the display refresh rate through Shizuku. The Sleep feature adds a second,
independent lever: when the screen turns off, OneHZ asks the platform to enter Doze
immediately instead of waiting for the system's inactivity timers.

This document describes the design, the platform interfaces used, the measurement rules,
and the known limitations. It was written before the implementation and is the reference
for the code review.

## Scope

In scope:

- enter Doze when the screen turns off, and leave it when the screen turns on;
- a small set of user guards: pause, time window, charging, audio playback;
- a local measurement of screen-off time, Doze time, and battery drain per rest period.

Out of scope (deliberately):

- disabling Wi-Fi, mobile data, Bluetooth, GPS, or airplane mode;
- suspending packages or blocking notifications;
- disabling biometrics, sensors, or motion detection;
- root-only behaviour;
- anything that requires a new Android permission.

The feature is opt-in, off by default, and only runs while OneHZ itself is enabled. The
first enable shows a confirmation dialog explaining that notifications and internet calls
may be delayed; regular calls and alarms still work.

## Options and defaults

| Option | Default | Notes |
|---|---|---|
| Sleep | off | Requires the OneHZ master switch. |
| Deep Doze | on | Turn it off to request light idle. |
| Pause while charging | on | Read from the sticky battery broadcast. |
| Pause while music is playing | on | `AudioManager.isMusicActive`. |
| Time window | off, 23:00–07:00 | Evaluated only at screen-off; may cross midnight. |
| Pause | off | One hour from the moment it is pressed. |
| Doze exceptions | empty | System Doze exception list through Shizuku. |

## Platform mechanism

Android exposes the device-idle controller through its debug shell interface. A shell
process (UID 2000) may drive it with:

```
dumpsys deviceidle force-idle [deep|light]
dumpsys deviceidle unforce
dumpsys deviceidle whitelist
```

The controller is `DeviceIdleController` in the platform; the commands are the same ones
`adb shell` exposes for testing. `force-idle` places the device in the deep (or light)
idle state immediately. `unforce` clears the forced state and returns to the normal
transition logic. Turning the screen on also takes the device out of idle; `unforce` is
still sent so a later screen-off starts from a clean state.

OneHZ already runs a Shizuku UserService as the shell user, so no new permission, no
`pm grant`, and no manifest change are required. The service executes the commands
directly with its bounded `exec` helper and falls back from `dumpsys` to `cmd` when the
first form is unavailable on a given build.

State is read from two independent sources:

- `PowerManager.isDeviceIdleMode()` (public API, no privilege) for a quick deep-idle check;
- the `mState=` (deep) and `mLightState=` (light) fields of `dumpsys deviceidle` for
  verification and diagnostics. The light field matters because a force in light mode
  leaves the deep state at `INACTIVE`; checking only the deep field would report a false
  failure. The dump is about 23 KB on One UI and the state fields are at the end, so the
  read is not truncated.

Whitelist changes use `cmd deviceidle whitelist` and are confirmed by reading the list
back: `dumpsys deviceidle sys-whitelist` exits successfully without changing anything on
some builds, and `dumpsys` exits 0 even for unknown commands.

Charging is read from the sticky `ACTION_BATTERY_CHANGED` broadcast in addition to
`BatteryManager.isCharging()`: on the tested Samsung firmware the property returned
`false` while the device was charging, which would bypass the charging guard.

The verification matters: `dumpsys` returns exit code 0 even for an unknown command, so
the command's exit status alone is not evidence that the device entered idle.

## Decision model

Every decision is made once, at screen-off, and never polled. If a guard fails, OneHZ
does nothing and the platform keeps its normal behaviour.

| Order | Condition | Result |
|---|---|---|
| 1 | OneHZ master disabled or Sleep disabled | skip |
| 2 | Pause active | skip |
| 3 | Time window enabled and the current minute is outside it | skip |
| 4 | Charging and "skip while charging" enabled | skip |
| 5 | Music playing and "skip with music" enabled | skip |
| 6 | Shizuku service not ready | wait, then retry when it connects (if the screen is still off) |
| 7 | otherwise | force deep or light idle |

The time window supports ranges that cross midnight (for example 23:00 to 07:00, or
08:00 to 16:00 for people who sleep during the day). It is evaluated only at screen-off:
if the window ends while the device is already asleep, OneHZ does not wake up to leave
Doze. Turning the screen on always exits Doze.

## Ordering and timing

Screen-off is a chain: the AOD brightness key is written, the AOD refresh rate is applied,
and only then Sleep forces idle. A fixed delay of 0.8 s is used, with no wake lock. The
delay lets the AOD-related Binder calls complete before the CPU is allowed to suspend. It
is internal and not exposed in the UI.

Screen-on runs in the opposite order: `unforce` is dispatched asynchronously on the
controller's own thread and the wake pulse (first refresh-rate tier) is not blocked by the
shell call.

## Failure handling

- The service is stateless: a lost connection is reported to the controller, which shows
  "waiting for Shizuku" and retries once the privileged service is back.
- The "forced" flag is persisted. On service start and after a reboot, OneHZ sends
  `unforce` and clears the flag, so a crash cannot leave the device forced.
- `onDestroy` of the foreground service and disabling the feature both send `unforce`.
- If neither the deep nor the light state reports `IDLE` or `IDLE_MAINTENANCE`, the card
  shows “the ROM did not enter Doze” and the raw state is written to the diagnostics log.

## Measurement

All values are local, in memory and in `SharedPreferences`. Nothing is uploaded.

Segments are recorded only while the feature is enabled:

- `SCREEN_OFF` segment: from screen-off to screen-on;
- `IDLE` segment: from the device-idle broadcast entering idle to leaving it.

From those segments:

- **Last 24 hours**: rolling window, no midnight or charging reset. Screen-off time is the
  sum of `SCREEN_OFF` segments clipped to `[now - 24h, now]`; Doze time is the same
  clipping applied to `IDLE` segments. A rolling window keeps the metric neutral for any
  schedule, including people who work at night.
- **Last sleep session**: the most recent session of continuous screen-off time with a
  duration of at least 30 minutes. Screen-on gaps shorter than 2 minutes are merged into
  the same session, so a quick glance at the clock does not split a night in two.
- **Battery rate**: `(levelStart - levelEnd) / hours` for a session, in percent per hour,
  labelled **Battery use**. It is shown as `—` when the session is shorter than 15 minutes,
  when the level is unknown, or when the level ended higher than it started (which means
  the device was charging). A session with no measurable drop reports `0.0%/h`.
- **Maintenance**: the number of idle sessions inside a sleep session minus one.
- **Partial data**: when the ledger does not cover the whole window (reboot, app installed
  mid-day, service stopped), the screen shows a **Partial data** badge with an explanation
  instead of a misleading number.

Time uses the wall clock. Negative or zero-length spans are discarded, which covers clock
changes. The ledger keeps a bounded number of records; older ones are dropped.

The values are explicitly labelled as measured by OneHZ. They are not a replacement for
the system battery screen and do not attribute consumption to Doze: on devices with Always
On Display, the screen-off window includes the AOD, which draws power of its own.

## Known limitations

- **VoIP**: with the device in deep idle, an incoming call that depends on a background
  network connection may not arrive until a maintenance window. Traditional telephony
  calls and alarms still leave idle. The first-enable dialog and the detail screen state
  this in plain language.
- **AOD**: deep idle is compatible with AOD on the tested Samsung firmware, but a device
  that freezes its AOD clock can use the light mode instead.
- **OEM behaviour**: some vendor builds ignore or quickly revert a forced idle. The
  verification step surfaces that instead of reporting a false success.
- **Measurement gaps**: counters only exist while the feature and the OneHZ service were
  running. The card says "partial" rather than inventing history.

## Prior art

Forcing Doze through the platform shell interface was popularized by EnforceDoze
(<https://github.com/Akylas/EnforceDoze>, GPL-3.0) and its predecessor ForceDoze
(<https://github.com/theblixguy/ForceDoze>, GPL-3.0). The Sleep feature here was designed
independently from the public Android interfaces listed above. It shares no code, assets,
strings, file structure, or options with those projects, and no GPL-licensed code is
included in OneHZ. See `CREDITS.md`.
