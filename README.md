# Verbigem Android — Natywny Tłumacz Hy-MT2 (100% Kotlin + NDK)

Natywna aplikacja na system Android stworzona w **100% w języku Kotlin** z wykorzystaniem **Jetpack Compose** oraz dedykowanego, natywnego silnika wnioskowania **Hy-MT2-1.8B** (Tencent Hunyuan) w formacie **GGUF** przez mostek **C++/JNI (llama.cpp NDK)** z akceleracją sprzętową ARM NEON oraz Vulkan GPU.

Wzorowana na architekturze i funkcjach `mini.verbigem.com` (`verbigem/mini`).

---

## 🌟 Kluczowe funkcje

1. **Translator (Ekran główny)**:
   - Tłumaczenie pomiędzy 6 językami: **Polski (PL)**, **Angielski (EN)**, **Hiszpański (ES)**, **Chiński (ZH)**, **Niemiecki (DE)**, **Turecki (TR)**.
   - Wybór silników:
     - ⚡ **Hy-MT2 Szybki** (TQ1.25 / 1.25Bit ~440 MB) — najlżejszy, działa na słabszych telefonach,
     - 🎯 **Hy-MT2 Dokładny** (Q4_K_M ~1.1 GB) — bezkompromisowa jakość WMT,
     - ⚖️ **Oba (porównaj)** — jednoczesne generowanie obu wersji,
     - ☁️ **API online** — chmurowy fallback DeepSeek z portfelem.
   - **Push-to-talk (🎤):** przycisk mikrofonu po lewej stronie od przycisku OCR. Trzymaj → nagrywa
     mowę w języku źródłowym (SpeechRecognizer STT z podglądem na żywo), puszcz → kończy nagrywać
     i konwertuje na tekst. Nowo rozpoznany tekst **dodaje się** do istniejącego tekstu w polu
     (append, nie overwrite). Placeholder pola informuje: „Wpisz lub zamień mowę albo zdjęcie na tekst
     do przetłumaczenia”. W trakcie nagrywania mikrofon jest czerwony z podglądem tekstu na żywo.
   - **Czytaj Pro (💎):** płatne czytanie przez **OpenRouter TTS** (konfigurowane w
     `app_config/tts` na Firestore — domyślny model `google/gemini-3.1-flash-tts-preview`,
     osobny model dla chińskiego `fish-audio/s2.1-pro`). Klucz + modele synchronizowane
     z bazy lokalnej (Room) — zarządzane przez webapp admina (planowane).
   - **Skasuj:** czyszczenie wpisanego tekstu i wyniku tłumaczenia.
   - Automatyczny in-app model downloader z Hugging Face.
   - Historia ostatnich tłumaczeń w lokalnej bazie **Room Database (SQLite)** —
     każdy wiersz ma akcje: kopiuj, udostępnij, czytaj (offline), czytaj pro (💎), skasuj.
   - **Startowa synchronizacja z Firestore** (zasada „nowsze wygrywa" / last-write-wins
     po `updatedAt`): profil użytkownika, historia tłumaczeń i konfiguracja TTS Pro.
   - **Auto-aktualizacja APK (Firebase Hosting):** Plik `dist/android/version.json` w projekcie
     webapp (`verbigem/mini`) wdeployowany na Firebase Hosting pod `https://mini.verbigem.com/android/version.json`
     trzyma najnowszy `versionCode` + `versionName` + `apkUrl` + `updatedAt`. App (`UpdateManager.kt`)
     pobiera ten plik przy starcie, porównuje `versionCode` z zainstalowaną wersją
     i — gdy plik na Hostingu ma wyższy `versionCode` — pyta o zgodę, pobiera APK z
     `apkUrl` (też `mini.verbigem.com/android/`) i instaluje. Fallback: Firestore `app_config/update`
     (gdy Hosting niedostępny). Po rejestracji w Google Play otwiera sklep zamiast pobierać APK.
     - Deploy: `cd verbigem/mini && firebase deploy --only hosting --project mini-verbigem`.
       Zmiana wersji = edycja `dist/android/version.json` + wrzucenie APK do `dist/android/` + redeploy.
     - **Domena `.com`, NIE `.web.app`** — ROM Xiaomi (Private DNS) nie resolwi `*.web.app` w app.
     - GitHub (repo, raw, API) NIE jest już używane do update'ów.

2. **Rozmowa lokalna (Conversation)**:
   - Dwie osoby dzielą jeden telefon (Strona A / Strona B).
   - **Klawiatura nie zasłania karty wpisywania tekstu** — w `ConversationScreen` kolejność
     modyfikatorów to `imePadding()` **PRZED** `verticalScroll()`:
     ```kotlin
     Modifier.fillMaxSize().imePadding().padding(16.dp).verticalScroll(scrollState)
     ```
     Użyte PO (`==` wewnątrz) `verticalScroll()` dokłada tylko pusty margines na końcu
     treści, a **viewport zostaje pełnej wysokości** — wtedy `bringIntoViewRequester`
     "przewija pole do widoku" = na sam dół ekranu, czyli dokładnie pod klawiaturę
     (objaw: karta edycji schowana za klawiaturą). Odwrócona kolejność zwęża viewport o
     wysokość IME, więc "do widoku" znaczy "nad klawiaturą".
     Druga połowa naprawy: klawiatura wjeżdża **~250 ms po** tym jak pole dostaje focus,
     więc sam `onFocusEvent` nie wystarcza — `LaunchedEffect(WindowInsets.isImeVisible)`
     z `delay(250)` odpala `bringIntoView()` ponownie, gdy IME jest już na ekranie.
     (Manifest ma `android:windowSoftInputMode="adjustResize"`, a `MainActivity` woła
     `enableEdgeToEdge()` — bez tego `imePadding()` jest zerem.)
   - Natywne rozpoznawanie mowy **SpeechRecognizer (STT)** z podglądem na żywo.
   - Po skończeniu mowy: natychmiastowe tłumaczenie modelem **Hy-MT2-1.8B** i automatyczny odczyt głosem **TextToSpeech (TTS)** dla drugiej osoby.
   - Automatyczne przełączanie aktywnej strony rozmowy.

3. **Czat zdalny 1:1 (Chat)**:
   - Komunikator czasu rzeczywistego (Firebase Firestore).
   - **Skrzynka odbiorcza (`ChatListScreen`)** — lista konwersacji: avatar, nick,
     podgląd ostatniej wiadomości, godzina, kropka nieprzeczytanych. Sortowanie po
     `lastMessageAt` dzieje się **w aplikacji**, nie w Firestore (`whereArrayContains`
     + `orderBy` na innym polu wymagałoby indeksu złożonego, wdrażanego osobno).
   - **Wątek (`ChatThreadScreen`)** — trasa `chat/{uid}`, bańki, dzielniki dni,
     wskaźniki ✓ (wysłane) / ✓✓ (przeczytane), „pisze…", menu po długim naciśnięciu
     (kopiuj / czytaj / czytaj Pro / pokaż oryginał / cytuj / usuń u mnie).
   - **Tłumaczenie dzieje się u ODBIORCY (decyzja D1):** nadawca wysyła **oryginał**
     w `sourceLang` + własne tłumaczenie jako podpowiedź (`senderTranslation` —
     fallback dla odbiorców bez pobranego modelu). Odbiorca tłumaczy lokalnie
     Hy-MT2 na swój `speakLangSource` i **zapisuje wynik w Room** (`chat_translations`),
     żeby nie mielić modelu przy każdej rekompozycji. Przełącznik „pokaż oryginał"
     na każdej bańce + „Przetłumacz" ponownie po zmianie języka.
   - **Kolejka offline (`chat_outbox`):** kliknięcie Wyślij jest natychmiastowe —
     wiersz ląduje w Room, bańka pokazuje „wysyłanie", a `ConnectivityObserver`
     wyzwala wysyłkę. Id dokumentu w Firestore = `clientMsgId` (UUID wygenerowane
     przed wywołaniem sieci), więc ponowienie po zerwanym połączeniu **nie tworzy
     duplikatu** (`set()` na tym samym id jest no-op).
   - **Potwierdzenia odczytu i „pisze…" żyją w subkolekcjach**
     `chats/{chatId}/readReceipts/{uid}` i `chats/{chatId}/typing/{uid}` — jeden
     dokument na uczestnika. Reguła sprowadza się do „możesz pisać tylko swój
     dokument", dzięki czemu **nie trzeba** łagodzić `update: if false` na
     `messages` (wiadomości pozostają append-only). Kropka nieprzeczytanych w
     skrzynce liczy się z lokalnej tabeli `chat_reads` (zero odczytów z chmury).
   - Paginacja: live listener tylko na 50 najnowszych wiadomości, starsze strony
     doczytywane `startAfter` przy dojechaniu do góry listy.
   - Podgląd oryginału i odsłuch audio (TTS offline oraz Czytaj Pro 💎).
   - **Wyszukiwanie ludzi idzie przez `usersPublic/{uid}`** — `users/{uid}` jest czytelne
     tylko dla właściciela, więc odpytywanie go z innego konta zawsze kończyło się
     `PERMISSION_DENIED`. `AuthRepository` utrzymuje publiczną wizytówkę (nick, e-mail,
     avatar, języki + zlowercasowane `searchNick`/`searchEmail`), a `ensureProfile()`
     odnawia ją przy każdym logowaniu (samoleczący backfill).
   - **Znajomości są symetryczne:** dokument `friendships` ma `members: [uidA, uidB]`
     i zapytanie idzie `whereArrayContains("members", uid)`. Jeden listener w
     `ChatRepository` zasila trzy strumienie: `watchAccepted` / `watchIncoming` /
     `watchOutgoing`. (Dawniej filtr był na samo `uidA`, więc osoba o uid sortującym się
     druga nie widziała znajomego wcale.)
   - **Język z profilu, nie na sztywno.** Nadawca pisze w swoim `speakLangSource`,
     a podpowiedź tłumaczy na `speakLangSource` rozmówcy pobrany z `usersPublic`.
     Odsłuch (`speak()`) czyta w języku aktualnie wyświetlanego tekstu.
     Docelowo właściwe tłumaczenie dzieje się u odbiorcy — patrz `chat_kontakty.md`.
     **Język docelowy tłumaczenia** to `ChatThreadViewModel.translationLang` =
     `langOverride` z karty kontaktu (jeśli ustawiony) albo `speakLangSource` z profilu.
     Zmiana któregokolwiek czyści mapę tłumaczeń w pamięci i wraca do auto-tłumaczenia,
     ale **nie rusza cache w Room** (kluczowany językiem, więc stare tłumaczenia żyją).
   - **`chats/{chatId}` musi istnieć i mieć `members` ZANIM poleci wiadomość** — reguły
     bezpieczeństwa dla `messages` robią `get(/chats/$(chatId)).data.members`. Dlatego
     `sendMessage()` najpierw upsertuje dokument czatu, potem dodaje wiadomość.
     Bez tego wysłanie czegokolwiek było niemożliwe.
   - **Karta kontaktu (`contact/{uid}`)** — ustawienia per rozmówca, prywatne dla
     mnie: alias (nazwa widoczna tylko u mnie), język tłumaczenia, przypięcie,
     wyciszenie, blokada, notatka. Wchodzi się z nagłówka wątku i z ikony ⓘ w
     Kontaktach. Szczegóły w `chat_kontakty.md` (zadanie 1.13).
     - Dane leżą w **`users/{uid}/contacts/{otherUid}`** — pod MOIM dokumentem, nie
       w znajomości, bo to moje zdanie o kimś: nadany przeze mnie alias nie może
       pojawić się na cudzym telefonie. Reguła: odczyt/zapis tylko właściciel +
       whitelist 7 pól.
     - **Firestore, nie Room** — Firestore trzyma własny cache offline na Androidzie,
       więc karta działa bez sieci, a ustawienia idą za kontem na drugie urządzenie
       bez wymyślania nowego mechanizmu syncu.
     - **`langOverride` = język, NA KTÓRY tłumaczone są przychodzące wiadomości.**
       Puste = język z mojego profilu. Celowo NIE zmienia `sourceLang` wiadomości
       wychodzących: to pole musi opisywać to, co faktycznie napisałem, bo inaczej
       odbiorca tłumaczyłby z błędnego języka źródłowego.
     - **„Zablokuj" i „wycisz" są dziś lokalne.** Blokada ukrywa rozmowę w mojej
       skrzynce (nie da się jej egzekwować serwerowo bez Cloud Functions — faza 2),
       wyciszenie gasi kropkę nieprzeczytanych (nie ma jeszcze pushy do wyciszenia).
       Komunikaty w UI mówią to wprost, zamiast obiecywać więcej.
     - **„Usuń rozmowę" = ukrycie lokalne.** Wiadomości w Firestore są append-only
       i nie ma funkcji, która by je sprzątała, więc tabela `chat_hidden` w Room
       (migracja v6→v7) chowa rozmowę u mnie; rozmówca zachowuje wątek. Karta daje
       „Przywróć rozmowę".

4. **Kontakty i Znajomi (Contacts)**:
   - Wyszukiwanie użytkowników po nicku/e-mailu.
   - Przyjmowanie i odrzucanie zaproszeń.
   - **Znajomi z książki telefonicznej:** odczyt kontaktów przez
     `ContactsContract` (`PhoneContactsImporter`) — imiona i numery, deduplikacja,
     odczyt na `Dispatchers.IO`. Dane **zostają na urządzeniu**.
   - **Prominent disclosure:** `ContactsPermissionScreen` wyjaśnia, co i po co
     czytamy, zanim system zapyta o `READ_CONTACTS` (wymóg Google Play). Ma link
     do polityki prywatności i adres `privacy@verbigem.com`.
   - **Zapraszanie:** systemowy share sheet z linkiem
     `https://mini.verbigem.com/app?inv=<uid>` (działa z SMS, WhatsApp,
     Telegramem, e-mailem — bez żadnej integracji i bez `SEND_SMS`).

5. **OCR ze zdjęcia/aparatu (Camera OCR)**:
   - Zdjęcie z aparatu lub wybór z galerii.
   - **Aparat = PEŁNA ROZDZIELCZOŚĆ (TakePicture + FileProvider):** kamera używa
     `ActivityResultContracts.TakePicture()` zapisującego pełnowymiarowe JPEG do tymczasowego
     pliku w `cacheDir/ocr_camera/` przez `FileProvider` (`${packageName}.fileprovider`, ścieżka
     `cache-path path="."` w `res/xml/file_paths.xml`), a potem dekodowanego przez ten sam
     `processImageUri` co galeria — więc OCR ma identyczną jakość co zdjęcie z galerii.
     **NIE używamy `TakePicturePreview()`** — zwraca on zdownskalowany thumbnail (~160px),
     co psuło czytelność OCR (tekst rozmyty). Cancel aparatu = `ocr_camera_cancelled`.
   - `loadBitmap(uri)` w `OcrViewModel` czyta teraz **EXIF orientation** i obraca bitmapę do
     pionu (aparat zapisuje zdjęcia obrócone; galeria systemowa auto-obraca, `BitmapFactory`
     nie) — inaczej crop i OCR byłyby przekręcone dla zdjęć z aparatu.
   - Błyskawiczne offline OCR przez **Google ML Kit Text Recognition** + natywne tłumaczenie tekstu silnikiem Hy-MT2.
   - **Wybór obszaru do OCR (crop):** przed odczytem użytkownik zaznacza prostokątny obszar
     na zdjęciu — OCR czyta tylko to, co jest wewnątrz ramki.
     - Ramka to **ręcznie napisany overlay** (`com.verbigem.app.ui.screens.ocr.CropOverlay.kt`),
       NIE zewnętrzna biblioteka. Gotowe biblioteki do przycinania (np. vendored `ImageCropView`)
       nie działały poprawnie w tym układzie (ramka się nie ustawiała, a strona się nie scrollowała)
       — `cropview/` zostało usunięte.
     - Implementacja: **pojedynczy `Canvas`** wypełniający obszar zdjęcia + **jeden**
       `pointerInput(Unit)` (stały klucz, więc handler nie jest rekreowany w trakcie dragu).
     - Rozciąganie **4 rogów** (hit-radius 34dp): łapiesz róg, a **przeciwny róg pozostaje
       nieruchomy** przez cały drag (`fixedLeft/fixedTop` zablokowane od `onDragStart`).
     - **Scroll strony:** `detectDragGestures` przejmuje każdy gest i blokuje `verticalScroll`
       rodzica — dlatego użyto `awaitEachGesture`: jeśli dotyk NIE trafił uchwytu, gest jest
       **niekonsumowany** i idzie do kolumny (`verticalScroll`), więc strona się przewija
       (kluczowe, gdy zdjęcie jest wyższe niż ekran i dolny uchwyt jest pod zagięciem).
     - Współrzędne ramki są **znormalizowane 0f..1f** (względem zdjęcia) i trzymane w
       `OcrViewModel.cropRectFlow` — przycinanie bitmapy (`cropBitmap`) jest niezależne
       od skali podglądu. Przycisk „Odczytaj zaznaczony obszar" wywołuje `runOcrFromCrop()`
       (przycina oryginał do zaznaczonego obszaru i puszcza ML Kit).
     - Ramka domyślna: 0.1–0.9 (prawie całe zdjęcie); użytkownik zacieśnia do tekstu.
   - **UWAGA:** zachowanie gestów (łapanie rogów + scroll) weryfikuje się TYLKO na telefonie
     po zainstalowaniu APK — build to nie to samo co działający UX.
   - **OCR Pro (💎):** obok przycisków Aparat/Galeria widoczny jest przycisk **OCR Pro** (ikona
     aparatu). Dla free-userów jest wyszarzony i po kliknięciu pokazuje tooltip
     "OCR Pro wkrótce" (`ocr_pro_coming_soon`, 6 języków) — strona Pro powstaje później.
     Komponent: `ProFeatureButton` (współdzielony z głośnikiem Pro).
   - **Streaming tłumaczenia OCR:** tłumaczenie OCR działa **strumieniowo wyrazami**
     (tak samo jak w Translatorze) — `OcrViewModel.translateText()` woła
     `hyMt2Engine.translateSegmented(..., onPartial = { ... })` i wypisuje `_translatedText`
     przyrostowo. Zob. niżej "Tłumaczenie segmentami" — to samo dotyczy Translatora i OCR.
   - **Historia OCR (osobna, zsynchronizowana):** OCR ma **własną, niezależną historię**,
     siedzi w osobnej tabeli Room `ocr_history` (encja `OcrHistoryEntity`, DAO `OcrHistoryDao`,
     repo `OcrHistoryRepository`) i synchronizuje się z **własną kolekcją Firestore `ocr_history`**
     (subkolekcja `users/{uid}/ocr_history/{syncId}`) — tożsame zasady last-write-wins + tombstone
     co w Translatorze, ale listy się nigdy nie mieszają (osobna kolekcja, osobna tabela).
     `SyncManager.syncCollection(...)` jest sparametryzowany kolekcją i wywoływany dla
     `"history"` (Translator) i `"ocr_history"` (OCR). Usunięcie z historii OCR to fizyczny
     lokalny delete + tombstone (`PendingDeleteEntity` z `collection = "ocr_history"`) pchany do
     Firestore przy najbliższym syncu. Reguły Firestore mają `match /users/{uid}/ocr_history/{syncId}`.
   - **Karty historii OCR z głośnikiem Pro:** karty `OcrHistoryItem` mają pełen zestaw akcji
     jak w Translatorze — kopiuj, udostępnij, **czytaj (offline)**, **czytaj Pro (💎)**, skasuj.
     Przycisk „czytaj Pro" działa dla Pro (`speakPro` → OpenRouter TTS), a dla free-userów jest
     szary + tooltip (`pro_speaker_tooltip`), tak samo jak w `HistoryCard`.
   - **OCR NIE ma pozycji w BottomNav** (celowo): pasek ma 5 kompletnych ikon i nie ma
     slotu na OCR. Poprzednio `Screen.Ocr.route` figurował w `showBottomNav`, przez co na
     ekranie OCR pokazywał się pasek, który nie potrafił ani zaznaczyć OCR, ani do niego
     wrócić. OCR wchodzi się z Translatora (przycisk „From photo (OCR)"), wychodzi systemowym
     backiem. Jeśli kiedyś OCR ma trafić do nawigacji, to jako 6. pozycja w `BottomNav.items`,
     nie przez sam `showBottomNav`.

6. **Profil i Design System**:
   - Motywy: **Calm 🌊**, **Sharp ⚡**, **Playful 🎨**.
   - Tryby: **Dzień (Day ☀️)** / **Noc (Night 🌙)**.
   - Wybór języka interfejsu i domyślnej pary językowej.
   - Wektorowe flagi SVG dla wszystkich języków.
   - **Polityka prywatności** — karta otwierająca `mini.verbigem.com/privacy/`
     w przeglądarce, w języku interfejsu (szczegóły w sekcji 🔒 niżej).

---

## 📋 Plan budowy: Czat i Kontakty

Rozbudowa kart **Czat** i **Kontakty** jest rozpisana w osobnym, żywym dokumencie:
**`chat_kontakty.md`** (katalog główny repo). Zawiera diagnozę obecnego stanu,
macierz możliwości technicznych (co da się zrobić z importem kontaktów z WhatsApp
/ Telegrama, a co nie), architekturę docelową, plan w fazach 0–5 oraz listę
ryzyk. **Prace nad czatem i kontaktami zaczynamy od lektury tamtego pliku**, a
każdą sesję kończymy aktualizacją jego tabeli „Postęp".

Najważniejsze ustalenia (szczegóły w `chat_kontakty.md`):

- **Tłumaczenie wiadomości dzieje się u ODBIORCY** — nadawca wysyła oryginał +
  `sourceLang` + własne tłumaczenie jako podpowiedź (fallback dla odbiorców bez
  pobranego modelu). **Zrealizowane w fazie 1** (cache w `chat_translations`).
- **Faza 1 (skrzynka + wątek) jest w kodzie gotowa** — bez backendu, bez Cloud
  Functions. **Wydana jako v26** (na produkcji; test na telefonie u Milosza).
  Szczegóły w `chat_kontakty.md`.
- **Karta kontaktu (1.13) jest w kodzie gotowa** — alias, język tłumaczenia per
  kontakt, przypnij / wycisz / zablokuj, notatka, usuń rozmowę. Czeka na wydanie.
  **Blokada i wyciszenie są na razie lokalne** (brak Cloud Functions), a
  „usuń rozmowę" to ukrycie lokalne (wiadomości w Firestore są append-only).
- **Nie da się zaimportować listy kontaktów z WhatsApp ani Telegrama** (brak API).
  Źródłem listy jest **książka telefoniczna + opcjonalnie plik `.vcf`**, a WhatsApp
  / SMS / e-mail są **kanałami dostarczenia** na wyjściu.
- **Weryfikacja numeru telefonu jest leniwa** — przy pierwszym wejściu w Czat lub
  Kontakty, nie przy rejestracji. Umożliwia matching kontaktów po zahaszowanym numerze.
- Cloud Functions (matching + FCM) wchodzą w fazie 2 i **wymagają planu Blaze**.

---

## 🔒 Polityka prywatności (opublikowana)

**URL do zgłoszenia w Google Play:** `https://mini.verbigem.com/privacy/`
Wersje językowe: `/privacy/pl/`, `/en/`, `/de/`, `/es/`, `/zh/`, `/tr/`.
Adres kontaktowy do spraw prywatności: **privacy@verbigem.com**.

**Źródło treści:** `verbigem/mini/scripts/build_privacy.py` — jeden plik z całą
treścią w 6 językach i arkuszem stylów. To jedyne źródło prawdy; skrypt
generuje statyczne HTML do `public/privacy/` **i** `dist/privacy/`.

```bash
cd verbigem/mini && python scripts/build_privacy.py
cd verbigem/mini && firebase deploy --only hosting --project mini-verbigem
```

**Dlaczego `public/` i `dist/` naraz?** `firebase deploy --only hosting`
zastępuje hosting zawartością `dist/`. W `dist/` pliki muszą być, bo to ono
leci na serwer. W `public/` muszą być, żeby przetrwały przyszły
`npm run build` Vite (Vite kopiuje `public/` → `dist/`, ale najpierw `dist/`
czyści — patrz sekcja o APK niżej).

**Struktura URL-i:** katalogi (`/privacy/pl/index.html`), **nie** płaskie pliki
`/privacy/pl.html`. `firebase.json` ma catch-all rewrite `** → /index.html`
(webapp SPA), więc gdyby pliku nie było, `/privacy/pl` dostałoby stronę
webappy. Ze slashem serwowany jest prawdziwy plik statyczny, a `/privacy/pl`
dostaje 301 → `/privacy/pl/`.

**`/privacy/index.html`** = pełna treść po angielsku (crawler Google Play i
przeglądarki bez JS zawsze widzą politykę) + przełącznik języków + mały skrypt
przekierowujący wg `navigator.languages` (raz na sesję, `sessionStorage`).

**⚠️ Cache:** `firebase.json` daje `/privacy/**` nagłówek
`public, max-age=3600, must-revalidate`. **Nigdy nie dawaj tam `immutable`** —
Cloudflare zamroziłby politykę na rok (ta sama pułapka co przy `/android/**`).
Styl jest **inlinowany** do każdego pliku właśnie dlatego, że reguła
`**/*.css` ma `immutable`.

### Gdzie polityka jest w aplikacji

| Miejsce | Plik | Co robi |
|---|---|---|
| Profil → karta „Polityka prywatności" | `ProfileScreen.kt` | otwiera `/privacy/<uiLang>/` w przeglądarce |
| Kontakty → prominent disclosure | `ContactsPermissionScreen.kt` | ekran wyjaśnienia **przed** systemowym dialogiem `READ_CONTACTS` (wymóg Play) |

URL-e buduje **`data/AppLinks.kt`** (jedno źródło prawdy):
`privacyPolicy(uiLang)` w profilu (preferencja użytkownika),
`privacyPolicyFor(context)` tam, gdzie preferencji nie mamy (czyta faktyczny
język zasobów). Oba otwierają **przeglądarkę**, nie WebView — użytkownik widzi
pasek adresu i naszą domenę.

**Zasada spójności:** treść ekranu disclosure (stringi `contacts_perm_*` × 6
języków) musi zgadzać się z opublikowaną polityką. Zmiana polityki na stronie
**nie wymaga** nowego wydania APK; zmiana stringów w aplikacji — tak.

**`READ_CONTACTS`** jest w manifeście, ale aplikacja prosi o nie wyłącznie po
ekranie wyjaśnienia. Numery nie opuszczają urządzenia — do chmury (faza 2/3,
Cloud Function `matchContacts`) mają iść wyłącznie skróty SHA-256 + HMAC.

---

## 🛠️ Architektura techniczna

```
app/src/main/
│   ├── cpp/
│   │   ├── CMakeLists.txt              # Kompilacja biblioteki współdzielonej libverbigem_llama.so
│   │   ├── llama_jni.cpp               # Mostek JNI C++ do wnioskowania GGUF
│   │   └── llama.cpp/                  # Vendored llama.cpp (gitignored), branch STQ_0 (PR #22836 STQ1_0 kernel)
│   │       └── ggml/src/ggml-cpu/llamafile/sgemm.cpp  # fp16→fp32 fallback dla NDK 26
├── java/com/verbigem/app/
│   ├── MainActivity.kt             # Punkt wejścia i Edge-to-Edge Compose + dialog auto-update
│   ├── VerbigemApplication.kt      # Inicjalizacja Firebase + startowa synchronizacja (SyncManager) + reaktywny sync (ConnectivityObserver)
│   ├── data/
│   │   ├── ConnectivityObserver.kt  # callbackFlow na NetworkCallback — emituje isOnline (trigger sync na włączenie netu)
│   │   ├── local/                  # Room v7: HistoryEntity/Dao, OcrHistoryEntity/Dao, TtsConfigEntity/Dao, PendingDeleteEntity/Dao, ChatRoomEntities (chat_translations, chat_outbox, chat_reads, chat_deleted_messages, chat_hidden), ChatRoomDaos + DataStore Preferences
│   │   ├── model/                  # LangCode, UserProfile, PublicProfile, ChatMessage (+SenderTranslation), ChatSummary, Friendship, EngineChoice, TranslationHistory, TtsConfig
│   │   ├── AppLinks.kt             # Polityka prywatności (URL wg języka), InviteLinks, openUrl(), shareText()
│   │   ├── PhoneContactsImporter.kt # Odczyt książki telefonicznej (ContactsContract) — dane zostają na urządzeniu
│   │   └── repository/             # AuthRepository, ChatRepository, HistoryRepository, ProTtsRepository, SyncManager, TtsConfigSync
│   ├── engine/
│   │   ├── HyMt2NativeEngine.kt    # Natywny silnik Hy-MT2 z promptem  i czyszczeniem
│   │   ├── ModelDownloader.kt      # Pobieranie i cache modeli GGUF z Hugging Face
│   │   ├── SpeechManager.kt        # Natywne Android STT (SpeechRecognizer) + TTS
│   │   ├── OcrManager.kt           # Google ML Kit Text Recognition
│   │   ├── OnlineApiEngine.kt      # Chmurowy proxy DeepSeek
│   │   ├── ProTtsEngine.kt         # Płatne TTS przez OpenRouter (/audio/speech)
│   │   └── UpdateManager.kt        # Auto-update: Firebase Hosting (mini.verbigem.com) → OkHttp pobranie APK → instalacja
│   ├── jni/
│   │   └── LlamaNativeBridge.kt    # JNI deklaracje external fun
│   └── ui/
│       ├── components/             # FlagIcon, LangSelect, BottomNav, EnginePicker, DownloadDialog, AdBannerView, ProFeatureButton (Pro+grayscale+tooltip)
│       ├── navigation/             # AppNavigation, Screen
│       ├── screens/                # TranslatorScreen (+HistoryCard, ResultCard), ConversationScreen, ChatListScreen, ChatThreadScreen, ContactCardScreen, ContactsScreen (+ContactsPermissionScreen), OcrScreen (+CropOverlay), ProfileScreen, LoginScreen
│       └── theme/                  # Color, Theme, Type (Calm/Sharp/Playful × Day/Night)
```

---

## 🔤 Jak działa tłumaczenie

Tłumaczenie jest **w 100% lokalne** (offline, bez serwera) — tekst trafia do modelu
**Hy-MT2-1.8B** uruchomionego natywnie przez vendored **llama.cpp** (kernel `STQ1_0`,
PR #22836) przez mostek **C++/JNI**. Pipeline krok po kroku:

```
EditText (Compose)
   │  onInputChanged
   ▼
TranslatorViewModel.translate()
   │  uruchamia korutynę (Dispatchers.Default)
   ▼
HyMt2NativeEngine.translate(text, from, to, isAccurate, onPartial)
   │  1. ensureModelLoaded()  — ładuje .gguf z filesDir/models/ (mmap)
   │  2. buildPrompt()        — "Translate the following segment into <TARGET>,
   │                             without additional explanation：<TEXT>"
   │  3. generateNativeStreaming()  — wywołanie JNI (C++)
   ▼
llama_jni.cpp (C++)
   │  • llama_chat_apply_template("hunyuan-dense")  — wymagany szablon czatu
   │  │   (tokeny <｜hy_User｜>, <｜hy_Assistant｜> itd.)
   │  • llama_tokenize → llama_decode (pętla autoregresyjna)
   │  • po każdym tokienie: llama_token_to_piece → bufor wyrazów
   │  • gdy nowy token zaczyna się od spacji → flush poprzedniego wyrazu
   │  │   przez callback onToken(piece)  ← STREAMING WYRAZAMI
   ▼
TokenStreamCallback.onToken(piece)  (Kotlin)
   │  accumulated.append(piece); onPartial(accumulated.toString())
   ▼
TranslatorViewModel  →  _primaryResult / _secondaryResult (StateFlow)
   ▼
TranslatorScreen  —  Text() z nowym wyrazem (recompose, bez migotania)
```

**Kluczowe fakty:**

- **Model:** `Hy-MT2-1.8B-1.25Bit.gguf` (silnik Szybki, ~440 MB) lub
  `Hy-MT2-1.8B-Q4_K_M.gguf` (silnik Dokładny, ~1.1 GB). Pobierany z Hugging Face
  przez `ModelDownloader` przy pierwszym uruchomieniu.
- **STQ1_0 to kernel CPU-only** — `gpuLayers = 0` (wymuszone, bo kwant 1.25-bit
  nie ma ścieżki GPU). Akceleracja: ARM NEON + (opcjonalnie) KleidiAI.
- **Szablon czatu `hunyuan-dense` jest obowiązkowy.** Surowy prompt bez niego daje
  bełkot — model oczekuje tokenów `<|hy_User|>` / `<|hy_Assistant|>`.
- **Streaming wyrazami:** natywna pętla wysyła do UI **ukończone wyrazy**, a nie
  surowe subwordy (np. `trans`+`lat`+`ion` zostają w buforze do granicy słowa).
  Dzięki temu UI nie migocze literami, a użytkownik widzi tekst przyrostowo.

- **Tłumaczenie segmentami (cała strona tekstu):** Hy-MT2 to model **segmentowy** —
  dostaje cały akapit i tłumaczy tylko PIERWSZE zdanie, po czym daje EOS (stąd w historii
  OCR widać było urwany wynik: "Ten produkt tytoniowy zawiera substancje toksyczne:" i koniec).
  Żeby przetłumaczyć CAŁY tekst, `HyMt2NativeEngine.translateSegmented()` dzieli wejście na
  segmenty (~400 znaków) na granicach zdań (`.!?\n` — `splitIntoSegments`), a nadmiarowo
  długie zdanie łamie dalej po słowach, i tłumaczy każdy segment osobnym wywołaniem
  `translate()`, a potem składa wynik (segmenty łączone `\n\n`). Dla pojedynczego krótkiego
  zdania zachowuje się dokładnie jak `translate()` (jeden call, streaming 1:1).
  **Sanityzacja NIE ucina już do pierwszej linii** (`sanitizeTranslation` zwraca całość —
  poprzednio `split("\n")` brało tylko pierwszy wiersz, co dodatkowo obcinało wynik).
  `TranslatorViewModel` i `OcrViewModel` wołają `translateSegmented` dla silników lokalnych
  (Fast/Accurate/Both); online (DeepSeek) idzie bez zmian. UI nadal streamuje przyrostowo
  (na poziomie segmentów) przez `onPartial`.
  Szczegóły + gotowa implementacja Kotlin: skill `hy-mt2-offline-translation`
  (`references/segmented-translation.md`) — przeczytaj przed modyfikacją silnika tłumaczenia.
- **Sanityzacja:** `sanitizeTranslation()` na końcu usuwa ew. znaczniki `<think>`,
  wiodące cudzysłowy i etykiety typu "Tłumaczenie:". **NIE** ucina już do pierwszego
  akapitu (poprzednia wersja brała tylko pierwszą linię — to obcinało wielozdaniowe
  tłumaczenia; cała obsługa wielu zdań jest w `translateSegmented`).
- **Języki:** model wymaga **angielskich nazw** języków w prompcie
  (`LangCode.englishName`), nie kodów ISO.
- **Wydajność:** native lib budowany jako **Release** (`-DCMAKE_BUILD_TYPE=Release`)
  + KleidiAI; przy ~3–4 tok/s na telefonie ze słabszym ARM tekst pojawia się wyraz
  po wyrazie w akceptowalnym tempie.

---

## 💎 Czytaj Pro (płatne TTS przez OpenRouter)

Funkcja dostępna tylko dla użytkowników Pro (`UserProfile.isPro`). Zamiast darmowego
`TextToSpeech` (offline) używa chmurowego API OpenRouter (`/v1/audio/speech`).

**Widoczność dla free:** ikona głośnika Pro (💎) jest **zawsze wyświetlana** — użytkownicy free widzą
ją wyszarzoną (kolor `muted`) i po kliknięciu dostają **tooltip** ("Dostępne w wersji Pro",
string `pro_feature_tooltip` we wszystkich 6 językach) zamiast odtwarzania. To uświadamia free-userom
istnienie funkcji. Dla Pro ikona jest aktywna (kolor `accent`) i odpala `ProTtsEngine`.
Komponent współdzielony: `ui/components/ProFeatureButton.kt` (`TooltipBox` + `PlainTooltip`,
`ExperimentalMaterial3Api`) — używany też przez OCR Pro.

**Komponenty:**
- `ProTtsEngine.speak(text, lang, config)` — wysyła POST do `https://openrouter.ai/api/v1/audio/speech`
  z `model`, `input`, `voice`, `response_format=mp3`; odtwarza zwrócony mp3 przez `MediaPlayer`.
- `TtsConfig` (model domenowy) — `apiKey`, `defaultModelId`, `chineseModelId`, `defaultVoice`,
  `chineseVoice`, `updatedAt`. Metody `modelIdFor(lang)` / `voiceFor(lang)` wybierają
  osobny model dla `LangCode.ZH`.
- `ProTtsRepository` + `TtsConfigDao`/`TtsConfigEntity` — cache w Room (`tts_config`, 1 wiersz, id=1).
- `TtsConfigSync` — przy starcie pobiera `app_config/tts` z Firestore i nadpisuje lokalne
  (jeśli `remote.updatedAt >= local.updatedAt`).

**Wybrane modele (sierpień 2026, stosunek cena/jakość):**
- Domyślny (PL/EN/ES/DE/TR): `google/gemini-3.1-flash-tts-preview` (~$1/M in + $20/M out tokens, 70+ języków).
- Chiński (osobny): `fish-audio/s2.1-pro` (chiński-native, wysoka jakość ZH; jest też darmowy `fish-audio/s2.1-pro-free`).
- Odrzucone: `hexgrad/kokoro-82m` (tani, ale brak PL/TR).

**Konfiguracja:** `apiKey` wpisuje się ręcznie do Firestore (`app_config/tts`) do czasu
powstania webappu admina. App nie ma UI do wpisywania klucza (zgodnie ze specyfikacją:
"ręcznie do bazy teraz, webapp później").

---

## 🔄 Synchronizacja danych (startup sync, last-write-wins)

`SyncManager.syncNow()` uruchamiany w `VerbigemApplication.onCreate()` (korutyna IO,
`SupervisorJob`). Wykonuje się raz przy starcie (po zalogowaniu Firebase).

**Zakres (per użytkownik, `uid = FirebaseAuth.currentUser.uid`):**
1. **Profil** (`users/{uid}`) — odczyt (plan/wallet determinują `isPro`; real-time sync
   i tak idzie przez `ProfileViewModel.watchProfile`).
2. **Historia** (`users/{uid}/history/{syncId}`) — każdy wiersz ma stabilny `syncId` (UUID),
   używany jako document id. Merge:
   - Lokalne → remote: jeśli `remote` nie istnieje LUB `local.updatedAt >= remote.updatedAt`,
     `set(merge)` lokalnego wiersza.
   - Remote → local: jeśli `remote.updatedAt > local.updatedAt`, `upsertFromRemote`.
3. **TTS Pro** (`app_config/tts`) — przez `TtsConfigSync`.

**Usuwanie (tombstone, kluczowe):** lokalnie kasujemy wiersz **fizycznie** (żeby baza nie puchła),
ale zapisujemy jego `syncId` do lekkiej tabeli `pending_deletes` (tylko `syncId` + `updatedAt`).
Przy następnym syncu wysyłamy do Firestore **tombstone** = `{syncId, deleted:true, updatedAt}`
(zamiast pełnego wiersza). Inne urządzenie widzi `deleted:true` i usuwa wiersz u siebie.
Dzięki temu:
- usunięcia **propagują się** między telefonem a tabletem (nie "odżywają" przy syncu),
- działa nawet przy usunięciu **offline** — `pending_deletes` przetrwa brak netu i zostanie
  wysłane przy najbliższym syncu (zwykły sync iteruje po istniejących wierszach, więc bez
  tej kolejki offline-delete nigdy by nie wysłał tombstone'a),
- lokalna baza nie puchnie (trzymamy tylko `syncId`, nie pełny usunięty wiersz).

**Delta-sync na timestampach (kluczowe dla wydajności):** sync NIE czyta całej historii ani nie wysyła
wszystkiego za każdym razem. Każda kolekcja (`history`, `ocr_history`) ma w DataStore watermark
`lastSyncHistory` / `lastSyncOcr` (timestamp ostatniego udanego sync'u).
- **Push (lokalne→chmura):** do Firestore wysyłane są TYLKO wiersze z `updatedAt > lastSyncX`
  (repo. `getLocalSince(since)` → `WHERE updatedAt > :since`). Reszta jest pomijana — przy tysiącach
  wpisów nie wysyłamy megabajtów tekstu przy każdym syncu.
- **Pull (chmura→lokal):** Firestore zapytanie `WHERE updatedAt > lastSyncX ORDER BY updatedAt` —
  serwer zwraca TYLKO nowsze dokumenty (w tym tombstone'y, które niosą własne `updatedAt`). Nie
  pobieramy nigdy pełnej listy id.
- **Tombstone:** lokalny delete zapisuje `PendingDeleteEntity(syncId, collection, updatedAt=now)`;
  tombstone jest pchany do chmury tylko gdy `updatedAt > lastSyncX`.
- Po syncu: `lastSyncX = max(dotychczasowe, największe updatedAt z lokalnych pushed + remote pulled)`.
  Dzięki temu kolejny sync przesuwa się tylko do przodu (brak redundantnych transferów).

**Cap historii: max 200 wpisów (obie listy).** Po każdym `addHistory` (i po każdym syncu Firestore)
repo woła `pruneToLimit()` — DAO `DELETE … WHERE id NOT IN (SELECT id … ORDER BY timestamp DESC LIMIT 200)`.
Najstarsze wpisy są kasowane, gdy dochodzą nowe. Dotyczy zarówno `translation_history` (Translator)
jak i `ocr_history` (OCR). Stała limitu = 200 (na sztywno w SQL, brak preferencji).

**Infinity-scroll historii (offset paging, bez Paging 3):** listy historii w UI (Translator i OCR) NIE
ładują całej tabeli do pamięci. Repository eksponuje `getPage(offset, limit)` (DAO
`HistoryDao.getPage` / `OcrHistoryDao.getPage`: `SELECT … ORDER BY timestamp DESC LIMIT :limit OFFSET :offset`),
a ViewModel trzyma `historyItems: StateFlow<List<TranslationHistory>>` + `loadMoreHistory()`
(append kolejnej strony, pageSize 20) + `resetHistory()` (po dodaniu/usunięciu). W Composable
`val historyItems by viewModel.historyItems.collectAsState()`, `LazyColumn(state = historyListState)`
i `LaunchedEffect(historyListState) { snapshotFlow { layoutInfo → ostatni widoczny = totalItemsCount-1 }
.collect { if (atEnd) viewModel.loadMoreHistory() } }` — ładuje kolejne strony przy scrollowaniu do końca.
`allHistory` (pełna lista Flow) zostaje TYLKO dla startup sync'u (delta `getLocalSince`).
UWAGA: Paging 3 (`androidx.paging`) został usunięty — w lokalnym Gradle cache brak JAR-ów
`paging-runtime`/`paging-compose` (offline build), więc używamy ręcznego offset-pagingu (czysty
Room + StateFlow, zero zewnętrznych libs). Działa identycznie: nie ładujemy całej historii.

Sync jest też wyzwalany **natychmiast po każdym usunięciu** w `TranslatorViewModel.deleteHistory`
(gdy online) — tombstone wychodzi bez czekania na restart app.

**Reaktywny sync (od wersji 1.0.1):** sync nie czeka już na restart app. Dwa dodatkowe triggery:
- **Po każdej zmianie historii** — `TranslatorViewModel.addHistoryAndSync()` (wrap `historyRepository.addHistory`)
  oraz `OcrViewModel.addHistory()` odpalają `SyncManager.syncNow()` zaraz po zapisie lokalnym, więc
  nowe tłumaczenie/OCR trafia do chmury bez czekania na zamknięcie app.
- **Na włączenie sieci** — `ConnectivityObserver` (`data/ConnectivityObserver.kt`, `callbackFlow` na
  `NetworkCallback`) nasłuchuje `onAvailable`. `VerbigemApplication` subskrybuje `isOnline` i na
  przejściu `false→true` wywołuje `SyncManager.syncNow(uid)`. Dzięki temu offline-edits (dodania i
  tombstone'y z `pending_deletes`) wylatują do Firestore w momencie odzyskania netu — rozwiązuje
  scenariusz "telefon + tablet pracują offline, po połączeniu tylko jedna historia się wgrała".
- Startup sync pozostaje (AuthStateListener w `VerbigemApplication` + `LaunchedEffect(uid)` w
  `AppNavigation`) jako fallback dla zimnego startu.

**Głośnik / odczyt historii — animacja tylko na czytanej karcie.** Każdy wiersz historii (i wynik)
pokazuje `CircularProgressIndicator` zamiast ikony głośnika TYLKO gdy ten konkretny wiersz jest czytany.
Stan to `speakingSyncId` / `speakingProSyncId` (ViewModel; `null` lub syncId czytanego wiersza),
a karta porównuje `item.syncId == speakingSyncId`. Poprzednio był jeden współdzielony `isSpeaking: Boolean`,
przez co animacja włączała się na WSZYSTKICH kartach naraz.

**Odczyt historii w JĘZYKU WŁASNYM wiersza.** `speakHistory(item)` / `speakProHistory(item)` czytają
tekst w `LangCode.fromCode(item.targetLang)` — języku zapisanym przy tym konkretnym tłumaczeniu, a NIE
w bieżącym języku UI (który może się różnić od języka sprzed tygodnia). Dotyczy obu ekranów
(Translator `speakHistory`, OCR `speakHistory`). Wynik/edycja OCR czytają w bieżącym `targetLang`
(`viewModel.speak(text, targetLang)`).
- v6→v7 (karta kontaktu): `chat_hidden` (`chatId` PK, `hiddenAt`) — rozmowy usunięte
  ze skrzynki („usuń rozmowę"). Ustawienia per kontakt idą do Firestore, nie do Room
  — patrz wyżej.
- v5→v6 (faza 1 czatu): cztery tabele —
  - `chat_translations` (`msgId` + `targetLang` = PK, `chatId`, `translatedText`,
    `updatedAt`) — cache tłumaczeń wykonanych na TYM urządzeniu (decyzja D1),
  - `chat_outbox` (`clientMsgId` PK, `chatId`, `text`, `sourceLang`, `createdAt`,
    `status`, `attempts`) — kolejka wysyłek; id = klucz dokumentu w Firestore
    (idempotencja ponowień),
  - `chat_reads` (`chatId` PK, `lastReadAt`) — lokalny znacznik przeczytania
    (kropka „nieprzeczytane" w skrzynce, zero odczytów z chmury),
  - `chat_deleted_messages` (`msgId` PK, `deletedAt`) — lokalne tombstone'y
    „usuń u mnie" (wiadomości w Firestore są append-only).
- v2→v3: nowa tabela `pending_deletes` (`syncId TEXT PK`, `updatedAt INTEGER`) — kolejka tombstone'ów.
- v3→v4: nowa tabela `ocr_history` (`id PK`, `syncId`, `sourceText`, `translatedText`,
  `sourceLang`, `targetLang`, `timestamp`, `updatedAt`) — **lokalna historia OCR, synced do
  własnej kolekcji Firestore `ocr_history`** (nie do `history` Translatora; SyncManager woła
  `syncCollection(uid, "ocr_history", ocrHistoryRepository)`). `PendingDeleteEntity` zyskało
  kolumnę `collection` („history" / „ocr_history"), by tombstone trafił do właściwej kolekcji.
- `translation_history`: kolumny `syncId TEXT`, `updatedAt INTEGER` (z v2). **To jest historia Translatora**,
  synchronizowana z `users/{uid}/history/{syncId}` przez `SyncManager`.
- `tts_config` (id, apiKey, defaultModelId, chineseModelId, defaultVoice, chineseVoice, updatedAt).
- `HistoryDao`: `insert`, `update`, `getBySyncId`, `upsertBySyncId`, `deleteById`, `deleteBySyncId`, `clearAll`,
  `getAllHistory` (Flow, LIMIT 50 — dla sync delta), `getSince(since)` (delta push), `getPage(offset, limit)` (UI).
- `OcrHistoryDao`: `getAll` (Flow, LIMIT 50), `getPage(offset, limit)` (UI), `getSince(since)` (delta push),
  `insert`, `deleteById`, `clearAll`.
- `PendingDeleteDao`: `insert`, `getAll`, `deleteBySyncId`, `clearAll`.
- `TranslationHistory.create()` generuje `syncId` (UUID) + `updatedAt` (teraz).

**Filter logcat:** `SyncManager`.

---

## 📦 Auto-aktualizacja APK

`UpdateManager` + dialog w `MainActivity` (`StartupGate` / `UpdateDownloadDialog`). Po starcie (raz,
`LaunchedEffect`) odczytuje **`/updates/version.json`** z **Firebase Hosting** pod domeną
`mini.verbigem.com` — `UpdateManager.updateJsonUrl`:
```
https://mini.verbigem.com/updates/version.json
```
```json
{
  "versionCode": 25,
  "versionName": "1.0.24",
  "apkUrl": "https://mini.verbigem.com/android/app-debug-v25.apk?v=25",
  "playStoreUrl": "https://play.google.com/store/apps/details?id=com.verbigem.app",
  "onPlayStore": false,
  "minSupportedCode": 1,
  "updatedAt": "2026-09-03"
}
```
gdy `info.versionCode > currentVersionCode()` → `AlertDialog` (stringi: `update_available_title`,
`update_available_body`, `update_action`, `update_later`). W trakcie pobierania pokazuje się drugi
dialog — `UpdateDownloadDialog`: pasek + **procenty + licznik megabajtów**
(`update_downloading_title` / `update_downloading_body` / `update_download_percent` /
`update_download_bytes`). Błąd pobierania jest **widoczny** (`update_failed_title`, `update_failed`)
i daje przycisk `update_retry` — wcześniej szedł tylko do logcatu, a dialog wisiał na 0% bez wyjścia.

### Jak działa postęp pobierania (tu były dwa błędy — nie powielaj ich)

- `UpdateManager.downloadAndInstall` emituje `MutableStateFlow<DownloadProgress>`
  (`bytesRead`, `totalBytes`; `totalBytes == 0` = serwer nie podał `Content-Length`, wtedy
  `fraction == -1f` i UI pokazuje pasek nieoznaczony + sam licznik MB).
  **Każdy tick to nowa instancja** — `MutableStateFlow` deduplikuje po `equals()`, więc stary
  model (`MutableStateFlow<Float?>`, w którym `-1f` powtarzał się w kółko przy braku
  Content-Length) był po cichu gubiony i nie generował rekompozycji.
- Tick leci co **64 KB albo 250 ms** (`EMIT_EVERY_BYTES` / `EMIT_EVERY_MS`) — na wolnym łączu
  pasek i licznik MB ruszają się, zamiast czekać na pierwsze 256 KB (stary próg).
- Flow żyje w **`MainActivity`, nie w `remember {}`** (`MainActivity.downloadProgress`), a
  `StartupGate` czyta go `collectAsState()` **w swoim własnym scopie** i przekazuje wartość jako
  **parametr** do `UpdateDownloadDialog`.
  Dlaczego tak: `AlertDialog` renderuje się do osobnego okna = **subkompozycja**. Gdy stan jest
  czytany **wyłącznie** wewnątrz treści dialogu, rodzic się nie rekomponuje i dialog dalej
  pokazuje wartość z pierwszej kompozycji (objaw: „pasek zamarznięty na 0%"). Odczyt w scopie
  rodzica + przekazanie parametrem omija całą tę klasę problemu.
- **Diagnoza na telefonie:** logcat `UpdateManager` wypisuje co ~1 MB
  `Download progress: X KB / Y KB`, a po końcu `Download finished, bytes=… (content-length=…)`.
  Jeśli pasek stanie, te linie mówią wprost, czy winna jest sieć, czy Compose.

**Źródło pliku update — DWA pliki, nie pomyl:** katalog `dist/` w projekcie webapp
(`verbigem/mini`), deployowany przez `firebase deploy --only hosting --project mini-verbigem`
(ten sam projekt Firebase `mini-verbigem` co webappa i Firestore).
- **`dist/updates/version.json` — TEN, KTÓRY CZYTA APPKA.** Trzymany ręcznie. Jako jedyny ma w
  `apkUrl` **cache-buster `?v=N`** (`...app-debug-v25.apk?v=25`): query omija pułapkę
  `Cache-Control: immutable` na `/android/**` (CDN cache'uje URL z query jako osobny obiekt),
  więc nowy `versionCode` → nowy `?v=` → świeże bajty bez purgowania Cloudflara.
- `dist/android/version.json` — generowany przez plugin `injectBuildId` w `vite.config.ts`
  przy każdym `npm run build`. Appka go NIE czyta, ale trzymaj go zgodnego z `/updates/`,
  bo inaczej pierwszy `npm run build` cofnie `versionCode` do wartości z `vite.config.ts`.
APK wrzucamy jako `dist/android/app-debug-vN.apk`.

**Ścieżki:**
- `onPlayStore == false` (AKTYWNA): `downloadAndInstall()` pobiera APK przez **OkHttp 4.12**
  (własna instancja `okhttp3.OkHttpClient`, NIE systemowy `com.android.okhttp`) asynchronicznie na
  `Dispatchers.IO`.
  - Własny `okhttp3.Dns` (lookup przez `InetAddress.getAllByName`) — omija zepsuty resolver
    platformy na niektórych ROM (Xiaomi MIUI + Private DNS rzuca `Unable to resolve host` dla
    `*.web.app`; domena `mini.verbigem.com` resolwi się poprawnie, więc **używamy `.com`, nie `.web.app`**).
  - Po pobraniu (weryfikacja: `length >= 1 MB`, by odrzucić przypadkowy HTML) → instalacja
    przez `Intent.ACTION_INSTALL_PACKAGE` + `FileProvider` (`${packageName}.fileprovider`).
    Wymaga uprawnienia `REQUEST_INSTALL_PACKAGES`.
- `onPlayStore == true` (po rejestracji w Google Play): `openPlayStore()` → otwiera sklep.

**NIE używamy już:** GitHub repo / raw / API do update'ów (porzucone — `app_config_update.json` na git
usunięte z obiegu). Firestore `app_config/update` to tylko fallback w `fetchUpdateInfo()`
(gdy Hosting niedostępny), ale w praktyce update idzie z Hostingu.

**Jak wypuścić nową wersję (BEZ KABLA, BEZ GIT PUSH):**
1. W `app/build.gradle.kts` podnieś `versionCode` (+1) i `versionName`.
2. Zbuduj APK (NDK ~7 min). Preferowana komenda — **bezpośrednio przez wrapper Javy**:
   ```bash
   JAVA_HOME="C:/Users/milo/.jdks/jbr-21.0.11" ANDROID_HOME="C:/Users/milo/AppData/Local/Android/Sdk" \
     "C:/Users/milo/.jdks/jbr-21.0.11/bin/java.exe" \
     -classpath "gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain \
     assembleDebug --console=plain
   ```
   (`gradlew.bat` / `cmd.exe /c` działają z terminala Windows, ale w niektórych środowiskach
   `cmd.exe` jest blokowany — wtedy wrapper Javy jest jedyną drogą.)
   Wynik: `app/build/outputs/apk/debug/app-debug.apk`.
3. Skopiuj `app/build/outputs/apk/debug/app-debug.apk` → `verbigem/mini/dist/android/app-debug-vN.apk`
   (N = nowy versionCode).
4. Wyedytuj **`verbigem/mini/vite.config.ts`** (sekcja `android/version.json` w pluginie `injectBuildId`):
   ustaw `versionCode` = N, `versionName` = nowa nazwa,
   `apkUrl` = `https://mini.verbigem.com/android/app-debug-vN.apk?v=N` (**z `?v=N`**), `updatedAt`.
5. Wyedytuj **`verbigem/mini/dist/updates/version.json`** — TE SAME wartości (to ten plik
   czyta `UpdateManager`).
   - ⚠️ **Nie uruchamiaj `npm run build`.** Vite czyści `dist/` przed buildem (znikną wszystkie
     `dist/android/*.apk`) i zmienia hashe assetów webappy, czyli cicho podmienia działającą
     stronę na `mini.verbigem.com`. Ręczna edycja `dist/updates/version.json` jest poprawna
     **pod warunkiem** że krok 4 zrobiłeś identycznie — inaczej pierwszy `npm run build`
     wygeneruje `version.json` ze starym `versionCode` i rozjedzie się z wgranym APK
     (serwer poda starszą wersję niż zainstalowana → brak promptu o update).
   - Jeśli jednak MUSISZ przebudować webapp: kopia zapasowa `dist/android/` + `dist/updates/`,
     `npm run build`, przywrócenie APK-ów, przepisanie `dist/updates/version.json`.
6. `cd verbigem/mini && firebase deploy --only hosting --project mini-verbigem`.
7. Gotowe — appka na telefonie sama wykryje nową wersję przy starcie i zaproponuje update.
8. Po deployu **zweryfikuj**: `https://mini.verbigem.com/updates/version.json` (nie
   `/android/`!) musi zwracać nowy `versionCode`. W razie wątpliwości
   `curl -s ".../android/app-debug-vN.apk?v=N" | sha256sum` i porównaj z lokalnym plikiem.

### ⚠️ Pułapka: `immutable` cache na `/android/**` — NIGDY nie nadpisuj istniejącej nazwy APK

`firebase.json` ustawia dla `/android/**` nagłówek `Cache-Control: public, max-age=31536000,
immutable`. W praktyce: **każdy plik pod `/android/` jest zamrażany w CDN (Cloudflare) na rok**.

- Nowa, **nieużywana wcześniej** nazwa pliku (`app-debug-v24.apk`) → nie ma jej w cache →
  CDN pobiera świeżą treść. Działa od razu. ✅
- **Nadpisanie istniejącej nazwy** (`app-debug-v23.apk` wrzucony drugi raz z inną treścią) →
  Firebase przyjmie nowe bajty, ale CDN nadal serwuje starą kopię. Użytkownik pobierze
  POPRZEDNIĄ wersję spod tej samej nazwy. ❌

Jak to rozpoznać (diagnoza z 2026-09-03):
```
curl -s https://mini.verbigem.com/android/app-debug-v23.apk | sha256sum   # stary hash
curl -s "https://mini.verbigem.com/android/app-debug-v23.apk?cb=$RANDOM" | sha256sum  # nowy hash
curl -sI https://mini.verbigem.com/android/app-debug-v23.apk | grep -iE 'last-modified|age'
```
Jeśli `?cb=` daje inny hash niż czysty URL ⇒ to cache CDN, nie Firebase. Firebase **ma** nowy
plik (potwierdza to też domena `*.web.app`, która cache'uje osobno).

Wniosek praktyczny: **zawsze wypuszczaj nowy `versionCode` = nowa nazwa pliku.** Nadpisywanie
tej samej nazwy wymagałoby purgu cache w Cloudflare (brak tokena w repo) i i tak jest
niezgodne z konwencją wersjonowania.

Jeśli mimo to trzeba zweryfikować, co naprawdę serwuje serwer:
`aapt2 dump badging <plik.apk> | head -1` — pokazuje `versionCode` / `versionName`
wbudowane w pobrany APK (`C:/Users/milo/AppData/Local/Android/Sdk/build-tools/36.0.0/aapt2.exe`).

### ⚠️ `mini.verbigem.com` to TA SAMA webapp — deploy APK nie może jej przebudować

`verbigem/mini` serwuje na `mini.verbigem.com` aplikację **Mini Verbigem — Translator**
(własny `index.html` + bundle z `mini/src`). `firebase deploy --only hosting` **zastępuje
całą zawartość hostingu** zawartością `dist/` — plików, których w `dist/` nie ma, zostaną
USUNIĘTE z serwera.

Dlatego przy wypuszczaniu APK **nie wolno przebudowywać webappu przy okazji**:

- `npm run build` w `mini` zmienia hashe assetów (`assets/index-*.js`, `index-*.css`), bo
  wynik zależy od wersji zależności w `node_modules`, a nie tylko od `src/`.
  Przebudowa = cicha podmiana działającej strony.
- **Bezpieczna procedura** (użyta 2026-09-03): `dist/` odtworzony **co do bajtu** ze stanu
  na serwerze + zmienione TYLKO `android/version.json` i nowy APK.
  1. kopia `dist/android/` (Vite czyści `dist/`),
  2. pobranie wdrożonych plików: lista + ścieżki są w `mini/.firebase/hosting.ZGlzdA.cache`
     (`ZGlzdA` = base64 „dist"), każdy `curl -o dist/$p https://mini.verbigem.com/$p`,
  3. sprawdzenie `find dist -type f` vs lista z cache — **żadnego nowego i żadnego brakującego
     pliku** (inaczej deploy coś doda/usunie),
  4. podmiana `android/version.json` + wrzucenie APK.
  Wtedy `firebase deploy` zgłasza `uploading new files [0/1]` — czyli webapp nietknięta
  (potwierdzenie: `/version.json` z `BUILD_ID` nie zmienia wartości).

**Osobne projekty — nie pomyl:** `mini` → projekt Firebase `mini-verbigem` (mini.verbigem.com).
`webapp` → projekt `verbigem-app-7k2` (verbigem.com, językowa apka do nauki). Deploy z `mini`
**nie ma fizycznie jak** nadpisać `webapp`.

### 🐛 Uśpiony błąd: `npm run build` w mini sypie błędem (tesseract.js)

`src/ocr/OcrPage.tsx` (linia 64) robi `await import('tesseract.js')`, ale pakietu nie było
w `package.json` ani w `package-lock.json` — był zainstalowany tylko w `node_modules`, które
potem zostało wyczyszczone. Skutek: `tsc -b` → `TS2307: Cannot find module 'tesseract.js'`,
a `vite build` → `Rollup failed to resolve import "tesseract.js"`.

Naprawione 2026-09-03 przez `npm install tesseract.js --save` (dopisany do `package.json`).
UWAGA: wersja `tesseract.js` wchodzi w skład bundle'a, więc jej zmiana **zmienia hashe
assetów** — po reinstalacji nie zakładaj, że build odtworzy wdrożoną stronę co do bajtu.

**Wymagane w manifeście:** `REQUEST_INSTALL_PACKAGES`, `<provider>` FileProvider z
`android:authorities="${applicationId}.fileprovider"`.

**Filter logcat:** `UpdateManager` (logi: `Starting APK download`, `Download started/finished`,
`Download error`, `Install launch failed`).

---



## 🌍 Wielojęzyczność — ZAKAZ hardkodowania tekstów UI

Aplikacja jest **wielojęzyczna** (PL, EN, DE, ES, ZH, TR). **Pod karą nie wolno** hardkodować
żadnych tekstów interfejsu (etykiet, komunikatów, tooltipów, opisów) bezpośrednio w kodzie
Kotlin/Compose (ani po polsku, ani po angielsku).

**Zasady:**

1. Każdy widoczny tekst UI musi pochodzić z `stringResource(R.string.xxx)`.
2. Zasoby znajdują się w:
   - `app/src/main/res/values/strings.xml` — **domyślny (angielski)**,
   - `values-pl/`, `values-de/`, `values-es/`, `values-zh/`, `values-tr/` — tłumaczenia.
3. Etykiety silników (`EngineChoice`) i opisy tooltipów używają `descriptionResId` /
   `labelResId` mapowanych na `R.string.*` (nie `labelKey` z hardkodowanym tekstem).
4. Komunikaty błędów z warstwy `engine`/* (np. `HyMt2NativeEngine`) pobieramy przez
   `context.getString(R.string.xxx)` — **nie** dosłowny string w kodzie.
5. Po dodaniu nowego `string` do `values/strings.xml` **należy** dodać go do wszystkich
   pozostałych `values-xx/strings.xml` (nawet jeśli to tymczasowy angielski fallback).
6. Klucze akcji historii/result: `action_copy`, `action_share`, `action_read`, `action_read_pro`,
   `action_delete`. Dialog update: `update_available_title/body/action/later`. Reklama: `ad_banner_label/text`.

---

## 🔥 Firebase — konfiguracja projektu

- **Projekt Firebase:** `mini-verbigem` (project_number `1064156518963`, zgodne z
  `app/google-services.json` → `mobilesdk_app_id` `1:1064156518963:...`).
- **Package:** `com.verbigem.app`.
- **Kolekcje Firestore:**
  - `users/{uid}` — profil (`UserProfile`: plan, walletCreditsCents, uiLang, speakLang*, ...).
    **Czytelny wyłącznie przez właściciela.**
  - `usersPublic/{uid}` — publiczna wizytówka do wyszukiwania (`PublicProfile`): uid,
    nickname, photoURL, uiLang, speakLangSource/Target, `searchNick`, `searchEmail`
    (zlowercasowane, bo Firestore nie ma case-insensitive `whereGreaterThanOrEqualTo`).
    Read dla zalogowanych, **create/update tylko właściciel**. Utrzymywana przez
    `AuthRepository`; backfill starych kont: `node backfill_faza0.js --apply`.
  - `users/{uid}/history/{syncId}` — historia tłumaczeń (sync, last-write-wins).
  - `friendships/{id}` (kolekcja główna) — `members: [uidA, uidB]` + uidA/uidB, status,
    requestedBy, nicki. Zapytania **po `members`**.
  - `chats/{chatId}` (+ `chats/{chatId}/messages`) — czat/kontakty. Dokument czatu
    **musi** mieć `members`, inaczej reguły odrzucą każdą wiadomość. Zawiera też
    `lastMessage`, `lastMessageAuthorId`, `lastMessageAt` (ms) — zasilają skrzynkę.
    Wiadomość: `authorId`, `sourceLang`, `text` (ORYGINAŁ), `senderTranslation`
    (`{lang, text}` — podpowiedź nadawcy), `type`, `clientMsgId`. Id dokumentu =
    `clientMsgId`, więc ponowienie wysyłki jest idempotentne.
  - `chats/{chatId}/readReceipts/{uid}` — `{uid, lastReadAt}` — potwierdzenia
    odczytu (jeden dokument na uczestnika; reguła: tylko własny dokument).
  - `chats/{chatId}/typing/{uid}` — `{uid, expiresAt}` — wskaźnik „pisze…"
    (wygaśnięcie po 8 s, więc padnięta aplikacja nie udaje że pisze).
  - `users/{uid}/contacts/{otherUid}` — ustawienia per kontakt (`ContactSettings`):
    `alias`, `langOverride`, `muted`, `pinned`, `blocked`, `note`, `updatedAt`.
    Odczyt i zapis **tylko właściciela** + whitelist pól (bez niej klient mógłby
    dopisać flagi, które serwer zacznie rozumieć w fazie 2). Pod `users/{uid}`,
    nie w `friendships` — to moje zdanie o kimś, nie wspólna umowa.
  - `users/{uid}/fcmTokens/{token}` — tokeny FCM do powiadomień push. **ID dokumentu
    to token**, żeby Cloud Function mogła skasować martwy token po samym ID (bez
    odczytu). Jeden dokument = jedno urządzenie, więc push idzie na telefon i tablet.
    Odczyt/zapis tylko właściciel + whitelist pól.
  - `app_config/tts` — konfiguracja TTS Pro (modele OpenRouter + apiKey).
  - `app_config/update` — metadane auto-update (versionCode, apkUrl, onPlayStore).
- **CLI:** `firebase.cmd` (npm global, `C:/Users/milo/AppData/Roaming/npm`). Zapis dokumentów:
  `firebase firestore:set` NIE istnieje w tym CLI — użyto Firestore REST API (Node.js) z
  tokenem z `%USERPROFILE%\.config\configstore\firebase-tools.json`.
  - ⚠️ **Token wygasa po ~1 h** (pole `expires_at` w configstore, ms). Starsze skrypty w
    repo (`write_firestore.js`, `update_firestore_update.js`) odświeżają go samodzielnie
    przez `oauth2.googleapis.com` z wpisanymi na sztywno `client_id`/`client_secret` —
    **to już nie działa**, Google zwraca `invalid_client` (w repo krążą dwa różne,
    oba błędne, client_id).
  - **Działające odświeżanie:** odpalić dowolne polecenie CLI (np. `firebase projects:list`
    — czysty odczyt, zero skutków ubocznych). CLI ma poprawne poświadczenia wbudowane
    i zapisuje świeży `access_token` z powrotem do configstore. Tak robi
    `backfill_faza0.js` (`refreshViaCli()`).
  - ⚠️ **REST z tokenem właściciela projektu OMJA reguły bezpieczeństwa.** Backfill potrafi
    zapisać cudzy dokument, którego aplikacja nie ruszy. Pożądane przy migracjach,
    niebezpieczne przy pomyłce w skrypcie — dlatego `backfill_faza0.js` domyślnie
    robi dry-run i wymaga `--apply`.
- **Reguły dostępu (`firestore.rules` w repo):** deploy przez
  `firebase deploy --only firestore:rules --project mini-verbigem`. Wymagane reguły dla
  auto-update i TTS: `match /app_config/{doc} { allow read: if true; allow write: if false; }`
  (app czyta `app_config/update` i `app_config/tts` PRZED loginem). Bez tego dokumenty są
  blokowane (`PERMISSION_DENIED`) i app nie wykrywa update'u ani nie pobiera konfiguracji TTS.
- **Pliki konfiguracyjne w repo:** `firebase.json` (wskazuje `firestore.rules`, blok
  `functions` i reguły), `.firebaserc` (projekt `mini-verbigem`).

---

## ☁️ Cloud Functions (`functions/`) — Node 20 + TypeScript

Backend czatu. **Wymaga planu Blaze** (decyzja D6 — wykupiony). Kod w `functions/src/`,
kompilacja do `functions/lib/` (`lib/` jest w `.gitignore`).

| Funkcja | Typ | Co robi |
|---|---|---|
| `onMessageCreated` | trigger Firestore `chats/{chatId}/messages/{msgId}` | push FCM do pozostałych członków czatu |
| `matchContacts` | callable (HTTPS) | dopasowanie kontaktów po HMAC numeru telefonu (2.3) |
| `inviteByPhone` | callable (HTTPS) | zapraszanie numerów, które nie mają jeszcze konta (2.4) |
| `verifyPhone` | callable (HTTPS) | zapis faktu „to konto ma zweryfikowany numer" (2.6) |
| `onPhoneVerified` | trigger Firestore `users/{uid}` | uzgadnia `phoneDirectory`, rozwiązuje zaproszenia (2.4) |
| `onMessageSearchIndex` | trigger Firestore `chats/{chatId}/messages/{msgId}` | zapisuje znormalizowane `searchText` do wyszukiwania (1.12) |

### Kanały wychodzące (3.5) — jak zapraszamy ludzi bez Verbigema

`data/OutboundChannel.kt`: jeden interfejs, pięć implementacji. **Verbigem
tłumaczy, potem przekazuje** — niczego nie wysyłamy sami, bo nie wiedzielibyśmy,
czy dotarło, i udawalibyśmy, że wiemy.

| Kanał | Mechanizm | Ograniczenie |
|---|---|---|
| WhatsApp | `ACTION_VIEW` → `wa.me/<numer>?text=…`, paczka `com.whatsapp` → `w4b` → bez paczki | Nie wiemy, czy numer jest na WA — bez zewnętrznego API się nie dowiemy |
| SMS | `ACTION_SENDTO` → `smsto:<numer>` + `sms_body` | **Celowo nie `SmsManager`** — `SEND_SMS` to wniosek do Google Play i ryzyko odrzucenia |
| E-mail | `ACTION_SENDTO` → `mailto:` + `EXTRA_EMAIL` | Wymaga adresu; importer czyta je osobnym zapytaniem po `CONTACT_ID` |
| Telegram | `t.me/share/url?url=…&text=…` | Otwiera **wybór rozmowy**, nie konkretną osobę |
| Inne | systemowy `ACTION_SEND` | Pokrywa Signal, Messenger i wszystko, czego nie przewidzieliśmy |

☠️ **Plan §5.4 mylił się co do Telegrama.** Zakładał `t.me/<username>` i pójście
na schowek, bo „URL Telegrama nie potrafi wkleić tekstu". Zwykły nie potrafi,
ale `t.me/share/url` **tak**, i to z jednoczesnym podaniem linku — czyli
dokładnie tym, czego potrzebuje zaproszenie. Odpada snackbar „skopiowaliśmy".

`OutboundTarget` to lekki nośnik na czas lotu, **nie** encja `external_contacts`
z 3.6 — kiedy powstanie tabela w Room, dostanie `toTarget()`. Robienie z tego
encji teraz byłoby zgadywaniem kształtu czegoś, czego jeszcze nie zapisujemy.

Dostępność liczy się per odbiorca: „SMS" znika, gdy wpis nie ma numeru,
„E-mail", gdy nie ma adresu albo nikt na telefonie nie ma klienta poczty.

### Wyszukiwanie w wiadomościach (1.12) — jak to działa

Firestore **nie ma wyszukiwania pełnotekstowego**. Jedyna tania sztuczka to zakres
po prefiksie: `where >= q` i `where < q + "\uF8FF"` (`\uF8FF` to ostatnia dozwolona
wartość z prywatnego obszaru UTF-8). Dlatego „kot" znajdzie „kot ma Alego", ale
**nie** „Ala ma kota" — ograniczenie jest napisane wprost w UI, zamiast żeby
użytkownik miał się go domyślić.

`searchText` to dane pochodne, więc zapisuje je wyłącznie Cloud Function — reguły
zabraniają klientowi dotknąć tego pola. Gdyby klient mógł je ustawić, mógłby
zaindeksować coś innego niż wysłał i wypychać własne wiadomości na każde cudze
zapytanie.

**Transformacja musi być identyczna po obu stronach** (`functions/src/searchIndex.ts`
↔ `app/.../data/MessageSearch.kt`): NFD → zdjęcie znaków łącznych (`\p{M}`) →
lowercase → trim → 2000 znaków. Bez NFD polski użytkownik piszący „jestes" nigdy
nie znalazłby własnego „jesteś". Rozjazd obu implementacji objawia się **ciszą**:
każde zapytanie zwraca zero wyników i nic w logach o tym nie mówi.

**Zapytanie idzie per czat, nie jako collection group.** Zapytanie grupowe
przemiatałoby każdą rozmowę w bazie, a reguły nie potrafią go ograniczyć — dostęp
decyduje `get()` na nadrzędnym czacie, czego group query nie wyrazi. Przejście po
własnej liście rozmów trzyma odczyt wewnątrz dokumentów, których użytkownik i tak
jest członkiem.

**Backfill:** trigger indeksuje tylko nowe wiadomości. Dla historii sprzed wdrożenia
jest `backfill_searchtext.js` (dry run domyślnie, `--apply` zapisuje). Wymaga
`cd functions && npm run build` — skrypt importuje normalizację ze zbudowanego
`lib/`, żeby nie trzymać drugiej kopii.

### Weryfikacja numeru (2.6) — jak to działa

**Aplikacja NIGDY nie wysyła numeru do naszego backendu.** Kolejność:

1. Firebase Phone Auth wysyła SMS i dowiązuje numer do konta
   (`linkWithCredential` — nie `signInWithCredential`, bo konto już istnieje).
2. Firebase wkłada zweryfikowany numer E.164 do tokena ID.
3. `getIdToken(true)` odświeża token — bez tego funkcja widziałaby token sprzed
   dodania numeru.
4. `verifyPhone` czyta `request.auth.token.phone_number` i zapisuje na `users/{uid}`
   tylko `phoneVerified` + `phoneHash` + `phoneVerifiedAt`. **Nie ma parametru
   do sfałszowania**, bo klient nie jest pytany o numer.
5. `onPhoneVerified` (trigger) dopisuje `phoneDirectory/{hmac}` i rozwiązuje
   zaproszenia oczekujące pod tym numerem.

☠️ **`requireSmsValidation(true)` w `PhoneAuthOptions` jest obowiązkowe.**
Bez niego Firebase potrafi zweryfikować numer w locie albo sam przechwycić SMS-a
i **samodzielnie zalogować użytkownika** — wylogowując go z dotychczasowego konta
i wstawiając nowe, oparte tylko na numerze. Metoda istnieje w `firebase-auth` 23.0.0.

☠️ **Przed pierwszym testem trzeba wpisać odciski SHA certyfikatu** w Firebase
Console → Project settings → Your apps → aplikacja Android → **Add fingerprint**,
potem pobrać nowy `google-services.json`. Bez tego Phone Auth nie przejdzie.
Debugowy keystore (`C:\Users\milo\.android\debug.keystore`, hasło `android`):

```
SHA1:   EC:9D:EB:58:CD:F2:48:3A:7E:FE:2B:73:C2:C7:90:1B:9D:6D:3C:CC
SHA256: A4:2A:45:FF:D5:25:B9:02:8C:12:2B:BE:8A:92:FE:D9:F0:3D:A7:5C:9F:D1:CA:4D:90:98:8D:CA:AD:EC:CA:94
```

### Normalizacja numerów (E.164)

Matching porównuje **skróty**, nie łańcuchy: `0981 123 456` i `+595981123456` haszują
się zupełnie inaczej. Dlatego każdy numer musi być pełnym E.164 **przed** haszowaniem.

`app/src/main/java/com/verbigem/app/data/PhoneNumbers.kt` robi to przez
`android.telephony.PhoneNumberUtils.formatNumberToE164` — libphonenumber jest w
systemie, więc nie dokładamy zależności. Bez numeru kierunkowego kraj się zgaduje
(karta SIM, potem locale urządzenia), więc `e164Candidates` zwraca **listę** możliwych
postaci i aplikacja haszuje każdą.

Zmiana normalizacji po weryfikacji pierwszych numerów oznaczałaby **przeliczenie
całego `phoneDirectory`** — rób ją tylko wtedy, gdy katalog jest pusty.
Pozostała luka: ekspat z zagraniczną kartą SIM. Zakryje ją libphonenumber (faza 3.x).

### App Check

Zainicjowany w `VerbigemApplication`, ale **dostawca zależy od wariantu**
(`src/debug` → `DebugAppCheckProviderFactory`, `src/release` → Play Integrity).
`firebase-appcheck-debug` jest wyłącznie w `debugImplementation`, więc build
release'owy fizycznie nie potrafi sam siebie poświadczyć.

⚠️ **`matchContacts` ma `enforceAppCheck: false` i to nie jest przeoczenie.**
APK, który dystrybuujemy przez auto-update, to build **debugowy**, a Play Integrity
nie poświadcza aplikacji, których nie zainstalował Play Store. Włączenie tego dziś
odrzucałoby każdego użytkownika, nie tylko nadużywających. Włączyć razem z pierwszym
wydaniem na Play — TODO w `functions/src/contacts.ts` mówi gdzie.

Debugowy token do wpisania ręcznie (Firebase Console → App Check → **Debug tokens**)
pojawia się w logcat po tagiem `FirebaseAppCheck`. Jest per instalacja, więc trzeba
go wpisać ponownie po reinstalacji.

### Jak deployować

```bash
cd functions
npm install         # raz, po klonie
npm run build       # tsc -> lib/  (deploy i tak to robi przez predeploy w firebase.json)

# z katalogu głównego projektu:
firebase deploy --only functions --project mini-verbigem
firebase deploy --only firestore:rules --project mini-verbigem   # po zmianie reguł
firebase functions:log --project mini-verbigem                   # logi
```

`firebase.json` ma `predeploy: ["npm --prefix \"$RESOURCE_DIR\" run build"]`, więc
deploy sam kompiluje — `npm run build` ręcznie jest tylko po to, żeby złapać błąd typów
bez czekania na upload.

⚠️ **Deploy katalogu `functions/`:** Firebase CLI uruchamia lokalny serwer discovery,
który ładuje `lib/index.js` i ma **domyślnie 10 s** na odpowiedź. Na tej maszynie
zimny start Node + `firebase-admin` to za mało i deploy kończy się
`User code failed to load. Cannot determine backend specification. Timeout after 10000`.
Rozwiązanie (wartość w sekundach):

```bash
export FUNCTIONS_DISCOVERY_TIMEOUT=60
firebase deploy --only functions --project mini-verbigem
```

⚠️ **Node 20 jest przestarzały** — Google wyłączy go 2026-10-30. Przed tą datą trzeba
podnieść runtime (`firebase.json` → `runtime`) i `engines.node` w `functions/package.json`
na nodejs22, inaczej deploy przestanie działać.

### ☠️ NIGDY nie deployuj funkcji bez wylistowania nazw

Projekt `mini-verbigem` jest **współdzielony z webappem `verbigem/mini`**, który ma
własny katalog `functions/` i pięć działających funkcji produkcyjnych:

| Funkcja | Region | Po co |
|---|---|---|
| `deepseekProxy` | europe-west1 | tłumaczenie online (Pro) |
| `paddleWebhook` | europe-west1 | płatności — webhook Paddle |
| `portalSession` | europe-west1 | portal klienta Paddle |
| `visionProxy` | europe-west1 | OCR online |
| `walletTopUp` | europe-west1 | doładowanie portfela |

Oba projekty mają `codebase: default`, więc Firebase widzi **jeden** zbiór funkcji.
`firebase deploy --only functions` uruchomiony stąd uznaje tamte pięć za osierocone
i chce je **usunąć**. W trybie nieinteraktywnym na szczęście się wykłada
(`Aborting because deletion cannot proceed in non-interactive mode`) — z `--force`
po prostu by je skasowało: płatności, OCR i portfel przestałyby działać.

Zawsze podawaj nazwy:

```bash
firebase deploy --only functions:onMessageCreated,functions:matchContacts,\
functions:inviteByPhone,functions:verifyPhone,functions:onPhoneVerified,\
functions:onMessageSearchIndex \
  --project mini-verbigem
```

Dopiero nadanie obu projektom różnych `codebase` w `firebase.json` (np. `android`
i `mini`) trwale rozwiązałoby problem — wymagałoby jednak przewalczenia już
wdrożonych funkcji, więc na razie zostawiamy jak jest i uważamy.

⚠️ **Pierwszy deploy funkcji 2. gen na projekcie ZAWSZE sypie błędem Eventarc:**
`Permission denied while using the Eventarc Service Agent`. Uprawnienia Service Agent
propagują się z opóźnieniem — po prostu powtórzyć deploy po kilku minutach (drugi
przeszedł od razu). Nie szukać błędu w kodzie.

⚠️ **Po udanym deployu CLI żąda polityki czyszczenia obrazów:** kontenery w Artifact
Registry rosną z każdym deployem i kosztują. Ustawione jednorazowo:

```bash
firebase functions:artifacts:setpolicy --location us-central1 --days 7 --force \
  --project mini-verbigem
```

### Sekrety (Secret Manager)

`matchContacts` potrzebuje pieprzu do HMAC numerów telefonów:

```bash
printf '%s' "$(openssl rand -hex 32)" | \
  firebase functions:secrets:set PHONE_HASH_PEPPER --project mini-verbigem
```

⚠️ **Rotacja pieprzu unieważnia wszystkie dopasowania** — stare hashe w `phoneDirectory`
przestaną się zgadzać. Zmiana wymaga przeliczenia katalogu, nie tylko podmiany sekretu.
Funkcja nie wdroży się bez ustawionego sekretu (deploy pyta o to automatycznie).

### Powiadomienia push — decyzje, których nie wolno zmienić niechcący

1. **Wiadomość FCM jest `data-only` (bez pola `notification`).** Z polem `notification`
   Android sam renderuje powiadomienie, gdy aplikacja jest w tle, i **nie wywołuje
   `onMessageReceived`** — akcje „Odpowiedz" i „Oznacz jako przeczytane" działałyby
   tylko dla wiadomości przychodzących przy otwartej aplikacji. Data-only daje pełną
   kontrolę zawsze; ceną jest podatność na Doze, dlatego `priority: "high"` + TTL 4 tyg.
2. **Kanał `verbigem_messages`.** Identyfikator jest po obu stronach: `functions/src/
   messaging.ts` (komentarz) i `VerbigemNotifications.ensureChannel()`. Zmiana w jednym
   miejscu bez drugiego = ciche zniknięcie powiadomień na Androidzie 8+.
3. **Podgląd treści DOMYŚLNIE WYŁĄCZONY.** `buildBody()` zwraca „Nowa wiadomość",
   chyba że `app_config/notifications` ma `showMessagePreview == true`. Push wychodzi
   z urządzenia i przechodzi przez serwery Google — to inna historia prywatności niż
   „tłumaczenie dzieje się na Twoim telefonie". Bezpieczeństwo niejawne: brak dokumentu
   = podgląd wyłączony. Przełącznik w Firestore, da się włączyć bez nowego APK.
4. **Treść podglądu z `senderTranslation`, nie z `text`.** Podpowiedź nadawcy powstała
   *w języku odbiorcy* (decyzja D1), więc to jedyna wersja, którą ten człowiek przeczyta.
   Surowy `text` jest w języku nadawcy.
5. **Wyciszenie jest honorowane w chmurze**, nie na urządzeniu (`muted === true` na
   `users/{odbiorca}/contacts/{nadawca}`) — wyciszony czat nie budzi telefonu.
6. **Token = ID dokumentu.** FCM zwraca `messaging/registration-token-not-registered`
   dla martwych tokenów; funkcja kasuje je po ID, bez odczytu.

Po stronie aplikacji: `VerbigemMessagingService` (odbiera), `FcmTokenManager`
(`users/{uid}/fcmTokens/{token}`, rejestracja w `VerbigemApplication` po odtworzeniu
sesji, usunięcie przy wylogowaniu), `VerbigemNotifications` (kanał, grupa per czat,
MessagingStyle z historią, akcje), `NotificationActionReceiver` (odpowiedź + przeczytane
pod `goAsync()`, bo robią zapis do Firestore).

Uprawnienie `POST_NOTIFICATIONS` (Android 13+) jest proszone **raz, przy pierwszym
otwarciu skrzynki czatu** — nie na starcie aplikacji, bo użytkownik nie ma wtedy
powodu chcieć powiadomień, a Android przestaje pytać po dwóch odmowach. Flaga
`asked_notif_perm` w DataStore.

---

## 🚀 Jak uruchomić projekt

1. Otwórz katalog `c:\Users\milo\verbigem_android` w **Android Studio**.
2. Poczekaj na automatyczną synchronizację Gradle (`Sync Project with Gradle Files`).
3. Podłącz urządzenie z Androidem lub uruchom emulator (z obsługą `arm64-v8a` lub `x86_64`).
4. Kliknij **Run** (Zielony trójkąt / `Shift + F10`) lub zbuduj APK:
   ```bash
   ./gradlew assembleDebug
   ```

**Build (Windows, bash):** JDK 21 w `C:/Users/milo/.jdks/jbr-21.0.11` (ustaw `JAVA_HOME`).
Natywny build NDK (~2–3 min) kompiluje `libverbigem_llama.so` (Release + KleidiAI + Vulkan).

### Flow wydania (auto-update end-to-end — BEZ KABLA)

Pełna, aktualna procedura jest wyżej, w sekcji **„📦 Auto-aktualizacja APK → Jak wypuścić
nową wersję"** — to JEDYNE źródło prawdy (poniżej tylko skrót; wcześniejsza wersja tego
skrótu błędnie kazała edytować `dist/android/version.json` ręcznie, co rozjeżdżało się
z generowaniem pliku przez Vite).

1. Podbić `versionCode`/`versionName` w `app/build.gradle.kts`
   (konwencja: `versionName` = `1.0.(versionCode - 1)`, np. code 25 → name `1.0.24`).
2. Build APK (wrapper Javy, patrz wyżej) → `app/build/outputs/apk/debug/app-debug.apk`.
3. APK → `verbigem/mini/dist/android/app-debug-vN.apk` (N = nowy versionCode).
4. **`verbigem/mini/vite.config.ts`** (plugin `injectBuildId`) → `versionCode`, `versionName`,
   `apkUrl` = `https://mini.verbigem.com/android/app-debug-vN.apk?v=N`
   (**z cache-busterem `?v=N`**), `updatedAt`. To źródło prawdy dla przyszłych buildów Vite.
5. **`verbigem/mini/dist/updates/version.json`** — wpisz TE SAME wartości (to ten plik czyta
   appka). ⚠️ **Nie uruchamiaj `npm run build`**, chyba że celowo przebudowujesz webapp:
   `npm run build` zmienia hashe assetów i podmieni działającą stronę. Ręczna edycja
   `dist/updates/version.json` jest bezpieczna wtedy i tylko wtedy, gdy krok 4 zrobiłeś
   tak samo — inaczej pierwszy `npm run build` cofnie `versionCode`.
6. `cd verbigem/mini && firebase deploy --only hosting --project mini-verbigem`.
   Deploy zastępuje hosting zawartością `dist/` — przed deployem sprawdź
   `find dist -type f`, czy nie brakuje plików webappy (patrz wyżej „`mini.verbigem.com`
   to TA SAME webapp").
7. Zweryfikuj: `curl -s https://mini.verbigem.com/updates/version.json` musi zwrócić nowy
   `versionCode` i `apkUrl` z `?v=N`.
   - Jeśli zmieniono reguły Firestore (np. nowa kolekcja jak `ocr_history`):
     `cd verbigem/android && firebase deploy --only firestore:rules --project mini-verbigem`.
     App czyta `app_config/*` PRZED loginem (reguły muszą pozwalać read:true), a zapisuje
     do `users/{uid}/history` i `users/{uid}/ocr_history` (oba `allow read,write` dla właściciela).
8. Test: zainstaluj starszy APK (niższy versionCode) → otwórz → dialog update → pobierz nowy.

**Repozytorium git:** `https://github.com/ihletru/verbigem_android` (branch `master`, prywatne).
`.gitignore` wyklucza: `build/`, `llama_master/`, `*.gguf`, `model_probe/`, `build_log*`,
`crash_log*`, `local.properties`, `.workbuddy-ai/`.
Po każdej zmianie większej niż kosmetyczna: **zaktualizuj README, potem commit + `git push`.**
Projekt `mini` (webapp + hosting) **nie ma repozytorium git** — deploy idzie tylko przez
`firebase deploy`.
