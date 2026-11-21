# Backlog

OneHZ is a personal project. Items here are intentions, not commitments.

## Planned

### PiP (picture-in-picture) video handling

- Current video detection relies on the foreground app and its media/codec state.
- PiP windows need a different signal; this is not implemented yet.

## Unlikely

These are documented as known limitations rather than promises.

### Support for other brands/devices

- OneHZ is tied to Samsung's display settings and the S25+ was the only device tested.
- Without test devices, I cannot validate other models.
- PRs with real-device fixtures and testing are welcome.

### Remove the LTPO dependency

- The staircase and AOD behavior assume an LTPO panel with a low minimum rate.
- Removing this dependency would require a different control path and a different
  product decision.

## Non-goals

- Root-only features.
- AccessibilityService automation.
- Ads, analytics, or accounts.
- Paid features or a paywall.
- Automatic APK installation.
- Bundling Shizuku or other third-party apps.

## Ideas

- Export/import profiles.
- More granular per-app rules (e.g. per-orientation).
- Widget/quick-settings tile for the master switch.
- Additional translations.

To propose something, open a feature request. Explain the problem and the device you can
test on.
