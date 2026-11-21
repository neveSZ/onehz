# Releasing OneHZ

## Versioning

`version.properties` is the single source of truth:

```properties
versionName=1.0.0
versionCode=10000
```

Use Semantic Versioning and monotonically increasing `versionCode` values:

| Version | versionCode |
|---|---|
| 1.0.0 | 10000 |
| 1.0.1 | 10001 |
| 1.1.0 | 10100 |
| 1.1.0-beta.1 | 10091 |
| 1.1.0-beta.2 | 10092 |

A beta must use a `versionCode` lower than the upcoming stable so beta users can upgrade
to the final release. Tag format: `v1.0.0`, `v1.1.0-beta.1`. Tags containing `-` are
published as pre-releases.

## Keystore

The release keystore is stored outside the repository, for example:

```text
%USERPROFILE%\keys\onehz\onehz-release.jks
%USERPROFILE%\keys\onehz\keystore.properties
```

- Never commit the keystore or its password.
- Keep at least two backups (password manager + offline media).
- The signing certificate is the app identity; losing it prevents updates.
- If the password is exposed, re-encrypt the keystore with a new password without
  changing the certificate (e.g. `keytool -importkeystore` with the same alias/key).
- The documented certificate SHA-256 is in [README](../README.md) and
  [SECURITY](../SECURITY.md).

## GitHub secrets

Create an environment named `release` and add:

| Secret | Value |
|---|---|
| `ONEHZ_KEYSTORE_BASE64` | `.jks` file encoded as base64 |
| `ONEHZ_KEYSTORE_PASSWORD` | keystore password |
| `ONEHZ_KEY_ALIAS` | `onehz` |
| `ONEHZ_KEY_PASSWORD` | key password |
| `ONEHZ_CERT_SHA256` | expected certificate SHA-256, no colons, lowercase |

Encode the keystore locally **without printing the password**:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$env:USERPROFILE\keys\onehz\onehz-release.jks")) |
  Set-Clipboard
```

Paste the clipboard into the GitHub secret. Do not paste it into issues, chat, or docs.

## Pre-release checklist

- [ ] `version.properties` updated.
- [ ] `CHANGELOG.md` updated.
- [ ] `docs/releases/<version>.md` created (optional release notes).
- [ ] README known limitations/device table updated if needed.
- [ ] Tests and lint pass locally.
- [ ] `assembleRelease` produces a signed APK.
- [ ] APK installed and tested on the device.
- [ ] No secrets or keystore in `git status` / `git ls-files`.
- [ ] `gitleaks` (or equivalent) run.

## Release flow

1. Run the **Release** workflow with `publish = false` (dry run). It builds, verifies,
   and uploads the APK as a workflow artifact.
2. Download and install the artifact on the device. Run the manual checklist in
   [TESTING](TESTING.md).
3. Create and push the tag:

   ```powershell
   git tag -a v1.0.0 -m "OneHZ 1.0.0"
   git push origin main --tags
   ```

4. The workflow builds and publishes the GitHub Release with:
   - `OneHZ-<version>.apk`;
   - `OneHZ-<version>.apk.sha256`;
   - release notes (if `docs/releases/<version>.md` exists);
   - pre-release flag for tags containing `-`.
5. Download the published APK, verify the SHA-256 and signature, and install it.
6. Keep `mapping.txt` from the workflow artifact for crash deobfuscation (not attached
   publicly).

## Verifying the release

```powershell
Get-FileHash .\OneHZ-1.0.0.apk -Algorithm SHA256
apksigner verify --print-certs .\OneHZ-1.0.0.apk
aapt2 dump badging .\OneHZ-1.0.0.apk | Select-String "package:"
```

## Beta releases

- Use tags such as `v1.1.0-beta.1`.
- Mark the GitHub release as a pre-release.
- Use a lower `versionCode` than the future stable.
- Keep the same signing key.

## Rollback

A published release should not be overwritten. If a build is broken:

1. mark the release as a pre-release or edit its notes;
2. publish a new patch version with a higher `versionCode`;
3. never re-tag or replace an existing APK.
