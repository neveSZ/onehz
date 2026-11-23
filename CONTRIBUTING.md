# Contributing

OneHZ is a personal project maintained on a best-effort basis. There is no guarantee
that issues or pull requests will be reviewed or merged.

## Before you open an issue

- Read [README.md](README.md), [docs/FAQ.md](docs/FAQ.md), and
  [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md).
- Make sure Shizuku is running and OneHZ is enabled.
- Try the diagnostics screen: **Settings → Diagnostics → Copy diagnostics**.
- Remove personal data (package names you consider private, account names, tokens).

## Bug reports

Use the bug report template and include:

- OneHZ version, device model, Android/One UI or HyperOS build, and Shizuku version;
- panel refresh rates reported by the system;
- steps to reproduce and expected/actual behavior;
- the diagnostic report, sanitized;
- whether the issue happens after a reboot or after Shizuku restarts.

## Feature requests

Explain the problem first, then the proposed behavior. Keep in mind the tested scope:
Samsung and Xiaomi/Redmi/POCO devices with at least two refresh rates, two tested
devices, and no AccessibilityService.

## Pull requests

- Keep changes focused and explain the motivation.
- Use **English** for code, comments, logs, and commit messages.
- Put user-facing text in `app/src/main/res/values/` and add PT-BR/ES translations when
  possible.
- Add or update unit tests for pure logic.
- When behavior or visible UI changes, update `CHANGELOG.md`, the relevant docs
  (`FEATURES`, `FAQ`, `SLEEP`, `INSTALL`) and the `fastlane` metadata. Screenshots come
  from the final build; see [docs/RELEASING.md](docs/RELEASING.md).
- Run:

  ```powershell
  .\gradlew.bat testDebugUnitTest
  .\gradlew.bat lintDebug
  .\gradlew.bat assembleDebug
  ```

- Do not commit keystores, `keystore.properties`, `local.properties`, APKs, build
  outputs, or analysis of third-party applications.
- Do not copy code or assets from closed-source apps.
- If you used an LLM, disclose it in the PR description. You are responsible for
  reviewing and testing the result.
- Mention the device and Android/One UI or HyperOS version used for testing.

## Translations

Translation corrections are welcome, especially for English and Spanish, which are
AI-generated and not reviewed by native speakers. See
[docs/TRANSLATIONS.md](docs/TRANSLATIONS.md).

## Code style

- Kotlin, 4-space indentation, trailing commas.
- Comments explain *why*, not *what*. No ticket IDs, no third-party app names.
- Switch descriptions explain the effect of the current state; see
  [docs/UI_GUIDELINES.md](docs/UI_GUIDELINES.md).
- Prefer small, testable functions and pure parsers for protocol/format logic.
- Do not add network permissions without an explicit privacy review.

## Code of conduct

By participating, you agree to follow [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
