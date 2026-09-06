# Verbigem Android — historia zmian


> ⚠️ Tagi `v1.0.1`–`v1.0.3` to **wczesne buildy historyczne** (versionCode 2–3).
> Bieżąca wersja to **v1.0.39** (versionCode 40).
> Wersja trzymana jest w `app/build.gradle.kts` (`versionCode` / `versionName`).

---

## v1.0.39 (2026-09-05) — versionCode 40

Podsumowanie całego rozwoju od v1.0.3 do dziś.

**Co nowego**

- **Czat 1:1 + Kontakty (Faza 1–3)**
  - Skrzynka odbiorcza, wątek czatu, zakładki w Kontaktach (TabRow, 4 zakładki).
  - Import `.vcf` (własny parser, zero zależności), wyszukiwanie w wiadomościach, „Możesz znać" (`suggestFriends`), zaproszenia po numerze telefonu.
- **Weryfikacja numeru i SMS (Faza 2.4 / 2.6)**
  - Czytelne błędy Phone Auth, odblokowany region SMS (**ALL** — cały świat).
  - Poprawki crashy: „no activity" (v37), crash po kliknięciu „wyślij SMS" (v38), czytelne błędy (v39).
- **Powiadomienia push FCM** (Faza 2): Cloud Functions + FCM, App Check (sekret HMAC), `matchContacts`.
- **Zdjęcia i OCR** (Faza 5): podgląd zdjęcia na pełnym ekranie + postęp ładowania, OCR w czacie, transkrypcja STT na żywo.
- **Kody QR** (Faza 4): mój kod QR (ZXing), skaner GMS Code Scanner, App Links + `assetlinks.json`.
- **Branding Firefly**: przezroczysta ikona launchera (v40), logo, krok App Check w planie publikacji na Play Store.
- **Prywatność**: polityka prywatności w 6 językach, prominent disclosure dla `READ_CONTACTS`.
- **6 języków** (pl, en, es, zh, de, tr); audyt stringów ×6 (257 kluczy, zero braków).
- **Runtime Cloud Functions**: Node 20 → nodejs22 (7 funkcji Androida).

---

## v1.0.3 — versionCode 3

Auto-update z GitHub raw (`master`), poprawki UI: głośnik Pro zamiast gwiazdki,
OCR Pro obok OCR free, ukryte menu nad klawiaturą.

## v1.0.2

Reaktywny sync, czerwony śmietnik, głośnik Pro dla free z tooltipem,
OCR Pro + historia OCR, klawiatura nie zasłania Tłumacza.

## v1.0.1 — versionCode 2

Auto-update test build. Ikony Czytaj Pro / Skasuj, sync Firestore,
