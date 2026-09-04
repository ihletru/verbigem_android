# Czat i Kontakty — plan budowy

Dokument roboczy. Praca jest rozbita na fazy, z których każda kończy się stanem,
który da się zbudować i przetestować na telefonie. **Każdą sesję zaczynamy od
sekcji „Postęp", a kończymy jej aktualizacją** — dzięki temu kolejna sesja wie,
gdzie skończyliśmy, bez czytania całego pliku.

Ostatnia aktualizacja: 2026-09-03

---

## 1. Postęp

| Faza | Nazwa | Status | versionCode | Commit |
|---|---|---|---|---|
| 0 | Ratunek fundamentów | ⬜ nie rozpoczęta | — | — |
| 1 | Skrzynka odbiorcza + wątek | ⬜ nie rozpoczęta | — | — |
| 2 | Backend: Cloud Functions | ⬜ nie rozpoczęta | — | — |
| 3 | Kontakty 2.0 (import, kanały) | ⬜ nie rozpoczęta | — | — |
| 4 | Kody QR | ⬜ nie rozpoczęta | — | — |
| 5 | Media: zdjęcia + OCR, głosówki | ⬜ nie rozpoczęta | — | — |
| 6 | Czaty grupowe | ⏸ odłożone (nie wybrane) | — | — |

**Gdzie skończyliśmy:** nic jeszcze nie zrobione — dokument dopiero powstał.
**Następny krok:** Faza 0, zadanie 0.1 (`usersPublic`).

---

## 2. Ustalone decyzje

Potwierdzone przez Milosza 2026-09-03. Nie zmieniać bez nowej dyskusji.

| # | Decyzja | Ustalenie |
|---|---|---|
| D1 | **Gdzie tłumaczymy wiadomości** | **U odbiorcy.** Nadawca wysyła oryginał + `sourceLang` + własne tłumaczenie jako podpowiedź (fallback dla odbiorców bez pobranego modelu). Odbiorca tłumaczy lokalnie Hy-MT2 na swój język. |
| D2 | **Backend (Cloud Functions)** | **Dopuszczony.** Sekwencjonowanie: fazy 0–1 czysto klientowe, `functions/` wchodzi w fazie 2 (matching numerów + FCM). |
| D3 | **Weryfikacja numeru telefonu** | **Leniwa, nie przy rejestracji.** Gate przy pierwszym wejściu w Czat lub Kontakty (albo przy kliknięciu „Znajdź znajomych"). Opcja „Pomiń" — czat działa, ale użytkownik nie jest odnajdywalny po numerze i nie używa matchingu. |
| D4 | **Dodatki w zakresie** | Kod QR + skaner, zdjęcia z OCR w czacie, głosówki (nagraj → tekst). |
| D5 | **Czaty grupowe** | Odłożone / nie wybrane. Faza 6 zostaje w planie jako opcjonalna. |

---

## 3. Diagnoza stanu obecnego

Sześć realnych błędów. **Faza 0 musi pójść pierwsza** — nadbudowa na tym
fundamencie to budowanie na piasku.

| # | Błąd | Gdzie | Skutek |
|---|---|---|---|
| B1 | **Wyszukiwanie ludzi nie działa wcale.** `searchUsers()` odpytuje `users/`, reguły pozwalają czytać tylko własny dokument. Reguły przewidują `usersPublic/{uid}`, ale **żaden kod tego dokumentu nie tworzy** (grep: zero wystąpień). | `ContactsViewModel.kt:76` | Każde wyszukiwanie → `PERMISSION_DENIED`. |
| B2 | **Znajomi tylko w jedną stronę.** `watchFriendships()` filtruje `whereEqualTo("uidA", uid)`. Osoba zaproszona (jako `uidB`) nigdy nie widzi znajomego. | `ChatRepository.kt:60-68` | Połowa zaproszeń znika po akceptacji. |
| B3 | **Tłumaczenie hardkodowane PL→EN.** `hyMt2Engine.translate(text, PL, EN)` ignoruje profile obu stron. `speak()` też ma na sztywno `"en"` dla przychodzących. | `ChatViewModel.kt:68`, `ChatScreen.kt:141` | Czat tłumaczy w losowy język. |
| B4 | **Brak listy konwersacji.** Ekran Czatu to napis „wybierz znajomego w Kontaktach". Nie ma jak wrócić do toczącej się rozmowy. | `ChatScreen.kt:64-87` | Czat nieużywalny jako komunikator. |
| B5 | **Reguły blokują `update` na wiadomościach** (`allow update, delete: if false`). | `firestore.rules` → `chats/{chatId}/messages` | Potwierdzenia odczytu, edycja i usuwanie są dziś niemożliwe. |
| B6 | **BottomNav nie ma OCR.** `Screen.Ocr.route` jest w `showBottomNav`, ale brak go w `items`. | `BottomNav.kt:41-47` | Drobiazg, ale wprowadza niespójność. Etykiety w `BottomNav` są też hardkodowane po angielsku (naruszenie zasady wielojęzyczności). |

---

## 4. Co jest technicznie możliwe

Sprawdzone, nie zgadywane. Tu leży najważniejsza korekta do pierwotnego pomysłu.

| Źródło | Import listy | Wysyłka do konkretnej osoby | Decyzja |
|---|---|---|---|
| **Książka telefoniczna** (`ContactsContract`) | ✅ pełny (imiona, numery, e-maile, zdjęcia) | ✅ SMS z prefillem | **Główne źródło listy** |
| **WhatsApp** | ❌ brak ContentProvidera od lat | ⚠️ `https://wa.me/<E164>?text=…` — działa tylko dla numerów zarejestrowanych w WA, odpowiedzi nie wracają | **Kanał wysyłki** |
| **Telegram** | ❌ tylko TDLib/MTProto (wymaga api_id, logowania jako użytkownik, ryzykowne ToS) | ⚠️ `https://t.me/<username>` otwiera czat, ale **URL nie potrafi wkleić tekstu** | **Tylko zaproszenie** |
| **Plik .vcf** (eksport z WhatsApp / Google / Telegram) | ✅ | zależnie od kanału | **Import ręczny, ~120 linii, zero zależności** |
| Signal, Messenger, Instagram | ❌ | ❌ | Pomijamy — zostaje systemowy share sheet |

### 4.1 Model docelowy: jedna lista, wiele kanałów

WhatsApp nie da listy kontaktów — **ale to bez znaczenia**, bo WhatsApp opiera się
na numerach telefonu. Lista z książki telefonicznej *jest* w ~90% listą Twoich
kontaktów WhatsApp. Telegram jest wyjątkiem (username'y zamiast numerów) i dlatego
obsługujemy go wyłącznie jako kanał zaproszenia.

```
Książka telefoniczna (+ opcjonalnie .vcf)
        │
        ├─ normalizacja do E.164 (libphonenumber)
        ├─ SHA-256 każdego numeru
        └─ Cloud Function matchContacts() → kto ma Verbigem?
                │
        ┌───────┴────────┬──────────────────┐
        ▼                ▼                  ▼
   MA VERBIGEM      NIE MA, JEST NA WA     NIE MA
   zielony badge    "Zaproś" / "Napisz"    "Zaproś SMS"
   "Dodaj" /        przez WhatsApp         "Napisz SMS"
   "Napisz"         (tłumacz → przekaż)
   (pełny czat)
```

Kontakty bez Verbigema trzymamy **lokalnie w Room** (tabela `external_contacts`),
razem z historią tego, co im wysłaliśmy (`outbox_messages`).

### 4.2 Jak działają zaproszenia

- **Firebase Dynamic Links zostało wyłączone przez Google (sierpień 2025).**
  Zostaje zwykły link `https://mini.verbigem.com/app?inv=<uid>` + systemowy
  share sheet (`ACTION_SEND`) — działa z WhatsApp, Telegramem, e-mailem, SMS-em,
  z dowolną aplikacją na telefonie, **bez żadnej integracji**.
- `invites/{phoneHash}` = `{fromUid, fromName, createdAt}` — zapisywane przez
  Cloud Function. Gdy zaproszona osoba zweryfikuje numer, druga funkcja znajduje
  oczekujące zaproszenia i tworzy gotowe do akceptacji znajomości.

### 4.3 Prywatność numerów telefonów

- Do chmury wysyłamy **wyłącznie hashe**, nigdy numeru wprost.
- Klient: `SHA-256(E.164)`. Funkcja: `HMAC-SHA256(hash, pepper)` z pepperem
  w Secret Managerze. Kolekcja `phoneDirectory` trzyma HMAC, **nie jest czytelna
  dla klienta** — dostęp tylko przez funkcję.
- Dlaczego tak, a nie odwrotnie: gdyby klient wysyłał hashe swojej całej książki
  adresowej do zbioru do odczytu, serwer magazynowałby numery osób, które
  **nie są** użytkownikami. Model „każdy publikuje tylko swój numer" tego unika.
- Ochrona: App Check (Play Integrity) na wszystkich funkcjach + rate limiting.
- **Znane ryzyko MVP:** nawet sam HMAC można zaatakować rainbow table, jeśli
  pepper wycieknie. Mitygacja w fazie 2, akceptowalne na start.

---

## 5. Architektura docelowa

### 5.1 Firestore — nowe i zmieniane kolekcje

```
users/{uid}
  + phoneVerified: Boolean            (tylko funkcja może ustawić)
  + phoneHash: String                 (HMAC, tylko funkcja)

usersPublic/{uid}                     ← NAPRAWA B1, dziś nie istnieje
  uid, nickname, photoURL, uiLang, speakLangTarget
  searchNick (lowercase), searchEmail (lowercase)
  discoverableByPhone: Boolean

friendships/{id}                      ← NAPRAWA B2
  members: [uidA, uidB]               (zapytanie: whereArrayContains)
  uidA, uidB                          (zachowane dla kompatybilności)
  status: pending | accepted | blocked
  requestedBy, nicknameA, nicknameB, createdAt

users/{uid}/contacts/{otherUid}       ← ustawienia per kontakt (prywatne)
  alias, langOverride, muted, pinned, blocked, note

chats/{chatId}                        (chatId = posortowane uidA__uidB)
  members: [uidA, uidB]
  lastMessage, lastMessageAuthorId, lastMessageAt
  readState: { uid: lastReadAt }      (inkrementowane przez odbiorcę)
  typing: { uid: expiresAt }

chats/{chatId}/messages/{msgId}
  authorId, sourceLang, text          ← ORYGINAŁ, zawsze
  senderTranslation: { lang, text }   ← podpowiedź nadawcy (fallback)
  type: text | image | audio
  attachment: { storagePath, width, height, ocrText, ocrLang, durationMs }
  createdAt, clientMsgId              (idempotencja kolejki offline)
  readBy: { uid: timestamp }

phoneDirectory/{hmac}                 ← tylko funkcja, brak dostępu klienta
  uid

invites/{hmac}                        ← tylko funkcja
  fromUid, fromName, createdAt

users/{uid}/fcmTokens/{token}         ← rejestracja tokenów do pushy
  token, platform, updatedAt
```

### 5.2 Room — migracja v5 → v6

Cztery nowe tabele. Wzorowane na istniejącym `pending_deletes` (małe, wyspecjalizowane).

- **`chat_translations`** — cache tłumaczeń, żeby nie mielić modelu przy każdej
  rekompozycji i żeby działało offline.
  `msgId TEXT PK, chatId TEXT, targetLang TEXT, translatedText TEXT, updatedAt INTEGER`
- **`chat_outbox`** — kolejka offline (spójna z filozofią syncu w projekcie).
  `clientMsgId TEXT PK, chatId TEXT, text TEXT, sourceLang TEXT, senderTranslation TEXT, createdAt INTEGER, status TEXT, attempts INTEGER`
- **`external_contacts`** — kontakty spoza Verbigema.
  `id INTEGER PK, displayName TEXT, phoneE164 TEXT, email TEXT, channel TEXT, verbigemUid TEXT NULL, lastInvitedAt INTEGER, lastMessagedAt INTEGER, note TEXT, createdAt INTEGER`
- **`outbox_messages`** — historia wysyłek ręcznych (SMS / WhatsApp / Telegram).
  `id INTEGER PK, externalContactId INTEGER, channel TEXT, originalText TEXT, translatedText TEXT, targetLang TEXT, sentAt INTEGER, status TEXT`

### 5.3 Cloud Functions (`functions/`, Node 20 + TypeScript)

- `matchContacts(hashes: string[])` — callable, HMAC + `whereIn` (chunkowanie po 30,
  limit Firestore dla `in`), zwraca `{index, uid, nickname, photoURL}[]`.
- `inviteByPhone(hashes: string[])` — callable, zapisuje `invites/{hmac}`.
- `onPhoneVerified` (trigger na zmianę `users/{uid}.phoneVerified`) — dopisuje
  `phoneDirectory/{hmac}` i rozwiązuje oczekujące zaproszenia.
- `onMessageCreated` — trigger na `chats/{chatId}/messages`, wysyła FCM do odbiorców.
- **Wymaga planu Blaze** (Cloud Functions nie działają na Sparku — brak ruchu
  wychodzącego). Darmowe limity pokrywają wczesny ruch; do sprawdzenia przed fazą 2.

### 5.4 Warstwa kanałów dostarczenia

Jeden interfejs, cztery implementacje. Wspólne: tłumaczymy w Verbigem, potem
przekazujemy do aplikacji zewnętrznej przez `Intent`.

```kotlin
interface OutboundChannel {
    fun isAvailable(context: Context): Boolean
    fun handOff(context: Context, target: ExternalContact, text: String)
}
```

| Kanał | Mechanizm | Ograniczenie |
|---|---|---|
| WhatsApp | `ACTION_VIEW` → `https://wa.me/<E164>?text=<urlencoded>`, `setPackage("com.whatsapp")` + fallback na chooser | Tekst jest wklejony, użytkownik musi kliknąć wysłanie. Numer musi być na WA. |
| SMS | `ACTION_SENDTO` → `smsto:<E164>` + `EXTRA_SMS_BODY` | Prewypełnione, wymaga jednego kliknięcia. **Nie używamy `SmsManager`** — uprawnienie `SEND_SMS` to problem z polityką Google Play. |
| Telegram | `ACTION_VIEW` → `https://t.me/<username>` + schowek + snackbar | **URL Telegrama nie potrafi wkleić tekstu.** Kopiujemy do schowka i informujemy. |
| E-mail | `ACTION_SENDTO` → `mailto:` + `EXTRA_TEXT` | — |

**Uczciwość UX:** wątek zewnętrzny jest wyraźnie oznaczony jako jednokierunkowy —
„Wysyłka ręczna. Odpowiedzi nie wracają do Verbigem". Po handoff nie wiemy, czy
użytkownik faktycznie wysłał, więc rekord zapisujemy jako `handed_off`.

---

## 6. Fazy

### Faza 0 — Ratunek fundamentów

Bez tego żadna dalsza praca nie ma sensu. Mała, możliwa do zrobienia w jednej sesji.

- [ ] **0.1** `AuthRepository.ensureProfile()` + `updateProfile()` dopisują
      `usersPublic/{uid}` (uid, nickname, photoURL, uiLang, searchNick, searchEmail).
- [ ] **0.2** Jednorazowy skrypt (Node, wzorowany na `write_firestore.js`) który
      uzupełnia `usersPublic` dla istniejących użytkowników.
- [ ] **0.3** `ContactsViewModel.searchUsers()` pyta `usersPublic`, nie `users`.
      Wyszukiwanie po `searchNick` i `searchEmail`.
- [ ] **0.4** Model `Friendship`: dodaj `members: List<String>`, zachowaj `uidA/uidB`.
      Skrypt migracyjny dla istniejących dokumentów.
- [ ] **0.5** `ChatRepository`: jeden listener `whereArrayContains("members", uid)`;
      pochodne strumienie `accepted` / `incoming` / `outgoing`. Usuń asymetrię.
- [ ] **0.6** `ChatViewModel`: język z profilu (`speakLangSource`/`speakLangTarget`)
      zamiast hardkodowanego PL→EN. `speak()` czyta w języku wiadomości.
- [ ] **0.7** Reguły Firestore: `usersPublic` (read dla zalogowanych, write tylko
      właściciel przez update), `friendships` pod `members`.
- [ ] **0.8** BottomNav: dodać OCR do `items` albo usunąć z `showBottomNav`.
      Etykiety przenieść do `strings.xml` (6 języków).
- [ ] **0.9** Test na telefonie: dodaj znajomego z dwóch kont, sprawdź że obie
      strony go widzą; wyszukaj po nicku — musi znaleźć.
- [ ] **0.10** README + commit + push.

### Faza 1 — Skrzynka odbiorcza i porządny wątek

Czat 1:1 w pełni użyteczny, **bez backendu**.

- [ ] **1.1** `ChatMessage` v2 (`sourceLang`, `text`, `senderTranslation`, `type`,
      `clientMsgId`, `readBy`). `ChatRepository.sendMessage` przepisany.
- [ ] **1.2** Dokument `chats/{chatId}`: `members`, `lastMessage*`, `readState`, `typing`.
- [ ] **1.3** Nowa trasa `chat/{uid}` w `Screen.kt` + `AppNavigation`. Ekran `chat`
      = lista konwersacji, `chat/{uid}` = wątek.
- [ ] **1.4** `ChatListViewModel.watchChats(uid)` + ekran skrzynki: avatar, nick,
      podgląd ostatniej wiadomości, godzina, licznik nieprzeczytanych.
- [ ] **1.5** **Tłumaczenie u odbiorcy (D1):** `ChatTranslationCache` w Room
      (`chat_translations`). Przy pierwszym wyświetleniu wiadomości → Hy-MT2
      `translateSegmented`, spinner, zapis do cache. Brak modelu → `senderTranslation`.
- [ ] **1.6** Przełącznik „pokaż oryginał" na każdej bańce + możliwość
      przetłumaczenia na inny język po fakcie (czyści wpis z cache).
- [ ] **1.7** Menu po długim naciśnięciu: kopiuj, czytaj (TTS offline),
      czytaj Pro 💎 (`ProFeatureButton` już istnieje), pokaż oryginał, cytuj,
      usuń u mnie (lokalnie w Room + flaga `deletedFor`).
- [ ] **1.8** **Kolejka offline:** `chat_outbox` w Room. `ConnectivityObserver`
      (już istnieje) wyzwala wysyłkę. `clientMsgId` jako id dokumentu → idempotencja.
- [ ] **1.9** Paginacja: najpierw ostatnie 50, starsze dokładane przy scrollu
      (`endBefore`). Realtime listener tylko na najnowszą stronę.
- [ ] **1.10** Potwierdzenia odczytu: reguły pozwalają odbiorcy zmienić **tylko**
      własny klucz w `readBy` i `readState`. Wskaźniki ✓ / ✓✓.
- [ ] **1.11** Wskaźnik „pisze…" + obecność (Firestore z throttlem 5 s; RTDB jest
      tańszy do presence — do rozważenia, jeśli koszty urosną).
- [ ] **1.12** Wyszukiwanie w wiadomościach — klientowo po pobranych stronach.
      Docelowo pole `searchText` (lowercase) + `whereGreaterThanOrEqualTo`.
- [ ] **1.13** Karta kontaktu (`users/{uid}/contacts/{otherUid}`): alias,
      język per kontakt (auto-wykrywany z rozmowy + ręczne nadpisanie),
      wycisz, zablokuj, przypnij, usuń rozmowę.
- [ ] **1.14** Wszystkie stringi × 6 języków. Build + test na telefonie.
- [ ] **1.15** README + commit + push.

### Faza 2 — Backend: Cloud Functions

Warunek: plan Blaze.

- [ ] **2.1** `firebase init functions` (Node 20, TS). Dokumentacja deployu w README.
- [ ] **2.2** Secret Manager: pepper do HMAC. App Check (Play Integrity) na callable.
- [ ] **2.3** `matchContacts` — HMAC + `whereIn` po 30, rate limiting.
- [ ] **2.4** `inviteByPhone` + `onPhoneVerified` (rozwiązywanie zaproszeń).
- [ ] **2.5** **FCM:** rejestracja tokenów, `onMessageCreated` → push do odbiorcy.
      Kanał powiadomień, grupy, akcje (odpowiedz, oznacz jako przeczytane).
- [ ] **2.6** **Weryfikacja numeru (D3):** gate przy pierwszym wejściu w Czat lub
      Kontakty. Firebase Phone Auth → `linkWithCredential` na istniejącym koncie.
      „Pomiń" zostawia czat działający, ale użytkownik nie jest odnajdywalny.
- [ ] **2.7** Reguły: `phoneDirectory` i `invites` bez dostępu klienta,
      `phoneVerified`/`phoneHash` tylko do odczytu z klienta.

### Faza 3 — Kontakty 2.0

- [ ] **3.1** `READ_CONTACTS` + ekran wyjaśnienia (polityka Google Play wymaga
      prominent disclosure i linku do polityki prywatności — do ustalenia URL).
- [ ] **3.2** `PhoneContactsImporter` (ContactsContract): imię, telefony, e-maile,
      miniatura, `starred`.
- [ ] **3.3** libphonenumber → normalizacja E.164.
- [ ] **3.4** Matching przez `matchContacts` → trzy stany kontaktu z §4.1.
- [ ] **3.5** Zapraszanie: systemowy share sheet, SMS z prefillem, `wa.me`, e-mail.
- [ ] **3.6** `external_contacts` + `outbox_messages` w Room; wątek jednokierunkowy
      z kompozytorem i historią wysyłek.
- [ ] **3.7** Import `.vcf` — własny parser, zero zależności.
- [ ] **3.8** Zakładki w Kontaktach: Znajomi / Zaproszenia / Z telefonu /
      Zewnętrzne. Wyszukiwanie po wszystkich naraz.
- [ ] **3.9** „Możesz znać" — znajomi moich znajomych, bez już dodanych.
- [ ] **3.10** Stringi × 6. Build + test na telefonie. README + commit + push.

### Faza 4 — Kody QR

- [ ] **4.1** Mój kod QR — karta z bitmapą (`https://mini.verbigem.com/u/<uid>`).
      **Generowanie wymaga biblioteki** — ZXing `core` (`com.google.zxing:core:3.5.3`,
      Maven Central odpowiedział 200).
- [ ] **4.2** Skaner — preferowane `com.google.android.gms:play-services-code-scanner`
      (sam obsługuje kamerę i uprawnienia). Fallback: CameraX + ML Kit Barcode
      (projekt ma już CameraX).
- [ ] **4.3** `intent-filter` na `https://mini.verbigem.com/u/<uid>` (App Links)
      → otwiera kartę kontaktu z przyciskiem „Dodaj do znajomych".

### Faza 5 — Media

- [ ] **5.1** Firebase Storage + reguły (`chat_attachments/{chatId}/{msgId}`).
- [ ] **5.2** Zdjęcia: wyślij → opcjonalnie OCR na nadawcy (`OcrManager` już jest)
      → `ocrText` w dokumencie → odbiorca tłumaczy tekst swoim językiem.
- [ ] **5.3** Głosówki: nagrywanie → upload m4a → transkrypcja STT na nadawcy
      → tekst podlega zwykłemu tłumaczeniu u odbiorcy. `SpeechManager` już istnieje.
- [ ] **5.4** Pobieranie, postęp, cache offline, podgląd na pełnym ekranie.

### Faza 6 — Czaty grupowe (odłożone)

Nie wybrane przez Milosza. Zostaje jako opcjonalna: `chats` z `members` o długości
> 2, role (admin/członek), opuszczanie grupy, nazwa i avatar grupy, koszty
tłumaczenia rosną liniowo (każdy odbiorca tłumaczy na swój język —
akurat tu model „u odbiorcy" błyszczy).

---

## 7. Wpływ na reguły Firestore

Każda zmiana wymaga `firebase deploy --only firestore:rules --project mini-verbigem`.

| Kolekcja | Zmiana | Faza |
|---|---|---|
| `usersPublic/{uid}` | read dla zalogowanych; write tylko właściciel (update) | 0 |
| `friendships/{id}` | zapytania po `members`; blokada zmiany `members`/`uidA`/`uidB` przy update | 0 |
| `chats/{chatId}/messages` | **złagodzić `update: if false`** — odbiorca może zmienić tylko własny klucz w `readBy` | 1 |
| `chats/{chatId}` | update tylko dla członków, tylko `lastMessage*`/`readState.<uid>`/`typing.<uid>` | 1 |
| `users/{uid}/contacts/{otherUid}` | read/write tylko właściciel | 1 |
| `phoneDirectory`, `invites` | **brak dostępu klienta** (tylko funkcje) | 2 |
| `users/{uid}` | `phoneVerified`/`phoneHash` nie do zapisu z klienta (chronione przez `hasAny`) | 2 |
| `chat_attachments/**` | Storage: tylko członkowie czatu | 5 |

---

## 8. Zależności do dodania

**Projekt buduje się offline z lokalnego cache Gradle** (patrz README — dlatego
kiedyś wyleciało Paging 3). Przed dodaniem każdej zależności: **sprawdzić, czy
się pobiera**, ewentualnie zbudować raz online, żeby zapełnić cache.

| Zależność | Po co | Faza | Ryzyko |
|---|---|---|---|
| `com.googlecode.libphonenumber:libphonenumber` | normalizacja E.164 | 3 | niskie — popularna, stabilna |
| `com.google.firebase:firebase-functions-ktx` | wywołania callable | 2 | niskie — w BOM 33.2.0 |
| `com.google.firebase:firebase-messaging` | tokeny FCM | 2 | niskie |
| `com.google.firebase:firebase-storage` | zdjęcia, głosówki | 5 | niskie |
| `com.google.firebase:firebase-auth` (Phone) | weryfikacja numeru | 2 | **już jest** w BOM, telefonówka wymaga Blaze |
| `com.google.zxing:core` | generowanie QR | 4 | średnie — nowe źródło artefaktów |
| `com.google.android.gms:play-services-code-scanner` | skaner QR | 4 | średnie — wymaga GMS |

---

## 9. Ryzyka i pułapki

| Ryzyko | Szczegół | Mitygacja |
|---|---|---|
| **Plan Blaze** | Cloud Functions nie działają na Sparku | Przed fazą 2 sprawdzić koszty i limity; FCM i Storage mieszczą się w darmowych progach |
| **Polityka Google Play — READ_CONTACTS** | Wymaga prominent disclosure w aplikacji + polityki prywatności. Złe uzasadnienie = odrzucenie | Ekran wyjaśnienia przed dialogiem uprawnień; URL polityki do ustalenia |
| **Rainbow table na hashe numerów** | Jeśli pepper wycieknie, można odtworzyć mapowanie numer→uid | App Check, rate limiting, pepper w Secret Managerze, brak odczytu klienta |
| **Koszt tłumaczenia u odbiorcy** | Model 1.8B, ~3–4 tok/s. Długa wiadomość = kilka sekund | Cache w Room, spinner, `senderTranslation` jako natychmiastowy fallback |
| **Rozmiar modelu** | 440 MB (Fast) / 1.1 GB (Accurate). Odbiorca bez modelu nie przetłumaczy | Właśnie dlatego wysyłamy `senderTranslation` — wątek działa od razu |
| **Firestore `in` ograniczone do 30** | `matchContacts` z 500 kontaktami | Chunkowanie po 30 w funkcji |
| **Koszty odczytu w czacie** | Realtime listener na każdym otwartym wątku | Listener tylko na najnowszą stronę, paginacja starszych |
| **Migracja `usersPublic`** | Istniejący użytkownicy nie mają dokumentu → wyszukiwanie ich nie znajdzie | Skrypt jednorazowy w zadaniu 0.2 |
| **Nadpisywanie nazwy APK** (projektowa pułapka) | CDN trzyma `/android/**` jako immutable przez rok | Każde wydanie = nowy `versionCode` = nowa nazwa pliku. Patrz README |

---

## 10. Konwencje projektowe — przypomnienie

Obowiązują przy każdej fazie (z `README.md` i notatek projektu):

1. **README.md aktualizujemy w tym samym turnie, co zmianę kodu.** Szczególnie
   sekcje: „Kluczowe funkcje" (3. Czat zdalny, 4. Kontakty), „Architektura
   techniczna", „Firebase — konfiguracja projektu", „Synchronizacja danych".
2. **Zakaz hardkodowania tekstów UI.** Każdy string → `values/strings.xml`
   + `values-pl`, `-de`, `-es`, `-zh`, `-tr`. Nawet tymczasowy angielski fallback.
3. **Po każdej zmianie większej niż kosmetyczna: commit + `git push`** na
   `origin/master`. Projekt `mini` (webapp) nie ma repozytorium.
4. **Wydanie = nowy `versionCode`** (+1) i `versionName` = `1.0.(code-1)`, nowa
   nazwa APK, wpis w `vite.config.ts` i `dist/updates/version.json`,
   `firebase deploy --only hosting`.
5. **Bezpieczeństwo plików osobistych:** żadnego `rm -rf`, żadnych operacji na
   katalogach domowych bez wyraźnego potwierdzenia.
6. **Zachowanie gestów i UX weryfikuje się tylko na telefonie.** Build to nie to
   samo co działający interfejs (dotyczy zwłaszcza klawiatury, scrollowania i
   długiego naciśnięcia).

---

## 11. Pytania otwarte

Do rozstrzygnięcia w trakcie — nie blokują startu fazy 0.

1. **URL polityki prywatności** — wymagany przez Google Play przy `READ_CONTACTS`.
   Czy już istnieje na `verbigem.com`?
2. **Czy import kontaktów ma być jednorazowy, czy ciągły?** (opcjonalny okresowy
   re-match w tle, np. raz na tydzień, żeby wykryć znajomych którzy dołączyli).
3. **Czy kontakty zewnętrzne synchronizować z chmurą?** Na razie zakładam, że
   zostają lokalne (prywatność) — ale wtedy znikają po zmianie telefonu.
4. **Kto płaci za tłumaczenie w chmurze** w fallbacku dla użytkowników bez modelu?
   Czy `senderTranslation` jest darmowe (nadawca używa swojego modelu), czy Pro?
5. **Retencja wiadomości** — czy kasować stare wiadomości z Firestore po X dniach
   (koszty vs. historia)?
