# Verbigem — English release notes (What's new) for Google Play

Wersja: **1.0.60** (versionCode 61). Tekst do wklejenia w Play Console →
Release details → Release notes (język: English). Źródło: `CHANGELOG.en.md`.

---

## Short (recommended for the "What's new" box, ≤ 500 characters)

```
Verbigem 1.0.60 — build updated to target Android 16 (API 36) for Google Play.
No user-visible changes.
```

---

## Extended (gdy chcesz dać szerszy kontekst)

```
Verbigem 1.0.60

This release updates the build target to Android 16 (API 36), as required by
Google Play for new apps since 2026-08-31. There are no code changes and no
behavioural differences for end users — the app keeps its previous features
(offline translation & OCR with on-device AI, optional online mode, chats,
contacts, etc.). This is a build-config-only release.
```

---

## Per-version (dokładnie z CHANGELOG.en.md, gdybyś chciał wypisać historię)

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
