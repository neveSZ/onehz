# Translations

## Status

| Language | File | Status |
|---|---|---|
| English | `app/src/main/res/values/strings*.xml` | Base strings, **AI-generated**, not reviewed by a native speaker |
| Portuguese (Brazil) | `app/src/main/res/values-pt-rBR/strings*.xml` | Reviewed by the author |
| Spanish | `app/src/main/res/values-es/strings*.xml` | AI-generated, reviewed for consistency, **not reviewed by a native speaker** |

Strings are split across `strings*.xml` feature files (`strings_control.xml`,
`strings_usage.xml`, `strings_ux.xml`, `strings_sleep.xml`). Android merges all
`values/*.xml` files at build time, so keys must be unique across the whole locale.

The README, license, and documentation follow the same policy where applicable.
Corrections from native speakers are very welcome.

User-facing documentation is bilingual: `docs/FEATURES`, `docs/INSTALL`, `docs/FAQ`, and
`docs/COMPATIBILITY` have an English file and a `.pt-BR.md` counterpart. Developer
documentation (architecture, building, testing, releasing, translations, backlog) is
English-only.

## Files

- English base: `app/src/main/res/values/strings*.xml` (`strings.xml` plus the feature
  files)
- Portuguese: `app/src/main/res/values-pt-rBR/strings*.xml`
- Spanish: `app/src/main/res/values-es/strings*.xml`
- Feature files: `strings_control.xml`, `strings_usage.xml`, `strings_ux.xml` and
  `strings_sleep.xml` in each locale folder
- Locale list: `app/src/main/res/xml/locales_config.xml`
- In-app language selector: `core/LanguageManager.kt`

## Rules

- Keep the same keys in all three locales, across `strings.xml` and the split
  `strings*.xml` feature files.
- State-aware switch copy uses `_on` / `_off` suffixes (`sleep_doze_music_on`, or
  `aod_hint_on` / `aod_hint_off`). Keep the title stable and describe the effect of the
  current state; see [UI guidelines](UI_GUIDELINES.md).
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
  - Balanced / Equilibrado / Equilibrado;
  - Performance / Desempenho / Rendimiento.
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
| selected profile | perfil selecionado | perfil seleccionado |
| profile in use | perfil em uso | perfil en uso |
| economy profile | perfil Economia | perfil Economía |
| balanced profile | perfil Equilibrado | perfil Equilibrado |
| performance profile | perfil Desempenho | perfil Rendimiento |
| automatic Economy | Economia automática | Economía automática |
| battery saver | economia de bateria | ahorro de batería |
| video minimum | mínimo de vídeo | mínimo de vídeo |
| Doze exceptions | exceções do Doze | excepciones de Doze |
| Sleep session | sessão de sono | sesión de sueño |
| backup and restore | backup e restauração | copia de seguridad y restauración |
| AOD brightness | brilho do AOD | brillo del AOD |
| foreground app | app em primeiro plano | aplicación en primer plano |
| diagnostics | diagnóstico | diagnóstico |
| wake pulse | pulso de despertar | pulso de despertar |
| Sleep | Sono | Sueño |
| Doze with music | Doze com música | Doze con música |
| state-aware description | descrição por estado | descripción por estado |
| rest period | descanso | descanso |
| maintenance window | janela de manutenção | ventana de mantenimiento |

## How to contribute

1. Edit the relevant `strings.xml`.
2. Keep the XML valid and the key order consistent.
3. Build and run `.\gradlew.bat lintDebug` to catch missing/unused keys.
4. Open a PR describing your language background and what you reviewed.
5. If you used an LLM, disclose it.

Do not claim native review unless you are a native speaker. The project intentionally
marks English and Spanish as AI-generated until a native speaker reviews them.
