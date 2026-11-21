# Releasing OneHZ

## Versioning

`version.properties` is the single source of truth:

```properties
versionName=1.0.0
versionCode=10000
```

Use Semantic Versioning and monotonically increasing `versionCode` values.

From **1.0.1 onward**, the release workflow expects:

```text
base  = major*1,000,000 + minor*10,000 + patch*100
beta  = base + 1..99
final = base + 100
```

Examples:

| Version | versionCode |
|---|---|
| 1.0.0 (already published) | 10000 |
| 1.0.1-beta.1 | 1,000,101 |
| 1.0.1-beta.2 | 1,000,102 |
| 1.0.1 | 1,000,200 |
| 1.1.0-beta.1 | 1,010,001 |
| 1.1.0-beta.2 | 1,010,002 |
| 1.1.0 | 1,010,100 |

`1.0.0` is the historical exception (published as `10000`). The jump to
`1,000,200` is valid because Android only requires the new `versionCode` to be higher
than the installed one.

A beta must use a `versionCode` higher than the current stable (so testers can install it
over the stable APK) and lower than the upcoming stable (so beta users can move to the
final release). Tag format: `v1.0.0`, `v1.0.1-beta.1`, `v1.1.0-beta.2`. Tags containing
`-` are published as **pre-releases**; stable tags become the “Latest” release.

### Beta flow

1. Bump `version.properties` to the beta version and code.
2. Commit on `main`.
3. Create and push the beta tag:
   ```powershell
   git tag -a v1.0.1-beta.1 -m "OneHZ 1.0.1-beta.1"
   git push origin main --tags
   ```
4. Testers install the pre-release APK manually.
5. When validated, bump `version.properties` to the stable version/code, commit, and tag
   `v1.0.1`.
6. Stable users never receive beta automatically. Beta users upgrade to stable because the
   stable `versionCode` is higher.

The same package (`io.github.nevesz.onehz`) and signing key are used for both channels.
Beta and stable cannot be installed side by side.

## In-app update checker

The optional in-app checker reads the public GitHub Releases API:

- stable channel: `/repos/neveSZ/onehz/releases/latest`;
- stable plus pre-releases: `/repos/neveSZ/onehz/releases?per_page=30`, compared locally.

Drafts are ignored. Pre-releases are offered only when the user selects the pre-release
channel. The app compares the tag against `versionName`, so a release must use a tag that
matches `version.properties` (`v1.0.0` for `1.0.0`). Publishing a new APK with the same
`versionName` will not trigger an update warning.

The repository must stay public for anonymous users to read releases. Never add a GitHub
token to the app. If the repository becomes private, the in-app check cannot work.

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
   git tag -a v1.0.1 -m "OneHZ 1.0.1"
   git push origin main v1.0.1
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
Get-FileHash .\OneHZ-1.0.1.apk -Algorithm SHA256
apksigner verify --print-certs .\OneHZ-1.0.1.apk
aapt2 dump badging .\OneHZ-1.0.1.apk | Select-String "package:"
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
