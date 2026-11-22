# Backlog

OneHZ is a personal project. Items here are intentions, not commitments.

## Planned

### PiP (picture-in-picture) video handling

- Current video detection relies on the foreground app and its media/codec state.
- PiP windows need a different signal; this is not implemented yet.

## Unlikely

These are documented as known limitations rather than promises.

### Support for other models

- OneHZ supports Samsung and Xiaomi/Redmi/POCO devices with at least two refresh rates.
- LTPO is no longer required.
- Other models still need real-device validation; PRs with fixtures and testing are
  welcome.

### AOD brightness on Xiaomi

- Xiaomi controls AOD brightness in its own AOD process/light sensor
  (`aod_brightness_level` is a factory feature, not a Settings key).
- There is no tested Settings/Shizuku route, so the option stays Samsung-only.

### Equal brightness at 30 Hz

- On the tested POCO X7 Pro, 30 Hz is dimmer at the panel level even though system
  brightness, framebuffer, and backlight are identical to 60/120 Hz.
- The vendor dimming/gamma routes reachable through Shizuku do not change it, and the
  root-only routes are out of scope.
- OneHZ keeps 30 Hz available and warns in the profile editor instead of faking a
  compensation by dimming the other rates.

## Non-goals

- Root-only features.
- AccessibilityService automation.
- Ads, analytics, or accounts.
- Paid features or a paywall.
- Automatic APK installation.
- Bundling Shizuku or other third-party apps.

## Ideas

- Sleep: compare percent-per-hour in Doze with screen-off time without Doze.
- Sleep: reapply the AOD rate after a Doze maintenance window.
- Export/import profiles.
- More granular per-app rules (e.g. per-orientation).
- Widget/quick-settings tile for the master switch.
- Additional translations.

To propose something, open a feature request. Explain the problem and the device you can
test on.
