> 📄 Fragment dokumentacji Verbigem Android. Indeks: [README.md](../README.md)

# 🔥 Firebase — konfiguracja projektu

- **Projekt:** `mini-verbigem` (project_number `1064156518963`). **Package:** `com.verbigem.app`.
- **Kolekcje Firestore:**
  - `users/{uid}` — profil (`UserProfile`: plan, walletCreditsCents, uiLang, speakLang*). **Czytelny wyłącznie przez właściciela.**
  - `usersPublic/{uid}` — wizytówka do wyszukiwania (`PublicProfile`): uid, nickname, photoURL, uiLang, speakLangSource/Target + zlowercasowane `searchNick`/`searchEmail` (Firestore nie ma case-insensitive `whereGreaterThanOrEqualTo`). Read dla zalogowanych, create/update tylko właściciel. Backfill starych kont: `node backfill_faza0.js --apply`.
  - `users/{uid}/history/{syncId}`, `users/{uid}/ocr_history/{syncId}` — historie (sync, last-write-wins).
  - `friendships/{id}` — `members: [uidA, uidB]`, status, requestedBy, nicki. Zapytania **po `members`**.
  - `chats/{chatId}` (+ `messages`) — dokument czatu **musi** mieć `members`, inaczej reguły odrzucą każdą wiadomość. Zawiera `lastMessage`, `lastMessageAuthorId`, `lastMessageAt`. Wiadomość: `authorId`, `sourceLang`, `text` (ORYGINAŁ), `senderTranslation` (`{lang, text}`), `type`, `clientMsgId` (= id dokumentu → idempotencja ponowień).
  - `chats/{chatId}/readReceipts/{uid}` — `{uid, lastReadAt}`, tylko własny dokument.
  - `chats/{chatId}/typing/{uid}` — `{uid, expiresAt}` (wygaśnięcie po 8 s, więc padnięta aplikacja nie udaje, że pisze).
  - `users/{uid}/contacts/{otherUid}` — `ContactSettings`: `alias`, `langOverride`, `muted`, `pinned`, `blocked`, `note`, `updatedAt`. Odczyt/zapis **tylko właściciela** + whitelist pól (bez niej klient mógłby dopisać flagi, które serwer zacznie rozumieć).
  - `users/{uid}/fcmTokens/{token}` — **ID dokumentu to token**, żeby Cloud Function mogła skasować martwy token po samym ID (bez odczytu). Jeden dokument = jedno urządzenie.
  - `app_config/tts`, `app_config/update`, `app_config/notifications`.
- **Reguły (`firestore.rules`) deploy:** `firebase deploy --only firestore:rules --project mini-verbigem`. Wymagane: `match /app_config/{doc} { allow read: if true; allow write: if false; }` — app czyta `app_config/update` i `app_config/tts` **PRZED loginem**; bez tego `PERMISSION_DENIED` i brak wykrycia update'u / konfiguracji TTS.
- **Pliki w repo:** `firebase.json` (rules + blok `functions` + storage), `.firebaserc`, `firestore.rules`, `storage.rules`.
- **CLI:** `firebase.cmd` (npm global). ⚠️ `firebase firestore:set` **NIE istnieje** w tym CLI — do zapisu używamy Firestore REST API (Node.js) z tokenem z `%USERPROFILE%\.config\configstore\firebase-tools.json`.
  - ⚠️ **Token wygasa po ~1 h** (`expires_at`, ms). Starsze skrypty (`write_firestore.js`, `update_firestore_update.js`) odświeżają go samodzielnie przez `oauth2.googleapis.com` z wpisanymi na sztywno `client_id`/`client_secret` — **to już nie działa** (Google: `invalid_client`).
  - **Działające odświeżanie:** odpalić dowolne polecenie CLI (np. `firebase projects:list` — czysty odczyt). CLI ma poprawne poświadczenia i zapisuje świeży `access_token` z powrotem do configstore (tak robi `backfill_faza0.js` → `refreshViaCli()`).
  - ☠️ **REST z tokenem właściciela projektu OMJA reguły bezpieczeństwa.** Backfill potrafi zapisać cudzy dokument, którego aplikacja nie ruszy. Dlatego `backfill_faza0.js` domyślnie robi dry-run i wymaga `--apply`. (Drugi skrypt, `backfill_searchtext.js`, został usunięty w fazie 5 E2E — patrz `docs/czat-e2e.md` §6.)

---
