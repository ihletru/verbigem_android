# Verbigem — English release notes (What's new) for Google Play

Wersja: **1.0.64** (versionCode 64). Tekst do wklejenia w Play Console →
Release details → Release notes (język: English). Źródło: `CHANGELOG.en.md`.

---

## Short (recommended for the "What's new" box, ≤ 500 characters)

```
Verbigem 1.0.64 — the model-download window no longer claims a model is ready
when it is not, and it now follows the interface language you picked. The SMS
code screen also tells you when a code has expired instead of saying it is
wrong, and gives you twice as long to enter it.
```

---

## Extended (gdy chcesz dać szerszy kontekst)

```
Verbigem 1.0.64

Fixed: the model-download window could claim a model was ready when it was not
on the phone, and it ignored the interface language you had chosen.

Downloading the Fast model and then switching to Accurate showed a green "The
Accurate model is ready to use!" even though nothing had been downloaded. The
window also stayed in the device language instead of the one selected in the
app.

An expired SMS code now says so plainly, rather than reporting a wrong code,
and you have 120 seconds instead of 60 to enter it — with a slower SMS the old
window ran out before the code could be typed, and a correct code was rejected.
```

---

## Per-version (dokładnie z CHANGELOG.en.md, gdybyś chciał wypisać historię)

- **1.0.64** — Fixed: the model-download window lied about a model being ready
  and ignored the chosen interface language; expired SMS codes now have their
  own message and 120 s to be entered.
- **1.0.63** — Fixed: the app crashed on launch on newer phones (Android 14+).
- **1.0.60** — Build: compile/target SDK raised to API 36 (Android 16).
  Google Play requires new apps to target API 36 since 2026-08-31; build target
  only, no code changes.
- **1.0.59** — Fixed: "1 contacts", "1 mutual friends". Three messages with a count
  now follow each language's plural rules (Polish 1 / 2-4 / 5+, Turkish & Chinese
  single form). Built on Android plural resources.
- **1.0.58** — Fixed two places that pretended everything worked (delete-failed
  conversation, failed people search now report the error).
- **1.0.57** — Fixed a message stuck on "sending" when sent while another was in
  flight; Read Pro now reports speech-generation failure.
- **1.0.56** — Microphone now reports no-permission / no-recognition / failure
  instead of staying silent.
- **1.0.55** — Photo/voice messages now go through the same send queue as text
  (retry on failure). One-off DB migration v9 -> v10.
- **1.0.54** — Error messages no longer hardcoded in Polish; full 6-language
  localization audit (482 strings, none missing).
