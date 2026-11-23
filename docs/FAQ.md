# FAQ

**English** · [Português (Brasil)](FAQ.pt-BR.md)

### Is OneHZ free?

Yes. All features are free and open source (MIT). Donations through
<https://livepix.gg/onehz> are optional and never unlock features.

### Does OneHZ need root?

No. It uses **Shizuku**, which can run through wireless debugging or root. Root is not
required.

### Why Shizuku instead of accessibility?

AccessibilityService can interfere with banking apps and other sensitive applications.
OneHZ deliberately avoids it. Shizuku provides a narrower, explicit privileged channel.

### Why only Samsung and Xiaomi/Redmi/POCO?

The display control uses vendor-specific settings: Samsung's `refresh_rate_mode` plus
`peak/min`, and HyperOS's `miui_refresh_rate` (or `user_refresh_rate`). Other
manufacturers have different routes, so the app blocks them instead of pretending to
work.

### Why does the display need two refresh rates?

The staircase switches between rates. A single-rate panel has nothing to switch to, so
the app fails closed. **LTPO is not required**; a 120/90/60/30 panel is enough.

### What are Economy, Balanced and Performance?

They are three built-in profiles generated from the panel:

- **Economy** uses the lowest useful bands and is applied automatically while the system
  battery saver is on;
- **Balanced** is the default profile and uses the highest rate above 60 Hz when available;
- **Performance** keeps a resting floor of 60 Hz or higher.

You can select the active profile on Home, edit any built-in profile and restore it to the
device defaults. Custom profiles are also supported.

### Why does the card show one profile selected and another in use?

When **Automatic Economy** is on and the system battery saver is active, OneHZ uses
Economy even if another profile is selected. The Home card shows the selected profile and
the profile in use. Turn off **Automatic Economy** in **Manage profiles** to always use the
selected profile.

### Why does my app profile become Economy with battery saver on?

When the system battery saver is on and **Automatic Economy** is enabled, Economy applies
to every app unless the app keeps its own profile. Open **Apps → Configured**, tap the app
and turn on **“Keep this app’s profile during battery saver”**. The same switch is offered
when you add the app. The app row then shows **Keeps its profile** instead of the Economy
badge.

### What happens on a panel with only two refresh rates?

All three profiles use the same two bands and differ only by how long the maximum is held
(1 s / 2 s / 5 s). The minimum is the resting rate until the next touch, so there is only
one adjustable hold on those panels.

### What refresh rate does AOD use?

The lowest rate exposed by the panel. There is no separate AOD frequency picker. If the
panel rejects a very low rate in AOD, the engine falls back to 30 Hz and logs the failure.

### Can I back up my settings?

Yes. Settings → **Backup and restore** exports a versioned JSON file through the system
file picker. It includes all profiles, app assignments, video, AOD settings, Sleep
settings and the master switch. Theme, language, diagnostics, Sleep measurements and the
system Doze exceptions are not included. Importing replaces the current behavior
configuration and adapts unsupported rates to the current panel. A backup created before
Sleep settings existed does **not** change the current Sleep settings.

### Does AOD brightness work on Xiaomi?

No. Xiaomi's AOD brightness is automatic and controlled by the AOD process/light
sensor (`aod_brightness_level` is a factory feature, not a Settings key). The Samsung
`debug_aod_brightness` route does not exist there, so the brightness row is shown as a
disabled switch that explains the panel manages it.

### Why is the screen darker at 30 Hz on Xiaomi?

On the tested POCO X7 Pro the 30 Hz panel mode itself is dimmer: system brightness,
framebuffer, and reported backlight are identical to 60/120 Hz, but the panel/vendor PQ
applies a different gamma. The vendor knobs reachable through Shizuku (`shell`, no root)
do not change it, so no app can compensate. OneHZ keeps 30 Hz available and shows a
warning in the profile editor. See
[COMPATIBILITY](COMPATIBILITY.md#30-hz-brightness-on-hyperos).

### Which device was tested?

A Samsung Galaxy S25+ (`SM-S936B`), Android 17, One UI 9.0, build `S936BXXUDDZJB`,
and a POCO X7 Pro (`2412DPC0AG`, `rodin`), Android 16, HyperOS 3.0
(`OS3.0.304.0.WOJMIXM`). See [COMPATIBILITY](COMPATIBILITY.md).

### How do I get beta builds?

Beta builds are published as pre-releases on the same
[releases page](https://github.com/neveSZ/onehz/releases):

- install the APK manually from the pre-release, or
- enable **stable + pre-releases** in the in-app update checker, or
- enable pre-releases for the OneHZ source in Obtainium.

A beta uses the same signing key and a higher `versionCode` than the current stable, so
it installs over the stable release and keeps your settings. The next stable has a higher
`versionCode`, so installing it over the beta returns you to the stable channel.

### What changed in 1.2.1?

- The AOD rate switch is available on every supported device. Non-Samsung devices show
  the AOD brightness row as a disabled switch with an explanation.
- The Sleep detail was redesigned: one master on Home, behavior options always available,
  one dismissible warning, a status line only for active/paused/failure states, and
  consistent icons on the switches.
- Video settings are clearer: the minimum rate is separate from the detection rules, and
  the rules plus the global forced-app list open in a focused sheet.
- Preferences moved to DataStore with a one-time migration. Export a backup before
  downgrading, because the legacy XML is cleared after the migration.
- Baseline Profile, bundled icons, edge-to-edge, ViewModels, and release tooling.
- Distributed through Shizu Store (recommended), GitHub Releases, and Obtainium.

### What changed in 1.2.1-beta.1?

- The Home AOD switch is now the only AOD master; the detail screen no longer repeats it.
- AOD rate and brightness are independent again. Turning the rate off no longer hides
  brightness or its restore action.
- The AOD master is migrated from the old rate/brightness choices, and existing backups
  stay compatible.
- With Sleep off, the detail keeps the behavior options, wake-up apps and measurements
  visible and editable; test and pause require Sleep on.
- The Home Sleep subtitle now says "Off" when Sleep is disabled.

### What changed in 1.2.0?

- Reusable **Economy, Balanced and Performance** profiles, plus custom profiles.
- Manual profile selection with optional **Automatic Economy** while the system battery
  saver is on.
- Per-app assignments now reference a saved profile instead of copying its staircase.
- Per-profile video minimum and detection rules; the forced-app list is global.
- AOD always uses the panel minimum; the AOD brightness action now has a system restore.
- Optional **Sleep** (forced Doze) with a first-enable warning, guards and local
  measurements.
- Backup includes Sleep settings; a backup without Sleep does not change them.
- Home was reorganized; AOD and Sleep now have detail screens.
- Portuguese profile names are **Economia / Equilibrado / Desempenho**.

### What is Sleep (Doze)?

Sleep is an optional feature that asks Android to enter **Doze** as soon as the screen
turns off. Doze is Android's deep sleep state. Regular calls and alarms still work, but
notifications and internet calls (WhatsApp, Telegram, etc.) may be delayed. Sleep is off
by default, asks for confirmation before the first enable, and can be paused for one hour
at any time. See [SLEEP](SLEEP.md).

### Will updating change my profiles?

The staircases are kept, but the model changes. Normal becomes Balanced, ECO becomes
Economy, and a new Performance profile is created. Copied per-app profiles become
references to a matching profile (or to a deduplicated custom profile). Preferences are
migrated to DataStore on the first start; the old XML is cleared, so downgrading after the
migration shows defaults for settings changed later. If you want the old layout back, edit
the profiles or restore the built-ins to their device defaults.

### Why does control stop after a reboot?

Shizuku may need to be restarted after a reboot (wireless debugging/ADB). OneHZ
reconnects when Shizuku becomes available and fails closed until then.

### Why didn't the per-app profile apply right after opening the app?

Older versions re-read the foreground app only on touch-down, so an app opened from the
launcher, recents, or a notification could still be treated as the previous app until you
touched inside it. OneHZ now follows task-stack changes while the screen is on and falls
back to a touch-up read when the platform rejects that listener. If it still happens,
touch once inside the app and send a diagnostics report.

### Why is OneHZ not on Google Play?

It uses Shizuku, `QUERY_ALL_PACKAGES`, and a battery-optimization exemption, and it
modifies hidden Samsung and HyperOS settings. Distribution through Shizu Store, GitHub
Releases and Obtainium keeps the project simple and transparent.

### Does OneHZ collect data?

No personal data is collected. The optional update checker uses the internet only after
you enable it; there are no analytics, ads, or telemetry. See [PRIVACY](../PRIVACY.md).

### How do I open Diagnostics?

Open **Settings → Diagnostics**. The report is generated locally, can be paused or cleared
from that screen, and is only shared when you copy it.

### The AOD brightness is stuck after uninstalling. What do I do?

Run:

```powershell
adb shell settings delete system debug_aod_brightness
```

or reinstall OneHZ and use **Settings → Diagnostics → Restore system brightness**.

### Why is the battery-saver profile called Economy?

“Economy” is the user-facing name for the lowest-band profile. “ECO” remains only in old
changelog entries and internal legacy keys; it is no longer the profile name.

### Why does the rate not change in some apps?

Some apps/games set their own preferred frame rate or use surface-level frame-rate
overrides. OneHZ controls the system peak/min values; it cannot override every app-level
decision. Please report the app and the diagnostic state.

### Does it work with another refresh-rate controller?

No. Running two controllers at the same time causes conflicting writes. Disable the
other one.

### The rate stayed at a fixed value after the app was killed. How do I restore it?

Open OneHZ again; on the next Shizuku connection it restores the pending session. If that
does not work, on Samsung run:

```powershell
adb shell settings put secure refresh_rate_mode 2
adb shell settings delete system peak_refresh_rate
adb shell settings delete system min_refresh_rate
```

On Xiaomi/Redmi/POCO, check the keys with:

```powershell
adb shell settings get secure miui_refresh_rate
adb shell settings get secure user_refresh_rate
```

Grant the optional battery exemption to reduce the chance of the app being killed.

### Why 1 Hz is not listed?

OneHZ lists the rates exposed by the system for the current panel. On the tested S25+,
the system exposes 10 Hz as the minimum even though the panel is LTPO. The app does not
invent rates the firmware does not report.

### Can I build it myself?

Yes. See [BUILDING](BUILDING.md). You need JDK 25, SDK 37, and build-tools 36.0.0.

### Can I use it on another Samsung or Xiaomi model?

Yes, if it has at least two refresh rates and the vendor route exists. It is untested
on other models. Report your model, build, panel rates, and diagnostics if you try.

### Why are English and Spanish marked as AI-generated?

Because they are. The author reviewed Portuguese. English and Spanish were generated and
reviewed for consistency, but not by native speakers. Corrections are welcome.

### Does Sleep force Doze? Will I miss notifications?

Sleep is optional and off by default. When enabled, it asks Android to enter Doze as soon
as the screen turns off, so network and background work wait for the maintenance windows.
Traditional calls and alarms still wake the device, but an incoming VoIP call that depends
on a background connection may not arrive until the next maintenance window. Use **Pause
for 1 hour**, the time window, or add the app to **Apps that may wake the device** when you
need it in real time.

### Does Sleep pause when music is playing?

By default, yes: the music guard pauses Doze while audio is playing. Turn on **Doze with
music** in the Sleep detail to keep idle even during playback. Some devices may interrupt
streaming or background audio in deep idle, so the option is off by default. The guard is
re-evaluated when playback starts or stops and when the charger is connected or removed;
you do not need to turn the screen off again.

### Does Sleep keep Doze active while charging?

By default, yes. **Doze while charging** stays on, so the charger does not pause Sleep.
Turn it off in the Sleep detail to pause Doze while the device is plugged in. The charger
state is re-evaluated when the cable is connected or removed.

### How are the Sleep numbers measured?

Everything is local. “Last 24 hours” is a rolling window: screen-off and Doze time are the
sums of the recorded spans clipped to that window. “Last rest” is the most recent
continuous screen-off period of at least 30 minutes; screen-on gaps shorter than two
minutes are merged. The battery rate is the session's level drop per hour and is shown as
`—` for sessions shorter than 15 minutes or when the level increased (charging). The
figures include the Always On Display and are measured by OneHZ, not by the system; they
are never uploaded. If the app was not running for the whole window, the totals are
prefixed with “partial”.
