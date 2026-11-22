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

### What happens on a panel with only two refresh rates?

All three profiles use the same two bands and differ only by how long the maximum is held
(1 s / 2 s / 5 s). The minimum is the resting rate until the next touch, so there is only
one adjustable hold on those panels.

### What refresh rate does AOD use?

The lowest rate exposed by the panel. There is no separate AOD frequency picker. If the
panel rejects a very low rate in AOD, the engine falls back to 30 Hz and logs the failure.

### Can I back up my settings?

Yes. Settings → **Backup and restore** exports a versioned JSON file through the system
file picker. It includes all profiles, app assignments, video, AOD settings and the master
switch. Theme and language are not included. Importing replaces the current behavior
configuration and adapts unsupported rates to the current panel.

### Does AOD brightness work on Xiaomi?

No. Xiaomi's AOD brightness is automatic and controlled by the AOD process/light
sensor (`aod_brightness_level` is a factory feature, not a Settings key). The Samsung
`debug_aod_brightness` route does not exist there, so the option is hidden on Xiaomi.

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
it installs over 1.0.1 and keeps your settings. The next stable has a higher
`versionCode`, so installing it over the beta returns you to the stable channel.

### What changed in 1.1.0?

- Xiaomi/Redmi/POCO support (HyperOS 2/3, with a HyperOS 1 fallback).
- The LTPO requirement was replaced by a capability check: at least two refresh rates.
- New default profiles for fresh installs: normal **maximum → 60 Hz → minimum**
  (2 s / 5 s) and ECO **60 Hz → minimum** (2 s).
- Configurable video detection rules and a forced-app list.
- Diagnostics now include screen brightness/mode, battery-saver state, and the active
  rate route.

### What changed in the reusable profiles beta?

- Economy, Balanced and Performance profiles generated from the panel.
- Manual profile selection, with automatic Economy while battery saver is on.
- Custom profiles, rename/edit/delete and “Restore default” for the built-ins.
- Per-app profiles now reference a saved profile instead of copying its staircase.
- Video settings belong to each profile.
- AOD always uses the panel minimum.
- Versioned JSON backup and restore.

### Will updating change my profiles?

The staircases are kept, but the model changes. Normal becomes Balanced, ECO becomes
Economy, and a new Performance profile is created. Copied per-app profiles become
references to a matching profile (or to a deduplicated custom profile). The old
SharedPreferences keys remain for safety. If you want the old layout back, edit the
profiles or restore the built-ins to their device defaults.

### Why does control stop after a reboot?

Shizuku may need to be restarted after a reboot (wireless debugging/ADB). OneHZ
reconnects when Shizuku becomes available and fails closed until then.

### Why is OneHZ not on Google Play?

It uses Shizuku, `QUERY_ALL_PACKAGES`, and a battery-optimization exemption, and it
modifies hidden Samsung and HyperOS settings. Sideloading through GitHub Releases keeps
the project simple and transparent.

### Does OneHZ collect data?

No personal data is collected. The optional update checker uses the internet only after
you enable it; there are no analytics, ads, or telemetry. See [PRIVACY](../PRIVACY.md).

### The AOD brightness is stuck after uninstalling. What do I do?

Run:

```powershell
adb shell settings delete system debug_aod_brightness
```

or reinstall OneHZ and use **About/Diagnostics → Restore system brightness**.

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

### How are the Sleep numbers measured?

Everything is local. “Last 24 hours” is a rolling window: screen-off and Doze time are the
sums of the recorded spans clipped to that window. “Last rest” is the most recent
continuous screen-off period of at least 30 minutes; screen-on gaps shorter than two
minutes are merged. The battery rate is the session's level drop per hour and is shown as
`—` for sessions shorter than 15 minutes or when the level increased (charging). The
figures include the Always On Display and are measured by OneHZ, not by the system; they
are never uploaded. If the app was not running for the whole window, the totals are
prefixed with “partial”.
