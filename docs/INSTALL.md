# Install and update

**English** · [Português (Brasil)](INSTALL.pt-BR.md)

OneHZ is distributed as a signed APK on GitHub Releases. It is not on Google Play, and
it requires Shizuku.

## Requirements

- Samsung phone **or** Xiaomi/Redmi/POCO phone (HyperOS/MIUI) whose display exposes at
  least two refresh rates.
- Android 12 (API 31) or newer.
- Shizuku installed and running.
- The same signing key for every update.

Supported devices and the eligibility gate: [COMPATIBILITY](COMPATIBILITY.md).

## Shizuku

OneHZ was tested with the **thedjchi fork `v13.7.0-thedjchi`**:
<https://github.com/thedjchi/Shizuku/tree/v13.7.0-thedjchi>

The official Shizuku should work because OneHZ uses the standard API, but it was not
tested. If you want to match the tested setup, use the same fork version.

Start Shizuku through wireless debugging or root. After a reboot, Shizuku may need to be
started again; OneHZ reconnects when it becomes available and fails closed until then.

OneHZ does **not** use `AccessibilityService`. Accessibility services can interfere with
banking apps and other sensitive applications, and everything privileged goes through
Shizuku instead.

## Install

1. Install Shizuku and start it.
2. Download the APK from [GitHub Releases](../../releases).
3. Open the APK and install it. Android may ask you to allow installs from that source.
4. Open OneHZ and allow the Shizuku permission.
5. Follow the setup screen: compatibility, Shizuku, optional battery/Doze/notification
   permissions.
6. Turn on the master switch.

> If you are coming from a debug build signed with a different key, uninstall it first:
> `adb uninstall io.github.nevesz.onehz`.

## Beta builds

Beta builds are published as **pre-releases** on the same releases page and use the same
package name and signing key.

- Stable and beta cannot be installed side by side.
- A beta `versionCode` is higher than the current stable, so you can install it over
  1.0.1 and keep your settings.
- The future stable release has a higher `versionCode` than the beta, so beta users can
  move back to stable by installing it over the beta.
- In the app update checker, select **stable + pre-releases** to be notified about beta
  releases. The stable channel never offers a pre-release.
- Obtainium can also track pre-releases; enable that option in the app source settings.

## Automatic updates with Obtainium

[Obtainium](https://obtainium.imranr.dev/) can track OneHZ releases and notify you when a
new version is available:

1. Install Obtainium.
2. Add `https://github.com/neveSZ/onehz` as an app source, or use the badge in the
   README.
3. Let Obtainium check for updates and install new releases.

GitHub Releases and Obtainium use the same signed APK and signing key. The optional
in-app update checker is separate: it only opens the GitHub release page when you enable
it. Obtainium is the recommended way to receive automatic updates.

## Update

Install the new APK over the old one. Updates require the same signing key. Your
settings and profiles are kept. If Shizuku is running, the app reconnects automatically.

OneHZ can optionally check GitHub for a newer release when you open the app. The check
is **off by default**: enable it in **Settings → Updates**, accept the explanation, and
choose the interval (24 hours, 3 days, 7 days, or 30 days) and the channel (stable only
or stable plus pre-releases). It never runs in the background.

The in-app checker only notifies you about a release; it never downloads or installs an
APK.

## Uninstall

1. Turn off AOD brightness and use “Restore system brightness” (Samsung).
2. Uninstall the app.
3. If the AOD key remains:
   `adb shell settings delete system debug_aod_brightness`.

## Verify the APK

Every release includes a `.sha256` file next to the APK.

```powershell
Get-FileHash .\OneHZ-<version>.apk -Algorithm SHA256
apksigner verify --print-certs .\OneHZ-<version>.apk
```

The signing certificate SHA-256 is:

`D8:E8:24:B2:5F:6B:1D:29:75:BD:72:D6:89:5F:C4:7F:7B:EB:C2:05:78:41:F7:83:3C:25:67:0A:C2:04:F8:0A`

If the fingerprint does not match, do not install the APK. Only install builds from the
official GitHub Releases page.

## Recovery commands

Reset Samsung refresh-rate keys to the system default:

```powershell
adb shell settings put secure refresh_rate_mode 2
adb shell settings delete system peak_refresh_rate
adb shell settings delete system min_refresh_rate
```

Inspect the HyperOS keys (Xiaomi/Redmi/POCO):

```powershell
adb shell settings get secure miui_refresh_rate
adb shell settings get secure user_refresh_rate
```

OneHZ snapshots and restores these keys on release; the commands above are only for
manual recovery after the app was killed or uninstalled at an unusual moment.

Other questions are answered in the [FAQ](FAQ.md).
