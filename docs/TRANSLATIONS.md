# Translations

## Status

| Language | File | Status |
|---|---|---|
| English | `app/src/main/res/values/strings.xml` | Base strings, **AI-generated**, not reviewed by a native speaker |
| Portuguese (Brazil) | `app/src/main/res/values-pt-rBR/strings.xml` | Reviewed by the author |
| Spanish | `app/src/main/res/values-es/strings.xml` | AI-generated, reviewed for consistency, **not reviewed by a native speaker** |

The README, license, and documentation follow the same policy where applicable.
Corrections from native speakers are very welcome.

User-facing documentation is bilingual: `docs/FEATURES`, `docs/INSTALL`, `docs/FAQ`, and
`docs/COMPATIBILITY` have an English file and a `.pt-BR.md` counterpart. Developer
documentation (architecture, building, testing, releasing, translations, backlog) is
English-only.

## Files

- English base: `app/src/main/res/values/strings.xml`
- Portuguese: `app/src/main/res/values-pt-rBR/strings.xml`
- Spanish: `app/src/main/res/values-es/strings.xml`
- Locale list: `app/src/main/res/xml/locales_config.xml`
- In-app language selector: `core/LanguageManager.kt`

## Rules

- Keep the same keys in all three files.
- Keep placeholders exactly as in the base string (`%1$s`, `%1$d`, `%%`).
- Keep `plurals` quantities (`one`, `other`).
- Do not translate:
  - `OneHZ`, `Shizuku`, `AOD`, `AMOLED`, `Doze`;
  - package names, commands, or settings keys;
  - the `debug_aod_brightness` key.
- Use the platform's own term for battery saver:
  - en: battery saver;
  - pt-BR: economia de bateria;
  - es: ahorro de batería.
- Translate the profile names:
  - Economy / Economia / Economía;
  - Balanced / Balanceado / Equilibrado;
  - Performance / Performance / Rendimiento.
- “ECO” may appear only in historical changelog entries and legacy key names; it is no
  longer the user-facing profile name.
- User-facing text belongs in resources, never hardcoded in Kotlin.

## Glossary

| en | pt-BR | es |
|---|---|---|
| refresh rate | taxa de atualização | tasa de actualización |
| tier / level | faixa / nível | nivel |
| hold | tempo de espera | tiempo de espera |
| resting rate | taxa de repouso | tasa de reposo |
| profile | perfil | perfil |
| economy profile | perfil Economia | perfil Economía |
| balanced profile | perfil Balanceado | perfil Equilibrado |
| performance profile | perfil Performance | perfil Rendimiento |
| battery saver | economia de bateria | ahorro de batería |
| backup and restore | backup e restauração | copia de seguridad y restauración |
| AOD brightness | brilho do AOD | brillo del AOD |
| foreground app | app em primeiro plano | aplicación en primer plano |
| diagnostics | diagnóstico | diagnóstico |
| wake pulse | pulso de despertar | pulso de despertar |

## How to contribute

1. Edit the relevant `strings.xml`.
2. Keep the XML valid and the key order consistent.
3. Build and run `.\gradlew.bat lintDebug` to catch missing/unused keys.
4. Open a PR describing your language background and what you reviewed.
5. If you used an LLM, disclose it.

Do not claim native review unless you are a native speaker. The project intentionally
marks English and Spanish as AI-generated until a native speaker reviews them.
