# Verbigem Android — changelog


> ⚠️ Tags `v1.0.1`–`v1.0.3` are **early historical builds** (versionCode 2–3).
> The current version is **v1.0.41** (versionCode 42).
> The version lives in `app/build.gradle.kts` (`versionCode` / `versionName`).

---

## v1.0.41 (2026-09-06) — versionCode 42

**UI fixes after the global help-window rule (v40).**

- **"I understand"** in the help window now follows the UI language (it used to stay in Polish). `HelpWindow` captures `LocalContext` before `Dialog{}` and replays it via `CompositionLocalProvider` — `Dialog` resets the locale to the base activity.
- **Bottom bar back to 5 icons** (Translator, Conversation, Chat, Contacts, Profile). The sixth OCR slot crowded the bar and did not shorten the path — OCR is one tap from Translator (camera icon in `HelpFramedIconButton`). The OCR screen still shows `BottomNav` but its icon is not in the bar.
- **Translator**: the "Translate" button auto-scrolls above the keyboard (`bringIntoViewRequester` + `LaunchedEffect(WindowInsets.isImeVisible)` with `delay(250)` — the keyboard slides in 250 ms after focus).
- **Engine icons (accurate, both, online)** show help on long-press even when disabled for free users (`EnginePicker.helpClickable` without `enabled` — only `onClick` checks `isEnabled`).
- **Conversation and OCR**: redundant subtitles under the title removed — the header "?" button already explains the page.
- **Contacts → From phone**: "Find friends in my contacts" and "Import .vcf" now have help (long-press). Rewritten from `Button` to `Box` + `helpClickable` because `Button` swallowed the long-press.
- **Profile**: the UI language picker now has a visible frame; under the privacy card a new **About** card shows `Version <versionName> · build <versionCode>` (from `BuildConfig`) and a **What is new** link (`AppLinks.whatsNew(uiLang)` → `mini.verbigem.com/android/changelog[-<lang>].html`).
- **Launcher icon**: adaptive icon background changed from green (#2C6B85) to cream (`CalmDayBg` #F7F5F1) to match the app pages. PNGs live in `drawable-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/` at 108/162/216/324/432 px (Lanczos from `mini/public/logo-firefly.png`).

---

## v1.0.40 (2026-09-06) — versionCode 41

**Global UI rule: tap an icon = do its job, long-press it = help pop-up.**

- **Every icon in the app** now explains itself: what it is, what it does, how to use it.
  New shared infrastructure in `ui/components/HelpDialog.kt` (`HelpWindow`, `helpClickable`,
  `HelpIconButton`, `HelpFramedIconButton`, `QuestionMarkButton`, `ScreenHeader`).
- **Every screen header**: firefly logo (transparent) + title + a **"?"** button on the right
  that opens an explanation of the whole page.
- **Translator**: help for both language pickers and the swap icon; four engines with short
  captions (fast / accurate / both / online) and full pop-ups — the old descriptions under the
  engines are **gone**; mic / camera / camera Pro now have **frames** and the captions
  "from voice" / "from photo" / "from photo pro"; help for the Translate button, the five icons
  on history and result cards, and the bottom bar.
- **Conversation**: logo + "?" (including the note that the conversation is never saved and
  never leaves the device), help for the language fields, swap, mic and send button.
- **OCR now has a bottom-bar entry** — the bar has six tabs (it used to be the only screen
  without navigation).
- **Contacts**: Friends / Invites / From phone / External as an **icon above 11.sp text**
  (like the bottom bar) plus help pop-ups.
- **Chat, Profile, my QR code, phone verification** — "?" headers and help on the icons.
- **~50 new help texts in 6 languages** (394 keys, nothing missing in any language).

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
