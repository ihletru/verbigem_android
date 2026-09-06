# Verbigem Android — Änderungsprotokoll

Neueste zuerst. Jede Version wird zusätzlich als GitHub Release veröffentlicht:
<https://github.com/ihletru/verbigem_android/releases>

> ⚠️ Die Tags `v1.0.1`–`v1.0.3` sind **frühe historische Builds** (versionCode 2–3).
> Die aktuelle Version ist **v1.0.39** (versionCode 40).
> Die Version steht in `app/build.gradle.kts` (`versionCode` / `versionName`).

---

## v1.0.39 (2026-09-05) — versionCode 40

Zusammenfassung der gesamten Entwicklung von v1.0.3 bis heute.

**Neuigkeiten**

- **1:1-Chat + Kontakte (Phase 1–3)**
  - Posteingang, Chat-Verlauf, Reiter in den Kontakten (TabRow, 4 Reiter).
  - `.vcf`-Import (eigener Parser, keine Abhängigkeiten), Suche in Nachrichten, „Kontakte, die du kennen könntest" (`suggestFriends`), Einladungen per Telefonnummer.
- **Telefonnummer- und SMS-Verifizierung (Phase 2.4 / 2.6)**
  - Verständliche Phone-Auth-Fehler, freigeschaltete SMS-Region (**ALL** — weltweit).
  - Crash-Fixes: „no activity" (v37), Crash nach Tippen auf „SMS senden" (v38), verständliche Fehler (v39).
- **FCM-Push-Benachrichtigungen** (Phase 2): Cloud Functions + FCM, App Check (HMAC-Geheimnis), `matchContacts`.
- **Fotos und OCR** (Phase 5): Foto-Vorschau im Vollbild + Ladefortschritt, OCR im Chat, Live-STT-Transkription.
- **QR-Codes** (Phase 4): mein QR-Code (ZXing), GMS Code Scanner, App Links + `assetlinks.json`.
- **Firefly-Branding**: transparentes Launcher-Icon (v40), Logo, App-Check-Schritt im Play-Store-Veröffentlichungsplan.
- **Datenschutz**: Datenschutzerklärung in 6 Sprachen, prominenter Hinweis für `READ_CONTACTS`.
- **6 Sprachen** (pl, en, es, zh, de, tr); String-Audit ×6 (257 Schlüssel, keine Lücken).
- **Cloud-Functions-Runtime**: Node 20 → nodejs22 (7 Android-Funktionen).

---

## v1.0.3 — versionCode 3

Auto-Update von GitHub raw (`master`), UI-Korrekturen: Pro-Lautsprecher statt Stern,
Pro-OCR neben dem kostenlosen OCR, verstecktes Menü über der Tastatur.

## v1.0.2

Reaktiver Sync, roter Papierkorb, Pro-Lautsprecher für Free-Nutzer mit Tooltip,
Pro-OCR + OCR-Verlauf, die Tastatur verdeckt den Übersetzer nicht mehr.

## v1.0.1 — versionCode 2

Auto-Update-Testbuild. Icons „Pro vorlesen" / Löschen, Firestore-Sync,
Auto-Update von GitHub Releases.
