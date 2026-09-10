# Verbigem — English release notes (What's new) for Google Play

Wersja: **1.0.62** (versionCode 62). Tekst do wklejenia w Play Console →
Release details → Release notes (język: English). Źródło: `CHANGELOG.en.md`.

---

## Short (recommended for the "What's new" box, ≤ 500 characters)

```
Verbigem 1.0.62 — fixes a crash on launch that affected Android 14 and newer
devices. The app now starts normally.
```

---

## Extended (gdy chcesz dać szerszy kontekst)

```
Verbigem 1.0.62

Fixed: the app crashed immediately on launch on Android 14 and above.

A field on the main screen was being initialised before Android had finished
attaching the application, so the app had no context to work with. Older
Android versions tolerated it; Android 14+ throws an error and the app closes
before showing anything.

The field is now created only when it is actually needed.
```

---

## Per-version (dokładnie z CHANGELOG.en.md, gdybyś chciał wypisać historię)

- **1.0.62** — Fixed: the app crashed on launch on newer phones (Android 14+).
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
