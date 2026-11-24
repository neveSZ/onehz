# UI copy guidelines

This document covers user-facing copy and layout across the app, with a focus on
state-aware switch descriptions, plain language, and consistent spacing. It applies to
every screen, sheet, dialog and notification.

## Stable title, state-aware description

The title names the setting and never changes with its value. The supporting line explains
what the **current state** does. The switch already communicates on/off, so the description
must not repeat "enabled" or "disabled".

Good:

| Title | On | Off |
|---|---|---|
| Doze with music | Doze is ignoring music playback. | Doze pauses while music is playing. |
| Automatic Economy | Activates Economy when system battery saver turns on. | Keeps the selected profile when battery saver turns on. |
| Only within a time window | Doze is only used between 23:00 and 07:00. | Doze can be used at any time. |

Avoid:

- "Enable this option to…" in both states;
- "On: …" / "Off: …" prefixes;
- unexplained jargon (`USAGE_MEDIA`, `mState`, shell commands);
- changing the title to describe the state;
- hiding a warning only in the state description. Warnings stay in a static banner.

## When to use it

Use state-aware copy when the effect differs in a way the user cannot infer from the title:
AOD rate and brightness, Sleep guards, video rules, automatic Economy, per-app exceptions,
and update checks.

Do not use it for:

- self-evident actions ("Open diagnostics");
- destructive or one-way actions whose state is not reversible from the same row;
- rows that are permanently disabled (for example, AOD brightness on a panel that does not
  support it); keep a static explanation there.

## Implementation

`ToggleRow` accepts `descriptionOn` and `descriptionOff`; `description` remains the static
fallback:

```kotlin
ToggleRow(
    title = stringResource(R.string.sleep_doze_music_title),
    descriptionOn = stringResource(R.string.sleep_doze_music_on),
    descriptionOff = stringResource(R.string.sleep_doze_music_off),
    checked = ui.dozeWithMusic,
    onCheckedChange = { on -> /* persist and reconcile */ },
)
```

Use `stringResource` with the current value for formatted copy, such as brightness
percentage or window times. Do not build sentences by concatenating translated fragments.

Translation keys follow the `_on` / `_off` suffix convention documented in
[TRANSLATIONS.md](TRANSLATIONS.md). Titles stay in one key.

## Layout

- Keep both descriptions within two lines at the default font size and at 200% font scale.
- Keep the two versions similar in length so the row does not jump when toggled.
- Do not use `maxLines` to hide a consequence; shorten the copy instead.

## Accessibility

- The row is one switch target; the title and supporting line are merged semantics.
- Confirm with TalkBack that it reads the title, the current state, and the effect once.
  Do not add a second `stateDescription` that repeats the visible text.
- The dynamic copy must be understandable without color or icon changes.
- Check at 200% font scale and with the screen reader on.

## Voice and terminology

- Write for someone who knows Android but not the OneHZ codebase. Do not expose internal
  names such as "engine", "motor", `mState`, `USAGE_MEDIA`, shell commands or data classes.
- Prefer a short noun title and a supporting line that explains the effect or context.
  Do not repeat "on/off"; the switch already says it.
- Do not use "local only" or "everything stays on this device" as the main description of a
  screen. Privacy is useful context, but the title and subtitle must explain the screen.
- Use "Inactive" for a stopped or unapplied state; use "Fora do controle" only for time
  that OneHZ did not control. Do not use "Sem controle" alone.
- Use "Consumption" (PT "Consumo", ES "Consumo") for the battery rate metric.
- Use "Sleep" for the OneHZ feature and "Doze" for the Android state. Do not translate
  Doze.
- Use "step" (PT "etapa", ES "paso") for a profile tier; use "rate" only for Hz.
- Use "Pin notification" (PT "Fixar notificação", ES "Fijar notificación") for the option.
  "Notifications" is the permission/setup row; do not call the permission "persistent".
- Use "Quick controls" for the tile and the pinned notification. Use "Automation" for the
  activation rules. Use "System" for system changes and diagnostics.
- Use "time window" (PT "janela de horário", ES "ventana de horario") as a noun; describe
  the off state as "no window" instead of "can be used at any time".
- Keep the glossary stable across the app and the three locales:

| Term | EN | PT-BR | ES |
|---|---|---|---|
| refresh rate | rate | taxa | tasa |
| profile tier | step | etapa | paso |
| OneHZ feature for forced Doze | Sleep | Sono | Sueño |
| Android idle state | Doze | Doze | Doze |
| Always On Display | AOD | AOD | AOD |
| quick settings tile | tile | tile | tile |
| activation rule | rule | regra | regla |
| notification pin | pinned notification | notificação fixa | notificación fija |

## Navigation and placement

- Primary data views belong in the bottom bar. Do not hide them under a generic section.
  The bottom bar is Home, Apps, Usage and Settings; activation rules have their own
  Automation section.
- Settings is organized into General, Quick controls, Automation, Data, System, Updates
  and About. Do not reintroduce a catch-all "Advanced" section; troubleshooting actions
  live in System.
- Configuration stays on the feature screen. One-off tests and engineering actions belong
  in Diagnostics. The Sleep screen links to Diagnostics instead of embedding a test card;
  the Doze test sits in the Diagnostics Tests card next to the display tests.
- Optional features introduced during setup must be skippable and must remain reachable
  later. The setup optional step offers only the Quick Settings tile and never completes
  setup; only "Finish and enable" does that. Usage is a bottom-bar tab, and activation
  rules live in Settings → Automation.
- The tile belongs in Quick controls and in the setup optional step, not in System.

## Screen structure

Large screens read as a stack of labelled sections, the same way Settings does:

- Each section starts with a `titleSmall` label in `onSurfaceVariant`, 24dp from the
  previous content and 8dp above its card.
- Every card has one header pattern: an 18dp icon in the primary color plus a `titleMedium`
  title, then 8dp before the content. Do not mix cards with and without a header on the
  same screen.
- Human-readable pairs use `MetricRow` (label left, value right). Do not build
  `"label: value"` strings by hand.
- Raw values such as shell keys, props, device paths and test output go in a contained
  block: `surfaceVariant` at 55%, `shapes.small`, 12dp of padding and a monospaced
  `bodySmall` text. Never leave a raw string inline with prose.
- A long dump (device candidates, full reports) is collapsed behind a "Details" row and
  expands in place. The summary stays visible.
- One action style per block: a filled `Button` filling the width for the primary action,
  `OutlinedButton` filling the width for secondary actions, stacked with 8dp. Do not place
  several variable-width buttons in a flow row; the ragged edges read as unfinished.
- Diagnostics follows this order: Status, Tests, Display, Input, Video, Report. Tests come
  before the informational cards so a troubleshooting user does not scroll to find them.

## Spacing

Use one scale across screens:

| Token | Value |
|---|---|
| Screen horizontal/vertical padding | 16dp |
| Card padding | 16dp (the Home hero may use 20dp) |
| Gap between cards | 12dp |
| Gap before a section title | 24dp |
| Title to body | 8dp |
| Between control groups | 12dp |
| Row vertical padding | 12dp |
| Bottom content padding | 24dp |

- `OneHzCard` uses 16dp and `MetricRow` uses 4dp vertical padding.
- Do not mix 14dp, 18dp and 20dp card paddings. The hero card is the only documented
  exception.
- Use `Arrangement.spacedBy(12.dp)` for card lists and avoid manual spacers when a shared
  list arrangement is enough.
- Check the screen at 100% and 200% font scale; spacing must not collapse or overlap.

## Review checklist

- [ ] The title is a stable noun or action.
- [ ] Each state's description describes its effect, not "on/off".
- [ ] Warnings remain in a static banner.
- [ ] No internal jargon, "local only" subtitle or "persistent" permission wording.
- [ ] Terms match the glossary above.
- [ ] PT-BR and ES strings mirror the base key and placeholders.
- [ ] The screen respects the spacing scale.
- [ ] TalkBack reads the row once and announces the switch state.
- [ ] The row and card fit at 200% font scale.
- [ ] A primary feature is not hidden in a generic catch-all section.
