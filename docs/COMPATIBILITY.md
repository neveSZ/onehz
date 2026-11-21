# Compatibility

## Tested configuration

| Item | Value |
|---|---|
| Device | Samsung Galaxy S25+ (`SM-S936B`, `pa2q`) |
| Android | 17 (API 37) |
| One UI | 9.0 (`ro.build.version.oneui=90000`) |
| Build | `S936BXXUDDZJB` |
| Security patch | 2026-10-05 |
| Resolution | 1080 × 2340 (FHD+) |
| Panel rates | 120, 80, 60, 48, 40, 30, 26.67, 24, 20, 10 Hz |
| Shizuku | thedjchi fork `v13.7.0-thedjchi` |
| OneHZ | 1.0.0 |

Everything else is **untested**.

## Eligibility gate

`LtpoGate.evaluate()` accepts a device only when all checks pass:

1. `Build.MANUFACTURER` is `samsung` (case-insensitive);
2. Samsung's `SEC_FLOATING_FEATURE_LCD_CONFIG_HFR_MODE` is greater than 1;
3. the minimum supported panel rate is ≤ 30 Hz.

If any check fails, OneHZ shows “Device not supported” and does not change anything.
This is intentional: without a low-frequency LTPO panel, the staircase and AOD behavior
would not make sense.

Check the device locally:

```powershell
adb shell getprop ro.product.manufacturer
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.oneui
adb shell getprop ro.build.display.id
adb shell dumpsys display | Select-String "mSupportedRefreshRates"
```

## Supported devices

- Samsung phones with an LTPO panel that pass the gate.
- Tested only on the S25+ above.

## Not supported / untested

- non-Samsung devices (rejected by the gate);
- Samsung phones without LTPO (rejected);
- foldables and cover screens (untested);
- tablets (untested);
- WQHD+ mode on the tested device (untested);
- Android 12–16 (minSdk allows them, but only Android 17 was tested);
- other Shizuku versions (only the fork above was tested).

## Firmware dependencies

OneHZ reads and writes Samsung-specific settings:

- `refresh_rate_mode`;
- `peak_refresh_rate`, `min_refresh_rate`;
- `debug_aod_brightness`;
- Samsung battery-saver rate keys.

A future One UI update may rename, restrict, or remove these settings. In that case,
OneHZ may stop controlling the display or fail closed. Please report the firmware build
and the diagnostic report.

## Shizuku

- Tested with the thedjchi fork `v13.7.0-thedjchi`.
- The official Shizuku should work because the app uses the standard API, but it was not
  tested.
- After a reboot, Shizuku may need to be started again through wireless debugging or
  ADB. OneHZ reconnects when Shizuku becomes available.

## Reporting compatibility

Include:

- model, product, Android, One UI, build, security patch;
- panel rates from `dumpsys display`;
- Shizuku version;
- whether the gate shows OK;
- what works and what does not.

Do not post personal data.
