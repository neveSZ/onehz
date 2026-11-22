# UI copy guidelines

This document covers user-facing copy for settings, with a focus on state-aware switch
descriptions. It applies to every detail screen and sheet.

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

## Review checklist

- [ ] The title is a stable noun or action.
- [ ] Each state's description describes its effect, not "on/off".
- [ ] Warnings remain in a static banner.
- [ ] PT-BR and ES strings mirror the base key and placeholders.
- [ ] TalkBack reads the row once and announces the switch state.
- [ ] The row fits at 200% font scale.
