# Verbigem Android — Änderungsprotokoll


> ⚠️ Die Tags `v1.0.1`–`v1.0.3` sind **frühe historische Builds** (versionCode 2–3).
> Die aktuelle Version ist **v1.0.42** (versionCode 43).
> Die Version steht in `app/build.gradle.kts` (`versionCode` / `versionName`).

---

## v1.0.42 (2026-09-06) — versionCode 43

**Hilfe funktioniert jetzt auch auf deaktivierten Bedienelementen.**

- `Modifier.helpClickable(enabled = false)` reichte `enabled` an
  `combinedClickable` weiter, und das deaktiviert **auch den langen Klick** — auf
  ausgegrauten Elementen verschwand das Hilfefenster lautlos. Jetzt sperrt
  `enabled` nur noch die Aktion: `combinedClickable` bekommt immer
  `enabled = true`, die Prüfung steckt in `onClick = { if (enabled) onClick() }`.
- Betroffen: Schaltfläche „Übersetzen" bei leerem Feld, Motor-Symbole
  (genau / beide / online) im kostenlosen Konto, OCR-Schaltflächen ohne Bild.
- README: neuer Punkt 10 in der Liste der Hilfe-Fallen.

## v1.0.41 (2026-09-06) — versionCode 42

**UI-Korrekturen nach der globalen Hilfefenster-Regel (v40).**

- **"Ich verstehe"** im Hilfefenster folgt jetzt der UI-Sprache (vorher blieb es auf Polnisch). `HelpWindow` sichert `LocalContext` vor `Dialog{}` und stellt ihn über `CompositionLocalProvider` wieder her.
- **Untere Leiste zurück auf 5 Positionen** (Übersetzer, Gespräch, Chat, Kontakte, Profil). Die sechste OCR-Position hat die Leiste überfüllt.
- **Übersetzer**: die Schaltfläche "Übersetzen" scrollt automatisch über die Tastatur (`bringIntoViewRequester` + `LaunchedEffect(WindowInsets.isImeVisible)` mit `delay(250)`).
- **Engine-Symbole (genau, beide, online)** zeigen Hilfe bei langem Druck auch im deaktivierten Zustand.
- **Gespräch und OCR**: überflüssige Untertitel entfernt — der "?"-Button erklärt die Seite bereits.
- **Kontakte → Aus dem Telefon**: die Schaltflächen "Freunde in Kontakten finden" und ".vcf importieren" haben jetzt Hilfe (langer Druck).
- **Profil**: UI-Sprachwahl hat einen sichtbaren Rahmen; unter der Datenschutz-Karte erscheint die Karte **Über die App** mit Version und Link **Was ist neu**.
- **Launcher-Symbol**: Hintergrund des adaptiven Symbols von Grün (#2C6B85) auf Creme (`CalmDayBg` #F7F5F1) geändert. PNGs in fünf Dichten 108/162/216/324/432 px (Lanczos).

---

## v1.0.40 (2026-09-06) — versionCode 41

**Globale UI-Regel: Symbol antippen = Aufgabe ausführen, lange drücken = Hilfe-Fenster.**

- **Jedes Symbol in der App** erklärt sich jetzt selbst: was es ist, was es tut, wie man es
  benutzt. Neue gemeinsame Infrastruktur in `ui/components/HelpDialog.kt` (`HelpWindow`,
  `helpClickable`, `HelpIconButton`, `HelpFramedIconButton`, `QuestionMarkButton`, `ScreenHeader`).
- **Jede Bildschirmüberschrift**: Glühwürmchen-Logo (transparent) + Titel + eine **„?"**-Taste
  rechts, die die ganze Seite erklärt.
- **Übersetzer**: Hilfe für beide Sprachwähler und das Tausch-Symbol; vier Engines mit kurzen
  Beschriftungen (schnell / genau / beide / online) und ausführlichen Fenstern — die alten
  Beschreibungen unter den Engines sind **entfernt**; Mikrofon / Kamera / Kamera Pro haben jetzt
  **Rahmen** und die Beschriftungen „aus der Stimme" / „vom Foto" / „vom Foto pro"; Hilfe für die
  Übersetzen-Taste, die fünf Symbole in Verlauf und Ergebnis sowie die untere Leiste.
- **Gespräch**: Logo + „?" (inklusive Hinweis, dass das Gespräch nie gespeichert wird und das
  Gerät nie verlässt), Hilfe für Sprachfelder, Tauschen, Mikrofon und Senden-Taste.
- **OCR hat jetzt einen Eintrag in der unteren Leiste** — die Leiste hat sechs Tabs (OCR war
  der einzige Bildschirm ohne Navigation).
- **Kontakte**: Freunde / Einladungen / Aus dem Telefon / Extern als **Symbol über 11.sp-Text**
  (wie in der unteren Leiste) plus Hilfe-Fenster.
- **Chat, Profil, mein QR-Code, Telefonbestätigung** — „?"-Überschriften und Hilfe an den Symbolen.
- **~50 neue Hilfetexte in 6 Sprachen** (394 Schlüssel, nichts fehlt in irgendeiner Sprache).

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
