# Czat i Kontakty — plan budowy

Dokument roboczy. Praca jest rozbita na fazy, z których każda kończy się stanem,
który da się zbudować i przetestować na telefonie. **Każdą sesję zaczynamy od
sekcji „Postęp", a kończymy jej aktualizacją** — dzięki temu kolejna sesja wie,
gdzie skończyliśmy, bez czytania całego pliku.

Ostatnia aktualizacja: 2026-09-04 — **faza 2 wystartowała: 2.1 i 2.5 w kodzie.**
`functions/` (Node 20 + TS) gotowy, `onMessageCreated` wysyła push FCM, aplikacja
rejestruje tokeny w `users/{uid}/fcmTokens/{token}` i pokazuje powiadomienia z
kanałem, grupą oraz akcjami „Odpowiedz" / „Oznacz jako przeczytane". **Reguły
`fcmTokens` i funkcja czekają na `firebase deploy`** — do tego czasu push nie działa.

Wcześniej tego samego dnia: **1.13 Karta kontaktu WYKONANA w kodzie.**
`users/{uid}/contacts/{otherUid}` (alias, język tłumaczenia, przypnij, wycisz,
zablokuj, notatka) + ekran `contact/{uid}` + reguły wdrożone + Room v7
(`chat_hidden` dla „usuń rozmowę"). Alias i przypięcie żyją już w skrzynce,
`langOverride` steruje tłumaczeniem u odbiorcy.

**Stan wydań:** v26 (faza 0 + faza 1) **jest na produkcji** — zweryfikowane
`https://mini.verbigem.com/updates/version.json` → `versionCode: 26`.
**1.13 czeka na wydanie** (build przeszedł, `app-debug.apk` gotowy):
wymaga podbicia `versionCode` na 27 i wrzucenia `app-debug-v27.apk`.
**Testy na telefonie (0.9, 1.14, 1.16) nadal są u Milosza** — bez nich nie ma
podstaw, żeby uznać fazę 1 za domkniętą.

> **B6 rozstrzygnięte 2026-09-03 — Milosz miał rację tylko połowicznie.**
> Zweryfikowane na żywym kodzie (`ui/components/BottomNav.kt`):
> - ❌ „nie ma hardkodowanych etykiet" — **są**, i to mieszane EN/PL:
>   `"Translator"`, `"Rozmowa"`, `"Czat"`, `"Kontakty"`, `"Profil"`.
>   Co gorsza, `nav_*` istniały w `strings.xml` we wszystkich 6 językach i
>   **nie były nigdzie używane** (grep `R.string.nav_` = zero trafień).
> - ✅ „nie ma OCR" — faktycznie, `items` ma 5 pozycji i OCR nie ma wśród nich.
>   Natomiast `Screen.Ocr.route` **był** w `showBottomNav`, co dawało pasek
>   nawigacji na ekranie OCR bez możliwości powrotu/usunięcia zaznaczenia.
> **Rozstrzygnięcie:** etykiety → `stringResource`, OCR **wyjęty** z
> `showBottomNav` (5 ikon jest kompletnych, OCR zostaje na ekranie
> Translatora + systemowy back). Punkt B6 → zamknięty.

---

## 1. Postęp

| Faza | Nazwa | Status | versionCode | Commit |
|---|---|---|---|---|
| 0 | Ratunek fundamentów | 🟡 **kod gotowy, v26 na produkcji** (0.9 — test na telefonie został) | 26 | `bae1df0` |
| 1 | Skrzynka odbiorcza + wątek | 🟡 **kod gotowy, v26 na produkcji** (1.14 — test na telefonie został) | 26 | `bae1df0` |
| 1.12 | Wyszukiwanie w wiadomościach | ⬜ odłożone (patrz „Odłożone" niżej) | — | — |
| 1.13 | Karta kontaktu | 🟡 **kod gotowy** (1.16 — test na telefonie został) | 27 | `4f39292` |
| 2.1 | Szkielet `functions/` (Node 20 + TS) | ✅ **zrobione** (kod + dokumentacja w README) | — | *w toku* |
| 2.5 | FCM: tokeny + `onMessageCreated` → push | 🟡 **wdrożone na produkcję** (test push u Milosza został) | 27 | `a4e65d5` |
| 2 | Backend: Cloud Functions | 🟡 **w toku** (2.1 ✅, 2.5 🟡; 2.2–2.4, 2.6–2.7 nie zaczęte) | — | — |
| 3 | Kontakty 2.0 (import, kanały) | 🟡 **częściowo** (3.0–3.2/3.5) | 25 | `93c6fe1` |
| 4 | Kody QR | ⬜ nie rozpoczęta | — | — |
| 5 | Media: zdjęcia + OCR, głosówki | ⬜ nie rozpoczęta | — | — |
| 6 | Czaty grupowe | ⏸ odłożone (nie wybrane) | — | — |
| PP | Polityka prywatności (§12) | ✅ **zrobione** | 25 | `93c6fe1` |

**Co zrobione w sesji 2026-09-04 (faza 1):**

- **1.1** ✅ `ChatMessage` v2: `senderTranslation` (`{lang, text}`), `type`,
  `clientMsgId`, zachowane legacy `translatedText` (stare wątki nadal się
  renderują przez `hintText()`). `sendMessage` przepisany na `set()` po
  `clientMsgId`.
- **1.2** ✅ Dokument `chats/{chatId}`: `members`, `lastMessage`,
  `lastMessageAuthorId`, `lastMessageAt` (upsert przed wiadomością — bez tego
  reguły odrzucają insert).
- **1.3** ✅ Trasy: `chat` = skrzynka (`ChatListScreen`), `chat/{uid}` = wątek
  (`ChatThreadScreen`). `ChatScreen` i `ChatViewModel` usunięte.
- **1.4** ✅ `ChatListViewModel` + skrzynka: avatar, nick (z `usersPublic`,
  fallback na nick ze znajomości), podgląd, godzina, kropka nieprzeczytanych.
- **1.5** ✅ **Tłumaczenie u odbiorcy (D1):** `chat_translations` w Room,
  `translateSegmented`, spinner, zapis do cache, `senderTranslation` jako
  natychmiastowy fallback. Jeden `Mutex` — model ładuje się raz.
- **1.6** ✅ Przełącznik „pokaż oryginał" (menu + podgląd oryginału pod bańką)
  i „Przetłumacz" ponownie (czyści wpis cache, wraca do auto-tłumaczenia).
- **1.7** ✅ Menu po długim naciśnięciu: kopiuj, czytaj (TTS offline),
  czytaj Pro 💎, pokaż oryginał / tłumaczenie, cytuj, usuń u mnie.
- **1.8** ✅ Kolejka offline `chat_outbox` + `ConnectivityObserver` wyzwala flush.
- **1.9** ✅ Paginacja: live listener na 50 najnowszych, `startAfter` przy
  dojechaniu na górę listy.
- **1.10** ✅ Potwierdzenia odczytu: **subkolekcja** `readReceipts/{uid}` zamiast
  `readBy` na wiadomości — patrz „Decyzja: readReceipts zamiast `update`".
- **1.11** ✅ Wskaźnik „pisze…": subkolekcja `typing/{uid}` z `expiresAt`,
  zapis throttlowany (4 s), heartbeat 1,5 s w VM odświeża wygaśnięcie.
- **1.12** ⬜ wyszukiwanie w wiadomościach — **odłożone** (patrz niżej).
- **1.13** ✅ karta kontaktu — **wykonana** (szczegóły w bloku niżej).
- **1.14** ⬜ test na telefonie (faza 1) — **zostaje dla Milosza**.
- **1.15** ✅ README + `chat_kontakty.md`; commit + push na koniec sesji.
- **1.16** ⬜ test na telefonie (karta kontaktu) — **zostaje dla Milosza**.
- **1.17** ⬜ test na telefonie (push FCM) — **zostaje dla Milosza** (patrz faza 2 niżej).

**Co zrobione w sesji 2026-09-04 (faza 2 — 2.1 i 2.5):**

- **2.1** ✅ **`functions/` od zera:** `package.json` (Node 20, `firebase-admin` 12,
  `firebase-functions` 6), `tsconfig.json` (commonjs, strict, `noUnusedLocals`),
  `.gitignore` (`node_modules/`, `lib/`), `src/index.ts`. `npm install` + `npm run
  build` przechodzą czysto. `firebase.json` dostał blok `functions` z `runtime:
  nodejs20` i `predeploy: npm run build`. Sekcja „☁️ Cloud Functions" w README:
  tabela funkcji, komendy deployu, Secret Manager, 6 decyzji pushy.
- **2.5** ✅ **FCM end-to-end w kodzie:**
  - `functions/src/messaging.ts` — `onDocumentCreated(chats/{chatId}/messages/{msgId})`
    → `sendEachForMulticast` do pozostałych członków. `members` czytane z dokumentu
    czatu (wiadomość jest append-only i nie ma rosteru). Martwe tokeny kasowane po ID.
    `Promise.allSettled` — nieudany push **nigdy** nie failuje triggera (wiadomość już
    jest w Firestore, retry = podwójne powiadomienia).
  - **Treść pusha = `senderTranslation`, nie `text`** (D1: podpowiedź nadawcy powstała
    w języku odbiorcy). **Podgląd DOMYŚLNIE WYŁĄCZONY** — `app_config/notifications`
    musi mieć `showMessagePreview == true`, inaczej „Nowa wiadomość". Push wychodzi z
    urządzenia i idzie przez serwery Google, więc failuje zamknięty.
  - **Wyciszenie honorowane w chmurze** (`users/{odbiorca}/contacts/{nadawca}` →
    `muted === true`), nie na urządzeniu: wyciszony czat nie budzi telefonu.
  - **Android:** `VerbigemMessagingService`, `FcmTokenManager`
    (`users/{uid}/fcmTokens/{token}` — **ID dokumentu = token**, żeby funkcja mogła
    kasować bez odczytu), `VerbigemNotifications` (kanał `verbigem_messages`, grupa
    per czat + podsumowanie, MessagingStyle z historią, akcje),
    `NotificationActionReceiver` (odpowiedź + „oznacz jako przeczytane" pod
    `goAsync()`, bo robią zapis do Firestore).
  - **Kanał powiadomień** tworzony w `VerbigemApplication.onCreate`, nie przy
    pierwszym pushu — użytkownik szukający „jak to wyłączyć" znajdzie go w ustawieniach.
  - **Tap → wątek:** `MainActivity.intentForChat()` + `onNewIntent` + flow
    `openChatUid` w Compose. `AppNavigation` czeka na odtworzenie sesji Auth
    (do 3 s), bo na zimnym starcie `currentUser` jest jeszcze nullem.
  - **`POST_NOTIFICATIONS` proszony raz, przy pierwszym otwarciu skrzynki**
    (flaga `asked_notif_perm` w DataStore) — nie na starcie aplikacji: Android
    przestaje pytać po dwóch odmowach, więc szkoda je przepalać.
  - **Reguły Firestore:** `users/{uid}/fcmTokens/{token}` (właściciel + whitelist
    pól + `data.token == token`). **Nowe stringi × 6** (6 kluczy `notif_*`).
  - **Wylogowanie kasuje token przed `signOut()`** — po wylogowaniu `currentUser`
    jest null i dokument zostałby osierocony.

**⚠️ Pułapka: `MessagingStyle` ma własną metodę `apply()`.**
`NotificationCompat.MessagingStyle.apply { ... }` **nie wywołuje** kotlinowej
funkcji `apply` — `MessagingStyle` deklaruje własny element
`apply(NotificationBuilderWithBuilderAccessor)`, który ją przysłania. Kompilator
zgłasza „Unresolved reference" dla każdego wywołania wewnątrz lambdy. Na tej
klasie piszemy zwykłe instrukcje, nie `.apply {}`.

**⚠️ Decyzja: FCM jest `data-only` (bez pola `notification`).**
Z polem `notification` Android sam renderuje powiadomienie w tle i **nie wywołuje
`onMessageReceived`** — akcje „Odpowiedz" / „Oznacz jako przeczytane" działałyby
tylko dla wiadomości przychodzących przy otwartej aplikacji. Data-only daje pełną
kontrolę zawsze; ceną jest Doze, stąd `priority: "high"` i TTL 4 tygodnie.

**Wdrożone 2026-09-04:** reguły `fcmTokens` **i** funkcja `onMessageCreated`
(`firebase deploy`, projekt `mini-verbigem`, region `us-central1`).
**Push wymaga APK z tym kodem** (v27, jeszcze nie wydany) — tokeny rejestrują się
dopiero po uruchomieniu aplikacji z `FcmTokenManager`.

**⚠️ Pierwszy deploy funkcji 2. gen zawsze rzuca błąd Eventarc.** Firebase mówi
wprost: „retry the deployment in a few minutes" — uprawnienia Service Agent
propagują się z opóźnieniem. Drugi deploy przeszedł. Po udanym deployu CLI żąda
jeszcze `functions:artifacts:setpolicy` (obrazy kontenerów w Artifact Registry
rosną i kosztują); ustawione na 7 dni.

**⚠️ `matchContacts` jest napisany, ale celowo NIE wyeksportowany**
(zakomentowany w `functions/src/index.ts`). Deklaruje sekret `PHONE_HASH_PEPPER`,
a Firebase weryfikuje istnienie sekretów przy deployu — wyeksportowanie go
przed `functions:secrets:set` blokuje **każdy** deploy, także pushowy. Włączyć
przy 2.3.

**1.17** ⬜ **test push na telefonie — zostaje dla Milosza:** wysłać wiadomość
z drugiego konta (aplikacja w tle / ekran zgaszony), sprawdzić powiadomienie,
akcję „Odpowiedz" (czy wiadomość doszła) i „Oznacz jako przeczytane", oraz
tap → czy otwiera właściwy wątek. Podgląd treści jest domyślnie WYŁĄCZONY,
więc na ekranie blokady ma być „Nowa wiadomość", nie treść.

**Co zrobione po fazie 1 — 1.13 Karta kontaktu (2026-09-04):**

- **Model + repozytorium.** `ContactSettings` w `CommonModels.kt` (`alias`,
  `langOverride`, `muted`, `pinned`, `blocked`, `note`, `updatedAt`).
  `ChatRepository`: jeden listener `watchContactSettings(uid)` na całą
  subkolekcję (jeden subskrypcja zamiast N) + `saveContactSettings` /
  `clearContactSettings`. Błąd zapisu idzie tylko do logcatu — utrata aliasu
  jest irytująca, utrata wątku nie do przyjęcia, więc nigdy nie wywala UI.
- **Ekran `ContactCardScreen` + `ContactCardViewModel`.** Alias, język
  tłumaczenia (Auto + 6 języków jako chipy), przypnij / wycisz / zablokuj,
  notatka, „Napisz wiadomość", „Usuń rozmowę" z dialogiem potwierdzenia.
  Wchodzi się z nagłówka wątku i z ikony ⓘ przy znajomym w Kontaktach.
- **Zapis z debounce 500 ms** dla pól tekstowych (alias, notatka), natychmiastowy
  dla przełączników. Flaga `pendingWrite` wycisza listener Firestore w trakcie
  własnego zapisu — bez niej snapshot przychodzący w środku pisania cofałby tekst.
- **Integracja ze skrzynką:** alias w nazwie, przypięte na górę
  (`compareByDescending<ChatRow> { it.pinned }.thenByDescending { it.lastMessageAt }`),
  zablokowane i usunięte odfiltrowane, wyciszone bez kropki nieprzeczytanych.
- **Integracja z wątkiem:** nagłówek klikalny → karta; `translationLang`
  (override ❔ profil) steruje celem tłumaczenia; zmiana języka czyści mapę
  w pamięci, ale nie rusza cache w Room (kluczowany językiem).
- **Room v6→v7:** tabela `chat_hidden` dla „usuń rozmowę". Wiadomości w Firestore
  są append-only i nie ma funkcji, która by je sprzątała, więc usunięcie może być
  tylko lokalnym ukryciem — dialog mówi to wprost.
- **Stringi × 6 + reguły Firestore wdrożone** (`users/{uid}/contacts/{otherUid}`:
  właściciel + whitelist 7 pól).
- **Build:** `assembleDebug` przeszedł (1m20s, NDK z cache).

**Uczciwość funkcji — zapisane w stringach, nie tylko w komentarzach:**
„zablokuj" i „wycisz" są **lokalne** (brak Cloud Functions do egzekwowania
blokady i brak pushy do wyciszania), a „usuń rozmowę" **nie kasuje** wiadomości
u rozmówcy. Teksty w UI o tym mówią — nie wolno ich „poprawić" na brzmiące lepiej,
dopóki faza 2 nie dowiezie prawdziwej blokady.

> **Decyzja: `readReceipts` zamiast łagodzenia `update` na `messages`.**
> Plan zakładał dopuszczenie `update` na wiadomościach, żeby odbiorca mógł
> zmienić własny klucz w `readBy`. Reguła musiałaby analizować diff zagnieżdżonej
> mapy (`resource.data.get('readBy', {}).diff(...)`), co jest kruche (stare
> dokumenty bez `readBy`), i otwiera furtkę na modyfikację cudzych wiadomości.
> Zamiast tego każdy uczestnik ma **jeden własny dokument** w subkolekcji —
> reguła to po prostu `request.auth.uid == uid`. Wiadomości w Firestore
> zostają **append-only** (`update, delete: if false`), a „usuń u mnie"
> realizują lokalne tombstone'y w Room. B5 uznajemy za rozwiązane inaczej.

**Odłożone z fazy 1 (świadomie, nie zapomniane):**

- **1.12 Wyszukiwanie w wiadomościach** — bez backendu i tak działałoby tylko po
  pobranych stronach, a prawdziwe wyszukiwanie wymaga pola `searchText` +
  (docelowo) indeksu. Lepiej zrobić je razem z fazą 2, kiedy możemy też
  dodać trigger zapisujący `searchText`.
- ~~**1.13 Karta kontaktu**~~ — **WYKONANE** (2026-09-04), patrz blok wyżej.
  Zrobione bez czekania na test telefonu, bo to własny tor: nie dotyka
  wysyłania ani tłumaczenia, tylko nakłada się na już działającą skrzynkę.

**Co zrobione w sesji 2026-09-03 → 04, noc (faza 0):**

- **Faza 0 — wykonana w całości poza testem na telefonie:**
  - **0.1** ✅ `AuthRepository.ensureProfile()` / `updateProfile()` utrzymują
    `usersPublic/{uid}` (nick, e-mail, avatar, `uiLang`, `speakLangSource/Target`,
    zlowercasowane `searchNick`/`searchEmail`). `ensureProfile` jest też
    samoleczącym backfillem — każde logowanie odnawia wizytówkę.
    Nowe `PublicProfile` w `CommonModels.kt` + `getPublicProfile(uid)`.
  - **0.2** ✅ `backfill_faza0.js` (domyślnie dry-run, `--apply` zapisuje).
    **Uruchomiony: 4/4 konta uzupełnione, zweryfikowane odczytem z Firestore.**
  - **0.3** ✅ `ContactsViewModel.searchUsers()` pyta `usersPublic` po
    `searchNick` **i** `searchEmail` (dwa zapytania — Firestore nie ma OR),
    wyniki scalone i zdeduplikowane po uid. Błąd przestał się chować: idzie do
    logcatu zamiast udawać „zero wyników".
  - **0.4** ✅ `Friendship` zyskało `members: List<String>` (posortowane) plus
    helpery `otherUid()` / `otherNickname()` / `isIncoming()` / `isOutgoing()`.
  - **0.5** ✅ `ChatRepository`: **jeden** listener
    `whereArrayContains("members", uid)` + pochodne `watchAccepted` /
    `watchIncoming` / `watchOutgoing`. `requestFriendship` zapisuje `members`.
    Asymetria uidA usunięta.
  - **0.6** ✅ `ChatViewModel`: język z profilu (mój = `speakLangSource`,
    rozmówcy = jego `speakLangSource` z `usersPublic`). `speak()` czyta w języku
    aktualnie wyświetlanego tekstu. `translateSegmented` zamiast `translate`.
  - **0.7** ✅ Reguły wdrożone (`firebase deploy --only firestore:rules`):
    `usersPublic` — zapis tylko właściciela; `friendships` — odczyt/zapis po
    `members`, zablokowana zmiana składu i pól funkcji.
  - **0.8** ✅ Etykiety BottomNav → `stringResource(R.string.nav_*)`.
    OCR wyjęte z `showBottomNav` (patrz werdykt B6 na górze pliku).
  - **0.9** ⬜ **test na telefonie — zostaje dla Milosza.**
- **Znaleziska naprawione przy okazji (nie było na liście fazy 0):**
  - **Czat w ogóle nie mógł wysłać wiadomości.** Reguły `messages` robią
    `get(/chats/$(chatId)).data.members`, a `sendMessage()` nigdy nie tworzyło
    dokumentu czatu. Teraz `chats/{chatId}` jest upsertowany z `members`
    **przed** dodaniem wiadomości.
  - **5 hardcodedowanych polskich stringów** (`"Odsłuchaj"`, `"Wyślij"` ×2,
    `"Mów"`, `"OK"`) → nowe klucze `chat_read_aloud`, `action_search`,
    `action_speak`, `ok` × 6 języków.

**Gdzie skończyliśmy:** faza 0 w kodzie gotowa, reguły na serwerze, dane
zbackfillowane. **APK nie wydany** — brak commita, pusha i podbicia wersji.
**Następny krok:**
1. **0.9** — zainstalować, zalogować się na dwa konta, dodać znajomego,
   sprawdzić że obie strony go widzą + wyszukać po nicku i e-mailu.
2. **Wydanie v26** (nowy `versionCode`, APK `app-debug-v26.apk`,
   `vite.config.ts` + `dist/updates/version.json`, `firebase deploy`).
3. **Faza 1** — skrzynka odbiorcza i porządny wątek.

**Ważne pułapki do pamiętania:**
- **Skrypty Node z Firestore (`write_firestore.js`, `update_firestore_update.js`)
  mają MARTWĄ ścieżkę odświeżania tokena.** Siedzą w nich na sztywno dwa różne
  `client_id` (oba błędne) i jeden `client_secret` — `oauth2.googleapis.com`
  odpowiada `invalid_client`, więc po wygaśnięciu tokena skrypt umiera.
  **Działające obejście:** odśwież przez CLI (`firebase projects:list` — odczyt,
  zero skutków ubocznych), który ma poprawne poświadczenia wbudowane i zapisuje
  świeży `access_token` do `~/.config/configstore/firebase-tools.json`; potem
  skrypt czyta go z dysku. Tak robi `backfill_faza0.js`.
- Token z `firebase login` wygasa po ~1 h (`expires_at` w configstore). Skrypt
  musi sprawdzać `expires_at`, nie zakładać że `access_token` jest żywy.
- **REST API Firestore z tokenem właściciela projektu omija reguły bezpieczeństwa**
  — backfill mógł zapisać cudze `usersPublic/{uid}` mimo reguły „tylko
  właściciel". Wygooglać się nie da: to cecha, nie bug. Pamiętać, że skrypt
  backfillu może więcej niż aplikacja.
- `/privacy/**` w `firebase.json` ma cache 1h (**nie** `immutable`), styl
  inlinowany (bo `**/*.css` ma `immutable`).
- Pliki polityki są w `mini/dist/privacy/` **i** `mini/public/privacy/`
  (deploy bierze z `dist/`; `public/` przeżyje `npm run build`).
- Przed deployem polityki **nie odpalać `npm run build`** (czyści `dist/`).

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
| D6 | **Plan Blaze** | **Potwierdzony — Milosz ma Blaze** (2026-09-03). Cloud Functions i FCM nie są zablokowane kosztowo. Nadal pilnować limitów i App Check. |
| D7 | **Polityka prywatności** | **OPUBLIKOWANA (2026-09-03).** 6 wersji językowych (PL/EN/DE/ES/ZH/TR) na `mini.verbigem.com/privacy/`, link z aplikacji (Profil) + do zgłoszenia w Google Play. Kontakt: **`privacy@verbigem.com`**. Szczegóły w §12. Faza 3 odblokowana. |

---

## 3. Diagnoza stanu obecnego

Sześć realnych błędów. **Faza 0 musiała pójść pierwsza** — nadbudowa na tym
fundamencie to budowanie na piasku.

**Stan po fazie 0 (2026-09-04):** B1 ✅, B2 ✅, B3 ✅ (częściowo — pełne
tłumaczenie u odbiorcy to 1.5), B6 ✅. **B4 i B5 zostają na fazę 1.**

| # | Błąd | Gdzie | Skutek | Stan |
|---|---|---|---|---|
| B1 | **Wyszukiwanie ludzi nie działa wcale.** `searchUsers()` odpytuje `users/`, reguły pozwalają czytać tylko własny dokument. Reguły przewidują `usersPublic/{uid}`, ale **żaden kod tego dokumentu nie tworzy** (grep: zero wystąpień). | `ContactsViewModel.kt:76` | Każde wyszukiwanie → `PERMISSION_DENIED`. | ✅ **NAPRAWIONE.** `AuthRepository` utrzymuje `usersPublic`, wyszukiwanie pyta `searchNick`/`searchEmail`, backfill wykonany (4/4 konta). |
| B2 | **Znajomi tylko w jedną stronę.** `watchFriendships()` filtruje `whereEqualTo("uidA", uid)`. Osoba zaproszona (jako `uidB`) nigdy nie widzi znajomego. | `ChatRepository.kt:60-68` | Połowa zaproszeń znika po akceptacji. | ✅ **NAPRAWIONE.** `members: [uidA, uidB]` + jeden listener `whereArrayContains`. Trzy strumienie pochodne (`accepted`/`incoming`/`outgoing`). |
| B3 | **Tłumaczenie hardkodowane PL→EN.** `hyMt2Engine.translate(text, PL, EN)` ignoruje profile obu stron. `speak()` też ma na sztywno `"en"` dla przychodzących. | `ChatViewModel.kt:68`, `ChatScreen.kt:141` | Czat tłumaczy w losowy język. | 🟡 **NAPRAWIONE CZĘŚCIOWO.** Język bierze się z profilu obu stron, `speak()` czyta w języku wyświetlanego tekstu. Nadal brak tłumaczenia u odbiorcy (1.5) — odbiorca widzi podpowiedź nadawcy. |
| B4 | **Brak listy konwersacji.** Ekran Czatu to napis „wybierz znajomego w Kontaktach". Nie ma jak wrócić do toczącej się rozmowy. | `ChatScreen.kt:64-87` | Czat nieużywalny jako komunikator. | ⬜ faza 1 (1.3, 1.4) |
| B5 | **Reguły blokują `update` na wiadomościach** (`allow update, delete: if false`). | `firestore.rules` → `chats/{chatId}/messages` | Potwierdzenia odczytu, edycja i usuwanie są dziś niemożliwe. | ⬜ faza 1 (1.10) |
| B6 | **BottomNav.** Etykiety hardkodowane (mieszane EN/PL), a `Screen.Ocr.route` był w `showBottomNav` mimo braku pozycji OCR w `items`. | `BottomNav.kt:41-47`, `AppNavigation.kt:55-62` | Nav nie do przetłumaczenia; pasek na ekranie OCR bez możliwości powrotu. | ✅ **NAPRAWIONE.** Etykiety → `stringResource(R.string.nav_*)`, OCR wyjęte z `showBottomNav`. |

> **B7 — znaleziony przy okazji, już naprawiony:** czat **w ogóle nie mógł
> wysłać wiadomości.** Reguły `chats/{chatId}/messages` robią
> `get(/chats/$(chatId)).data.members`, a `sendMessage()` nigdy nie tworzyło
> dokumentu czatu — każda próba leciała na `PERMISSION_DENIED`. Teraz
> `chats/{chatId}` jest upsertowany z `members` **zanim** poleci wiadomość.

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

### 5.2 Room — migracja v5 → v7

Cztery nowe tabele w v6 + jedna w v7. Wzorowane na istniejącym `pending_deletes`
(małe, wyspecjalizowane).

- **`chat_hidden`** (v6→v7, zadanie 1.13) — rozmowy usunięte ze skrzynki.
  `chatId TEXT PK, hiddenAt INTEGER`. Wiadomości w Firestore są append-only, więc
  „usuń rozmowę" nie ma jak ich skasować — zostaje ukrycie lokalne.
  **Ustawienia per kontakt NIE trafiły do Room** — idą do Firestore
  (`users/{uid}/contacts/{otherUid}`), bo Firestore i tak trzyma cache offline,
  a ustawienia mają iść za kontem na drugie urządzenie bez nowego mechanizmu syncu.

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

- [x] **0.1** `AuthRepository.ensureProfile()` + `updateProfile()` dopisują
      `usersPublic/{uid}` (uid, nickname, photoURL, uiLang, speakLangSource,
      speakLangTarget, searchNick, searchEmail). Nowy model `PublicProfile`.
      `ensureProfile` działa też jako samoleczący backfill przy każdym logowaniu.
- [x] **0.2** `backfill_faza0.js` (Node) — uzupełnia `usersPublic` i dorabia
      `members` na starych znajomościach. **Domyślnie dry-run**, zapis dopiero
      z `--apply`. **Uruchomiony: 4/4 konta.**
- [x] **0.3** `ContactsViewModel.searchUsers()` pyta `usersPublic`, nie `users`.
      Wyszukiwanie po `searchNick` i `searchEmail` (dwa zapytania, scalone).
- [x] **0.4** Model `Friendship`: `members: List<String>` (posortowane), `uidA/uidB`
      zachowane. Skrypt migracyjny w `backfill_faza0.js` (faza 2 skryptu).
- [x] **0.5** `ChatRepository`: jeden listener `whereArrayContains("members", uid)`;
      pochodne strumienie `watchAccepted` / `watchIncoming` / `watchOutgoing`.
- [x] **0.6** `ChatViewModel`: `myLang` z własnego profilu, `otherLang` z
      `usersPublic/{otherUid}`, `speak()` czyta w języku wyświetlanego tekstu.
- [x] **0.7** Reguły Firestore wdrożone: `usersPublic` (read dla zalogowanych,
      create/update tylko właściciel), `friendships` po `members`.
- [x] **0.8** BottomNav: etykiety → `strings.xml` (klucze `nav_*` już istniały
      w 6 językach, tylko ich nie używano). OCR **usunięte** z `showBottomNav`
      — 5 ikon jest kompletnych, zostaje link z Translatora + systemowy back.
- [ ] **0.9** **Test na telefonie (Milosz):** dodać znajomego z dwóch kont,
      sprawdzić że obie strony go widzą; wyszukać po nicku i po e-mailu.
- [x] **0.10** README + commit + push.

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
- [x] **1.13** Karta kontaktu (`users/{uid}/contacts/{otherUid}`): alias,
      język tłumaczenia (ręczne nadpisanie profilu), wycisz, zablokuj, przypnij,
      notatka, usuń rozmowę (ukrycie lokalne). Ekran `contact/{uid}`,
      wejście z nagłówka wątku i z Kontaktów. **Zostaje:** auto-wykrywanie
      języka z rozmowy (oryginalny pomysł) — wymagałoby licznika języków
      per wątek i progu pewności; ręczny wybór daje użytkownikowi kontrolę
      i jest przewidywalny. Do przemyślenia w fazie 2.
- [ ] **1.16** Test na telefonie (karta kontaktu): alias widoczny w skrzynce
      i w nagłówku, przypięcie sortuje, blokada ukrywa, zmiana języka
      przetłumaczenia w locie.
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

- [x] **3.0** **Warunek wstępny: polityka prywatności opublikowana** (§12) —
      zrobione 2026-09-03.
- [x] **3.1** `READ_CONTACTS` + ekran wyjaśnienia — **zrobione**.
      `ContactsPermissionScreen.kt` (prominent disclosure + link do polityki +
      e-mail `privacy@verbigem.com`) pokazywany **przed** systemowym dialogiem,
      uprawnienie dopisane do manifestu, `PhoneContactsImporter.hasPermission()`
      pilnuje, żeby nie prosić drugi raz.
- [x] **3.2** `PhoneContactsImporter` (ContactsContract) — **wersja podstawowa**:
      imię + numer, deduplikacja po numerze, odczyt na `Dispatchers.IO`.
      **Zostaje do dorobienia:** e-maile, miniatura, `starred`, zapis do tabeli
      Room `external_contacts` i normalizacja E.164 (libphonenumber, task 3.3).
- [ ] **3.3** libphonenumber → normalizacja E.164.
- [ ] **3.4** Matching przez `matchContacts` → trzy stany kontaktu z §4.1.
- [~] **3.5** Zapraszanie: **systemowy share sheet już działa** (`Context.shareText`
      + `InviteLinks.forUser(uid)` → link `https://mini.verbigem.com/app?inv=<uid>`).
      **Zostaje:** bezpośrednie kanały SMS z prefillem (`smsto:`), `wa.me` i e-mail
      — czyli interfejs `OutboundChannel` z §5.4.
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
| `usersPublic/{uid}` | read dla zalogowanych; **create/update tylko właściciel**, blokada zapisu `discoverableByPhone` (pole funkcji) | 0 ✅ wdrożone |
| `friendships/{id}` | zapytania po `members`; blokada zmiany `members`/`uidA`/`uidB` przy update | 0 ✅ wdrożone |
| `chats/{chatId}/messages` | **złagodzić `update: if false`** — odbiorca może zmienić tylko własny klucz w `readBy` | 1 |
| `chats/{chatId}` | update tylko dla członków, tylko `lastMessage*`/`readState.<uid>`/`typing.<uid>` | 1 |
| `users/{uid}/contacts/{otherUid}` | read/write tylko właściciel + whitelist 7 pól (`alias`, `langOverride`, `muted`, `pinned`, `blocked`, `note`, `updatedAt`) | 1 ✅ wdrożone |
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
| ~~**Plan Blaze**~~ | ~~Cloud Functions nie działają na Sparku~~ | **ROZWIĄZANE — Milosz ma Blaze** (2026-09-03). Pozostaje pilnować limitów i App Check. |
| **Polityka Google Play — READ_CONTACTS** | Wymaga prominent disclosure w aplikacji + opublikowanej polityki prywatności. Brak = odrzucenie wniosku | **§12 — politykę generujemy sami**, 6 języków, hosting na `mini.verbigem.com/privacy/`. Ekran wyjaśnienia przed dialogiem uprawnień |
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

1. ~~**URL polityki prywatności**~~ — **ROZSTRZYGNIĘTE (2026-09-03):** nie istniał,
   wygenerujemy go sami. Zob. §12.
2. **Czy import kontaktów ma być jednorazowy, czy ciągły?** (opcjonalny okresowy
   re-match w tle, np. raz na tydzień, żeby wykryć znajomych którzy dołączyli).
3. **Czy kontakty zewnętrzne synchronizować z chmurą?** Na razie zakładam, że
   zostają lokalne (prywatność) — ale wtedy znikają po zmianie telefonu.
4. **Kto płaci za tłumaczenie w chmurze** w fallbacku dla użytkowników bez modelu?
   Czy `senderTranslation` jest darmowe (nadawca używa swojego modelu), czy Pro?
5. **Retencja wiadomości** — czy kasować stare wiadomości z Firestore po X dniach
   (koszty vs. historia)?

---

## 12. Polityka prywatności — OPUBLIKOWANA (2026-09-03)

**URL:** `https://mini.verbigem.com/privacy/` (+ `/pl/`, `/en/`, `/de/`, `/es/`,
`/zh/`, `/tr/`). **Kontakt: `privacy@verbigem.com`.**

Wymagana przez Google Play (zwłaszcza przy `READ_CONTACTS` — bez niej wniosek o
dostęp do kontaktów zostanie odrzucony). **Zrobiliśmy ją sami** — treść jest
prosta, bo aplikacja jest zaprojektowana prywatnie: tłumaczenie dzieje się na
urządzeniu. Faza 3 jest odblokowana.

**Struktura treści (13 sekcji, identyczna w każdym języku):** kto odpowiada za
dane → dane tylko na urządzeniu → dane w chmurze → wiadomości czatu → numery
telefonów i kontakty → uprawnienia → podmioty trzecie (tabela: Firebase /
OpenRouter / DeepSeek / Hugging Face) → retencja → prawa użytkownika →
bezpieczeństwo → dzieci (13+/16+ w EOG) → zmiany polityki → kontakt. Na górze
jest ramka „Najważniejsze w skrócie" z 5 punktami, w tym adresem e-mail.

### 12.1 Zakres treści (do napisania)

- **Dane na urządzeniu, nie u nas:** tłumaczenia (model Hy-MT2), historia
  tłumaczeń i OCR, wyniki rozpoznawania mowy — przetwarzane lokalnie.
- **Dane w chmurze (Firebase/Google):** konto (nick, e-mail), treść wiadomości
  czatu (musi być przechowana, żeby dostarczyć ją odbiorcy), historia
  synchronizowana między urządzeniami, tokeny powiadomień.
- **Numery telefonów:** do chmury trafia **wyłącznie skrót kryptograficzny**
  (SHA-256 + HMAC z kluczem serwerowym), nigdy numer w postaci jawnej. Numery
  kontaktów nie są przechowywane na serwerze.
- **Podmioty trzecie:** Google Firebase (Auth, Firestore, Storage, FCM),
  OpenRouter (TTS Pro — tylko dla płacących, tylko tekst do syntezy),
  DeepSeek (silnik online — tylko gdy użytkownik go wybierze).
- **Prawa użytkownika:** usunięcie konta i danych, kontakt (adres e-mail
  do ustalenia — patrz pytanie niżej).
- **Dzieci / wiek:** standardowa klauzula 13+/16+.

### 12.2 Jak to jest wdrożone

- **Generator:** `verbigem/mini/scripts/build_privacy.py` — jeden plik z treścią
  w 6 językach i arkuszem stylów, zero zależności, zero bundlera. Wypluwa
  statyczne HTML do `public/privacy/` **i** `dist/privacy/`:
  ```bash
  cd verbigem/mini && python scripts/build_privacy.py
  cd verbigem/mini && firebase deploy --only hosting --project mini-verbigem
  ```
  Zmiana treści = edycja skryptu, wygenerowanie, deploy. **Bez wydania APK.**
- **Format:** 6 katalogów `/privacy/<lang>/index.html` + `/privacy/index.html`
  (pełna treść EN + przełącznik języków + skrypt przekierowujący wg
  `navigator.languages`, raz na sesję). Bez JS strona i tak pokazuje politykę —
  ważne dla crawlera Google Play.
- **Dlaczego katalogi, nie pliki `pl.html`:** `firebase.json` ma catch-all
  rewrite `** → /index.html` (SPA webappy). Pliki statyczne mają pierwszeństwo,
  ale tylko przy dokładnym trafieniu — `/privacy/pl/` działa zawsze,
  `/privacy/pl` dostaje 301 na wersję ze slashem (sprawdzone).
- **⚠️ Pułapka hostingu (znana z README):** `firebase deploy --only hosting`
  **zastępuje cały hosting zawartością `dist/`**. Dlatego pliki są w obu
  miejscach: `dist/` (leci na serwer teraz) i `public/` (przetrwa przyszły
  `npm run build`, bo Vite kopiuje `public/` → `dist/`).
- **⚠️ Pułapka cache:** w `firebase.json` dodano `/privacy/**` →
  `public, max-age=3600, must-revalidate`. **Nie wolno** dać tam `immutable` —
  Cloudflare zamroziłby politykę na rok (ta sama pułapka co `/android/**`).
  Z tego powodu **styl jest inlinowany** do każdego pliku, zamiast leżeć w
  osobnym `.css` (reguła `**/*.css` ma `immutable`).
- **Link w aplikacji:** karta „Polityka prywatności" w `ProfileScreen`
  (otwiera `/privacy/<uiLang>/`) oraz ekran `ContactsPermissionScreen`
  przed dialogiem `READ_CONTACTS`. URL-e buduje `data/AppLinks.kt` — jedno
  źródło prawdy. Stringi × 6 (`privacy_*`, `contacts_perm_*`).
- **Kontakt:** `privacy@verbigem.com` — ten sam adres w polityce, w ekranie
  disclosure i w zgłoszeniu do Play.

### 12.3 Zadania

- [x] **12.1** Adres kontaktowy: **`privacy@verbigem.com`** (ustalił Milosz,
      2026-09-03).
- [x] **12.2** Treść polityki — 13 sekcji + ramka „w skrócie", PL i EN jako
      wzorce.
- [x] **12.3** Tłumaczenia DE / ES / ZH / TR.
- [x] **12.4** 6 plików HTML + `index.html`, wspólny styl inlinowany,
      jasny/ciemny motyw pod `prefers-color-scheme`.
- [x] **12.5** Wrzucone do `mini/dist/privacy/` **i** `mini/public/privacy/`,
      wdrożone, wszystkie 7 URLi sprawdzone (HTTP 200, poprawne tytuły).
      Deploy zgłosił `uploading new files [0/7]` — webapp nietknięta.
- [x] **12.6** Pozycja „Polityka prywatności" w `ProfileScreen` + stringi × 6.
- [x] **12.7** README: sekcja „🔒 Polityka prywatności (opublikowana)" +
      pułapki `dist/` vs `public/` i cache.

**Kiedy:** zrobione 2026-09-03 jako osobny, lekki tor. Faza 3 odblokowana.
