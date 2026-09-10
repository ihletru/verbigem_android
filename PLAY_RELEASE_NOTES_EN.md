# Verbigem — English release notes (What's new) for Google Play

Wersja: **1.0.65** (versionCode 65). Tekst do wklejenia w Play Console →
Release details → Release notes (język: English). Źródło: `CHANGELOG.en.md`.

---

## Short (recommended for the "What's new" box, ≤ 500 characters)

```
Verbigem 1.0.65 — messages that ignored the interface language you picked
now follow it: sign-in, translation, speech and text recognition, top-up and the
update window. Speech errors alone had eleven Polish texts hardcoded in the
source. Technical library errors (like "HTTP 402") stay in the log, not on screen.
```

---

## Extended (gdy chcesz dać szerszy kontekst)

```
Verbigem 1.0.65

Fixed: some messages ignored the interface language you had picked.

With English selected, some messages still came out in Polish — and the other
way round. This covered sign-in errors, translation, speech and text
recognition, account top-up and the app-update window.

The cause was the same every time: those texts asked the system context for a
translation, and the system context knows nothing about the language chosen in
the app — it used the phone's language. Every such place now goes through one
shared, correct source of texts.

Technical errors from libraries (for example "HTTP 402") no longer reach the
screen; they stay in the log, and you see a clear message in your language.

Speech-recognition errors had eleven Polish texts hardcoded in the source,
including "Network error" and "No speech detected". They now come from the
translations and follow the interface language.

Wrong password, an e-mail already in use and no internet during sign-in finally
have their own messages instead of English text from Firebase. Update download
errors (incomplete file, server error, installer error) are in your language too.
```

---

## Per-version (dokładnie z CHANGELOG.en.md, gdybyś chciał wypisać historię)

- **1.0.65** — Fixed: messages that ignored the chosen interface language
  (sign-in, translation, OCR, speech, top-up, app update) now follow it; library
  errors stay in the log instead of the screen.
- **1.0.64** — Fixed: the model-download window lied about a model being ready,
  and all nine windows now follow the chosen interface language; expired SMS
  codes have their own message and 120 s to be entered.
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
