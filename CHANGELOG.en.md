# Verbigem Android — changelog


> ⚠️ Tags `v1.0.1`–`v1.0.3` are **early historical builds** (versionCode 2–3).
> The current version is **v1.0.39** (versionCode 40).
> The version lives in `app/build.gradle.kts` (`versionCode` / `versionName`).

---

## v1.0.39 (2026-09-05) — versionCode 40

Summary of all development from v1.0.3 up to today.

**What's new**

- **1:1 chat + Contacts (Phase 1–3)**
  - Inbox, chat thread, tabs in Contacts (TabRow, 4 tabs).
  - `.vcf` import (own parser, zero dependencies), message search, "People you may know" (`suggestFriends`), invitations by phone number.
- **Phone number and SMS verification (Phase 2.4 / 2.6)**
  - Readable Phone Auth errors, unlocked SMS region (**ALL** — worldwide).
  - Crash fixes: "no activity" (v37), crash after tapping "send SMS" (v38), readable errors (v39).
- **FCM push notifications** (Phase 2): Cloud Functions + FCM, App Check (HMAC secret), `matchContacts`.
- **Photos and OCR** (Phase 5): full-screen photo preview + loading progress, OCR in chat, live STT transcription.
- **QR codes** (Phase 4): my QR code (ZXing), GMS Code Scanner, App Links + `assetlinks.json`.
- **Firefly branding**: transparent launcher icon (v40), logo, App Check step in the Play Store release plan.
- **Privacy**: privacy policy in 6 languages, prominent disclosure for `READ_CONTACTS`.
- **6 languages** (pl, en, es, zh, de, tr); string audit ×6 (257 keys, no gaps).
- **Cloud Functions runtime**: Node 20 → nodejs22 (7 Android functions).

---

## v1.0.3 — versionCode 3

Auto-update from GitHub raw (`master`), UI fixes: the Pro speaker instead of a star,
Pro OCR next to free OCR, hidden menu above the keyboard.

## v1.0.2

Reactive sync, red trash icon, Pro speaker for free users with a tooltip,
Pro OCR + OCR history, the keyboard no longer covers the Translator.

## v1.0.1 — versionCode 2

Auto-update test build. Read Pro / Delete icons, Firestore sync,
