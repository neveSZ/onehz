# Security Policy

## Supported versions

| Version | Supported |
|---|---|
| 1.1.x (including beta) | Yes |
| 1.0.x | Yes |
| < 1.0 | No |

## Reporting a vulnerability

Please **do not open a public issue** for a security problem. Use GitHub's private
vulnerability reporting:

<https://github.com/neveSZ/onehz/security/advisories/new>

Include, when possible:

- OneHZ version and APK SHA-256;
- device model, Android/One UI build, and Shizuku version;
- steps to reproduce;
- impact and any suggested fix;
- sanitized logs (remove personal data, package names you consider private, and tokens).

You should receive an initial response within a reasonable time. This is a personal
project without a bug bounty; fixes are best-effort.

## Scope

In scope:

- the OneHZ app and its AIDL service;
- command construction and validation in `OneHzUserService`;
- local settings writes and restore behavior;
- release signing and CI configuration.

Out of scope:

- Samsung firmware, One UI, or hidden settings behavior;
- Shizuku itself (report to the Shizuku project);
- devices modified with root or custom ROMs;
- builds downloaded from unofficial sources.

## Network access

The only outbound request is the opt-in update check:

- `https://api.github.com/repos/neveSZ/onehz/releases...`;
- enabled only after explicit consent, only when the app opens, and never in the
  background;
- no GitHub token or account is shipped in the APK;
- response URLs are accepted only from `https://github.com/neveSZ/onehz/releases/...`;
- OneHZ never downloads or installs an APK automatically.

## Verifying releases

Only install APKs from the official GitHub Releases page. Each release includes a
SHA-256 file, and the signing certificate SHA-256 is:

`D8:E8:24:B2:5F:6B:1D:29:75:BD:72:D6:89:5F:C4:7F:7B:EB:C2:05:78:41:F7:83:3C:25:67:0A:C2:04:F8:0A`

```powershell
apksigner verify --print-certs OneHZ-1.2.0.apk
```

If the fingerprint does not match, do not install the APK.
