# Performance and resource use

OneHZ is event-driven: no polling loop, no wake lock, no periodic alarm and no background
network. This document records the hot paths, the caches that keep them cheap and how to
measure them.

## Hot paths

1. **Touch → policy → settings write.** A touch edge posts to the `OneHzEngine` thread, which
   resolves the policy and asks `SessionArbiter` to write the rate. The profile state and the
   panel capabilities are cached (see below), so this path no longer decodes JSON or queries
   the vendor on every touch.
2. **OneHZ app resume.** Reopening the app reconciles the system state and refreshes the
   foreground/video at most once every five seconds. Screen-on keeps the immediate path.
3. **Video detection.** A privileged probe runs on touch-up (1.8 s later) and when the
   staircase reaches its resting step. One direct activity dump feeds both the foreground
   and the pinned/PiP task; `media_session`, `ps`, the codec list and audio are read
   directly and parsed in-process, so a probe costs about five processes instead of eleven.
   The privileged service caches a positive verdict for 10 s and a negative one for 800 ms;
   the short negative window keeps the tap-to-play flow while repeated checks reuse a
   confirmed playback.
4. **Notification.** The service coalesces rate changes to one post per second, skips
   identical content and bypasses the window for state changes (pause, idle, AOD, profile).
   The pin/active check ignores posts from the last three seconds so our own update is never
   mistaken for a dismissal.
5. **UI lists.** App icons live in a 96-entry LRU; the diagnostics log reuses one formatter
   per thread and copies the list once.

## Caches and invalidation

| Cache | Key | Invalidation |
|---|---|---|
| `ProfileStore` | stored JSON + panel rates | `save`, blob change, `invalidate()` |
| `ActivationRulesStore` | stored JSON | `set`, blob change |
| `DisplayProfileResolver` | process | configuration change, unsupported results are not cached |
| `DisplayRates` | profile | configuration change |
| video verdict | package + config | 10 s positive / 800 ms negative |
| foreground package | time | 250 ms in the privileged service |
| icon bitmaps | package | LRU eviction (96 entries) |

The display caches are cleared from `OneHzApp.onConfigurationChanged`, which covers folding
and unfolding as well as a panel mode change. The profile cache is refreshed by `save`, so
editing a profile, importing a backup or restoring defaults is visible immediately.

## Remaining work (tracked in the 1.3.1 plan)

- **P-09**: the Samsung peak/min pair is written with two `settings` processes; wrapping both
  in a shell would add a third process, so the pair is left as is. The arbiter lock is kept
  because `release`/`restorePending` can run from another thread while an apply is in flight.
- **P-12/P-15**: move the Usage computations and the first profile decode off the main
  thread.

## Measuring

Use the protocol in the 1.3.1 plan (`onehz-1.3.1-performance-plan.md`): five runs per
scenario, median, both test devices. Useful commands:

```powershell
adb shell dumpsys meminfo io.github.nevesz.onehz
adb shell dumpsys notification --noredact | grep -m1 'android.text=String'
adb shell "settings get system peak_refresh_rate; settings get secure miui_refresh_rate"
adb shell dumpsys batterystats --charged > bstats.txt
Select-String bstats.txt 'NotificationManagerService:post:io.github.nevesz'
```

The app itself never polls; a snapshot of CPU, memory and probes should be taken while
interacting with the device, not while it is idle.
