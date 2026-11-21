# FAQ

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

### Does AOD brightness work on Xiaomi?

No. Xiaomi's AOD brightness is automatic and controlled by the AOD process/light
sensor (`aod_brightness_level` is a factory feature, not a Settings key). The Samsung
`debug_aod_brightness` route does not exist there, so the option is hidden on Xiaomi.

### Why is the screen darker at 30 Hz on Xiaomi?

On the tested POCO the 30 Hz panel mode itself is dimmer: system brightness, framebuffer,
and reported backlight are identical to 60/120 Hz, but the panel/vendor PQ applies a
different gamma. The vendor knobs reachable through Shizuku (`shell`, no root) do not
change it, so no app can compensate. OneHZ keeps 30 Hz available and shows a warning in
the profile editor. See [COMPATIBILITY](COMPATIBILITY.md#30-hz-brightness-on-hyperos).

### Which device was tested?

A Samsung Galaxy S25+ (`SM-S936B`), Android 17, One UI 9.0, build `S936BXXUDDZJB`,
and a POCO `2412DPC0AG` (`rodin`), Android 16, HyperOS 3.0
(`OS3.0.304.0.WOJMIXM`). See [COMPATIBILITY](COMPATIBILITY.md).

### Why does control stop after a reboot?

Shizuku may need to be restarted after a reboot (wireless debugging/ADB). OneHZ
reconnects when Shizuku becomes available and fails closed until then.

### Why is OneHZ not on Google Play?

It uses Shizuku, `QUERY_ALL_PACKAGES`, and a battery-optimization exemption, and it
modifies hidden Samsung settings. Sideloading through GitHub Releases keeps the project
simple and transparent.

### Does OneHZ collect data?

No personal data is collected. The optional update checker uses the internet only after
you enable it; there are no analytics, ads, or telemetry. See [PRIVACY](../PRIVACY.md).

### The AOD brightness is stuck after uninstalling. What do I do?

Run:

```powershell
adb shell settings delete system debug_aod_brightness
```

or reinstall OneHZ and use **About/Diagnostics → Restore system brightness**.

### Why is the battery-saver profile called ECO?

“ECO” is a neutral, short acronym that avoids confusion with platform/API names. The
descriptive text still says “battery saver” / “economia de bateria” / “ahorro de
batería”.

### Why does the rate not change in some apps?

Some apps/games set their own preferred frame rate or use surface-level frame-rate
overrides. OneHZ controls the system peak/min values; it cannot override every app-level
decision. Please report the app and the diagnostic state.

### Does it work with another refresh-rate controller?

No. Running two controllers at the same time causes conflicting writes. Disable the
other one.

### The rate stayed at 120/60/10 after the app was killed. How do I restore it?

Open OneHZ again; on the next Shizuku connection it restores the pending session. If that
does not work, run:

```powershell
adb shell settings put secure refresh_rate_mode 2
adb shell settings delete system peak_refresh_rate
adb shell settings delete system min_refresh_rate
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
