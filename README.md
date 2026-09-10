# Verbigem Android — Natywny Tłumacz Hy-MT2 (100% Kotlin + NDK)

> 📦 **Aktualna wersja: `v1.0.49`** (versionCode 50) — reklamy banerowe AdMob + zgody UMP —
> [Releases](https://github.com/ihletru/verbigem_android/releases) ·
> [Historia zmian (CHANGELOG.md)](CHANGELOG.md) ·
> [Co nowego na stronie (6 języków)](https://mini.verbigem.com/android/changelog.html)

> ℹ️ Wersja trzymana jest w `app/build.gradle.kts` (`versionCode` / `versionName`).
> Tagi `v1.0.1`–`v1.0.3` to wczesne buildy historyczne (versionCode 2–3).

Natywna aplikacja na system Android stworzona w **100% w języku Kotlin** z wykorzystaniem **Jetpack Compose** oraz dedykowanego, natywnego silnika wnioskowania **Hy-MT2-1.8B** (Tencent Hunyuan) w formacie **GGUF** przez mostek **C++/JNI (llama.cpp NDK)** z akceleracją sprzętową ARM NEON oraz Vulkan GPU.

Wzorowana na architekturze i funkcjach `mini.verbigem.com` (`verbigem/mini`).

Pełna, historyczna wersja tego dokumentu (przed kondensacją): **`docs/README_ARCHIWUM.md`**.

---

## 🌟 Kluczowe funkcje

### 1. Translator (ekran główny)

- Tłumaczenie między 6 językami: **PL, EN, ES, ZH, DE, TR**.
- Silniki: ⚡ **Szybki** (1.8B @1.25Bit, ~440 MB) · 🎯 **Dokładny** (1.8B @Q4_K_M, ~1.1 GB) · 🧠 **Pro 7B** (7B @UD-Q2_K_XL, ~2.9 GB) · ⚖️ **Oba (porównaj)** · ☁️ **API online** (fallback DeepSeek z portfelem).
- ⚠️ **Pro 7B jest ukryty na słabszych urządzeniach.** `ModelDownloader.blockReason()` sprawdza RAM ≥ 5 GB i ≥ 1.2× rozmiar wag wolnego miejsca; jeśli warunek nie jest spełniony, ikona w ogóle się nie pojawia (`TranslatorViewModel.availableEngines`). Użytkownik nie może więc pobrać 2.9 GB, których nie da się załadować.
- **Push-to-talk (🎤):** przycisk po lewej od OCR. Trzymaj → nagrywa (SpeechRecognizer STT z podglądem na żywo), puść → dopisuje rozpoznany tekst do pola (**append, nie overwrite**). W trakcie nagrywania mikrofon jest czerwony.
- **Czytaj Pro (💎):** płatne TTS przez **OpenRouter**, konfigurowane w `app_config/tts` na Firestore (domyślny model `google/gemini-3.1-flash-tts-preview`, osobny dla ZH: `fish-audio/s2.1-pro`). Szczegóły niżej.
- **Skasuj** — czyszczenie wejścia i wyniku. Automatyczny downloader modeli z Hugging Face.
- **Historia w Room (SQLite)** — wiersz ma akcje: kopiuj, udostępnij, czytaj (offline), czytaj Pro (💎), skasuj.
- **Startowa synchronizacja z Firestore** (last-write-wins po `updatedAt`): profil, historia, konfiguracja TTS Pro.
- **Auto-aktualizacja APK** z Firebase Hosting — patrz sekcja **📦 Auto-aktualizacja APK**.

### 2. Rozmowa lokalna (Conversation)

- Dwie osoby dzielą jeden telefon (Strona A / Strona B), automatyczne przełączanie aktywnej strony.
- **⚠️ Kolejność modyfikatorów w `ConversationScreen` jest nieprzypadkowa:** `imePadding()` **PRZED** `verticalScroll()`.
  ```kotlin
  Modifier.fillMaxSize().imePadding().padding(16.dp).verticalScroll(scrollState)
  ```
  Użyte PO (`==` wewnątrz) `verticalScroll()` dokłada tylko pusty margines na końcu treści, a viewport zostaje pełnej wysokości — wtedy `bringIntoViewRequester` „przewija pole do widoku" = **na sam dół ekranu, czyli pod klawiaturę**. Odwrócona kolejność zwęża viewport o wysokość IME, więc „do widoku" znaczy „nad klawiaturą".
  Druga połowa naprawy: klawiatura wjeżdża **~250 ms po** focusie, więc sam `onFocusEvent` nie wystarcza — `LaunchedEffect(WindowInsets.isImeVisible)` z `delay(250)` odpala `bringIntoView()` ponownie. Wymagane: `android:windowSoftInputMode="adjustResize"` w manifeście + `enableEdgeToEdge()` w `MainActivity` (bez tego `imePadding()` jest zerem).
- Natywne STT z podglądem na żywo → tłumaczenie Hy-MT2-1.8B → automatyczny odczyt TTS dla rozmówcy.

### 3. Czat zdalny 1:1 (Chat)

- **Skrzynka (`ChatListScreen`)** — avatar, nick, podgląd ostatniej wiadomości, godzina, kropka nieprzeczytanych. **Sortowanie po `lastMessageAt` dzieje się w aplikacji**, nie w Firestore (`whereArrayContains` + `orderBy` na innym polu wymagałoby indeksu złożonego).
- **Wątek (`ChatThreadScreen`)** — trasa `chat/{uid}`, bańki, dzielniki dni, ✓ (wysłane) / ✓✓ (przeczytane), „pisze…", menu po długim naciśnięciu (kopiuj / czytaj / czytaj Pro / pokaż oryginał / cytuj / usuń u mnie).
- **⚠️ Tłumaczenie dzieje się u ODBIORCY (decyzja D1):** nadawca wysyła **oryginał** w `sourceLang` + własne tłumaczenie jako podpowiedź (`senderTranslation` — fallback dla odbiorców bez pobranego modelu). Odbiorca tłumaczy lokalnie Hy-MT2 na swój `speakLangSource` i **zapisuje wynik w Room** (`chat_translations`), żeby nie mielić modelu przy każdej rekompozycji. Przełącznik „pokaż oryginał" na każdej bańce + „Przetłumacz" po zmianie języka.
- **Kolejka offline (`chat_outbox`):** wysyłka jest natychmiastowa — wiersz ląduje w Room (bańka „wysyłanie"), a `ConnectivityObserver` wyzwala wysyłkę. **Id dokumentu w Firestore = `clientMsgId`** (UUID sprzed wywołania sieci), więc ponowienie **nie tworzy duplikatu** (`set()` na tym samym id jest no-op).
- **Potwierdzenia odczytu i „pisze…" żyją w subkolekcjach** `chats/{chatId}/readReceipts/{uid}` i `chats/{chatId}/typing/{uid}` — jeden dokument na uczestnika, reguła „możesz pisać tylko swój dokument". Dzięki temu `messages` pozostają **append-only**. Kropka nieprzeczytanych liczy się z lokalnej tabeli `chat_reads` (zero odczytów z chmury).
- Paginacja: live listener na 50 najnowszych, starsze strony doczytywane `startAfter`.
- **Wyszukiwanie ludzi idzie przez `usersPublic/{uid}`** — `users/{uid}` jest czytelne tylko dla właściciela, więc odpytywanie go z cudzego konta kończy się `PERMISSION_DENIED`. `AuthRepository` utrzymuje wizytówkę (nick, e-mail, avatar, języki + zlowercasowane `searchNick`/`searchEmail`), a `ensureProfile()` odnawia ją przy logowaniu.
- **Znajomości są symetryczne:** `friendships` ma `members: [uidA, uidB]`, zapytanie `whereArrayContains("members", uid)`; jeden listener zasila `watchAccepted` / `watchIncoming` / `watchOutgoing`.
- **Język docelowy tłumaczenia** = `ChatThreadViewModel.translationLang` = `langOverride` z karty kontaktu albo `speakLangSource` z profilu. Zmiana czyści mapę tłumaczeń w pamięci, ale **nie rusza cache w Room** (kluczowany językiem).
- **⚠️ `chats/{chatId}` musi istnieć i mieć `members` ZANIM poleci wiadomość** — reguły dla `messages` robią `get(/chats/$(chatId)).data.members`. Dlatego `sendMessage()` najpierw upsertuje dokument czatu.
- **Karta kontaktu (`contact/{uid}`)** — alias (tylko u mnie), język tłumaczenia, przypięcie, wyciszenie, blokada, notatka. Dane w **`users/{uid}/contacts/{otherUid}`** (pod MOIM dokumentem — nadany alias nie może pojawić się na cudzym telefonie), **Firestore, nie Room** (Firestore ma własny cache offline + ustawienia idą za kontem).
  - `langOverride` = język, NA KTÓRY tłumaczone są **przychodzące** wiadomości. Celowo nie zmienia `sourceLang` wychodzących — to pole musi opisywać to, co faktycznie napisałem.
  - **„Zablokuj" i „wycisz" są dziś lokalne** (brak Cloud Functions do egzekucji). **„Usuń rozmowę" = ukrycie lokalne** (`chat_hidden` w Room, migracja v6→v7) — wiadomości w Firestore są append-only.
- **Media:** załączniki w Firebase Storage pod `chat_attachments/{chatId}/{msgId}` (reguły: tylko członkowie czatu, tylko nowe obiekty, `image/*` + `audio/*` do 25 MB). Zdjęcie → upload + opcjonalny OCR u nadawcy (`ocrText` w dokumencie), miniatura przez Coil, podgląd pełnoekranowy. **Głosówka = transkrypcja na żywo** przez `SpeechRecognizer` (`ChatMessage.transcript`), nie plik `.m4a` — `SpeechRecognizer` nie przyjmuje nagranego pliku i nie dzieli mikrofonu z `MediaRecorder`.
- **Wyszukiwanie w wiadomościach:** Firestore nie ma full-text search — tylko zakres po prefiksie (`>= q`, `< q + "\uF8FF"`), więc „kot" nie znajdzie „Ala ma kota" (ograniczenie napisane wprost w UI). `searchText` zapisuje **wyłącznie Cloud Function** (reguły zabraniają klientowi) — inaczej klient mógłby zaindeksować coś innego niż wysłał. **Normalizacja musi być identyczna po obu stronach** (`functions/src/searchIndex.ts` ↔ `data/MessageSearch.kt`): NFD → zdjęcie `\p{M}` → lowercase → trim → 2000 znaków. Rozjazd objawia się **ciszą**: zero wyników, nic w logach. Zapytanie idzie **per czat**, nie jako collection group (group query przemiatałoby całą bazę, a reguły nie potrafią go ograniczyć).

### 4. Kontakty i Znajomi (Contacts)

- **Cztery zakładki:** Znajomi / Zaproszenia / Z telefonu / Zewnętrzne. Gdy pole wyszukiwania jest niepuste, zakładki znikają i pokazuje się `CombinedSearch` (znajomi + książka + zewnętrzni naraz).
- **Znajomi z książki telefonicznej:** `PhoneContactsImporter` (`ContactsContract`) — imiona i numery, deduplikacja, odczyt na `Dispatchers.IO`. **Dane zostają na urządzeniu.**
- **Prominent disclosure:** `ContactsPermissionScreen` wyjaśnia, co i po co czytamy, **zanim** system zapyta o `READ_CONTACTS` (wymóg Google Play); link do polityki + `privacy@verbigem.com`.
- **Kanały wychodzące (`data/OutboundChannel.kt`)** — Verbigem tłumaczy, potem **przekazuje**; niczego nie wysyłamy sami, bo nie wiedzielibyśmy, czy dotarło.
  | Kanał | Mechanizm | Ograniczenie |
  |---|---|---|
  | WhatsApp | `ACTION_VIEW` → `wa.me/<numer>?text=…`, paczka `com.whatsapp` → `w4b` → bez paczki | Nie wiemy, czy numer jest na WA |
  | SMS | `ACTION_SENDTO` → `smsto:` + `sms_body` | **Celowo nie `SmsManager`** — `SEND_SMS` to wniosek do Play i ryzyko odrzucenia |
  | E-mail | `ACTION_SENDTO` → `mailto:` | Wymaga adresu |
  | Telegram | `t.me/share/url?url=…&text=…` | Otwiera wybór rozmowy, nie konkretną osobę |
  | Inne | systemowy `ACTION_SEND` | Signal, Messenger itd. |
- ☠️ **Plan §5.4 mylił się co do Telegrama.** Zakładał `t.me/<username>` i schowek, bo „URL Telegrama nie potrafi wkleić tekstu". Zwykły nie potrafi, ale `t.me/share/url` **tak** — i to z jednoczesnym linkiem.
- **`inviteContact` wołane po `channel.handOff`**, tylko gdy zwróci `true` i kanał nie jest `SystemShareChannel` — anulowanie arkusza udostępniania nie oznacza kontaktu fałszywie jako zaproszonego.
- **Wątek jednokierunkowy (`ExternalThreadScreen`)** — dla kogoś bez konta. Room `version = 8`: `external_contacts` (klucz `phone`), `external_outbox`. **Decyzje, których nie wolno zmienić niechcący:**
  - **Nazwa kanału w historii to `id`, nie tekst** (`whatsapp`/`sms`/`email`/`telegram`/`system`) — etykietę rozwiązuje `OutboundChannels.labelResFor(id)` przy wyświetlaniu, więc zmiana języka nie psuje starych wierszy.
  - **`status` to zawsze `handed_off`** — wiersz zapisujemy **po** udanym hand-off, nigdy przed. To roszczenie, że coś wyszło z telefonu.
  - **Język docelowy wybiera się ręcznie** — zewnętrzny kontakt nie ma profilu; ekran blokuje tłumaczenie, póki `lang` puste (`external_pick_lang_first`). Cicho przetłumaczone w złym języku i wysłane to błąd, którego nie da się cofnąć.
  - **Wejście zapisuje kontakt w Room przed nawigacją** (`ContactsViewModel.rememberExternal` jest `suspend`), inaczej wątek zastałby pusty ekran.
  - **Telefon w trasie jest kodowany** (`Uri.encode`/`Uri.decode`) — `+` w surowym segmencie ścieżki zachowuje się różnie między wersjami Androida.
  - Historia przycinana do 90 dni (`pruneHistory`).
- **Import `.vcf` (`data/VcfImporter.kt`)** — własny parser, zero zależności. Wyciąga `FN`/`N`, `TEL`, `EMAIL`; obsługuje wiele kart, `VERSION:2.1`/`3.0`, zwijanie linii. Celowo ignoruje `PHOTO`, grupy, adresy, wiele numerów. Nie rzuca na uszkodzonych kartach. Czytany przez `contentResolver` (`OpenDocument`, `text/vcard`), więc nie potrzebuje `READ_CONTACTS`.
- **Zapraszanie:** systemowy share sheet z `https://mini.verbigem.com/app?inv=<uid>`.
- **Możesz znać (3.9):** sekcja na szczycie zakładki Znajomi, kandydatów liczy Cloud Function `suggestFriends` (`functions/src/peopleMayKnow.ts`). Musi być po stronie serwera — klient czyta tylko własne `friendships` (reguły oparte na `members`), więc nie widzi znajomych swoich znajomych. Algorytm: przejście grafu ograniczone do `MAX_FRIENDS_WALKED = 100`, `mutualCount` malejąco, `MAX_SUGGESTIONS = 20`, brak nowego indeksu złożonego. Klient: `PeopleMayKnowRepository` — powierzchnia niekrytyczna, każdy błąd (offline, rate-limit, funkcja nie wdrożona) daje pustą listę i UI chowa sekcję.

### 5. OCR ze zdjęcia/aparatu (Camera OCR)

- **Aparat = PEŁNA ROZDZIELCZOŚĆ** — `ActivityResultContracts.TakePicture()` + `FileProvider` (`${packageName}.fileprovider`, `cache-path path="."`, katalog `cacheDir/ocr_camera/`), potem ten sam `processImageUri` co galeria. **NIE używamy `TakePicturePreview()`** — zwraca thumbnail ~160px, który psuje czytelność OCR.
- `loadBitmap(uri)` czyta **EXIF orientation** i obraca bitmapę do pionu (aparat zapisuje obrócone; galeria auto-obraca, `BitmapFactory` nie).
- OCR: **Google ML Kit Text Recognition** + tłumaczenie Hy-MT2.
- **Crop przed odczytem:** ramka to **ręcznie napisany overlay** (`ui/screens/ocr/CropOverlay.kt`), NIE biblioteka (`cropview/` usunięte — gotowe biblioteki nie działały w tym układzie). Pojedynczy `Canvas` + **jeden** `pointerInput(Unit)`; rozciąganie 4 rogów (hit-radius 34dp) z nieruchomym rogiem przeciwnym; `awaitEachGesture` nie konsumuje gestu, który nie trafił uchwytu, więc strona się przewija. Współrzędne znormalizowane 0f..1f w `OcrViewModel.cropRectFlow`, domyślnie 0.1–0.9.
- ⚠️ **Zachowanie gestów weryfikuje się TYLKO na telefonie po zainstalowaniu APK** — build to nie to samo co działający UX.
- **OCR Pro (💎)** — przycisk obok Aparat/Galeria, dla free wyszarzony z tooltipem `ocr_pro_coming_soon`. Komponent `ProFeatureButton` (współdzielony z głośnikiem Pro).
- **Streaming:** `OcrViewModel.translateText()` woła `translateSegmented(..., onPartial = { ... })` — wypisuje wynik przyrostowo, tak jak Translator.
- **Własna historia OCR** — osobna tabela `ocr_history` i osobna kolekcja Firestore `users/{uid}/ocr_history/{syncId}`; te same zasady last-write-wins + tombstone, ale listy nigdy się nie mieszają. `SyncManager.syncCollection(...)` wołany dla `"history"` i `"ocr_history"`. Karty mają pełen zestaw akcji jak w Translatorze.
- **Ekran OCR nadal pokazuje BottomNav** (jest w `AppNavigation.showBottomNav`), ale **ikona OCR NIE jest w pasku** — dolny pasek ma **zawsze MAX 5 ikon** (Translator, Rozmowa, Czat, Kontakty, Profil). Wcześniej (v41) OCR dostał szóstą pozycję, ale to zapychało pasek i nie skracało drogi do OCR — wejście do niego i tak jest jednym tapem z Tłumacza (ikona aparatu w `HelpFramedIconButton`). Patrz sekcja *„Dolny pasek — zasady"*.
- ⚠️ **Znany brak, wciąż otwarty (§5.4):** błąd wysyłki zdjęcia / głosówki **nie jest obsługiwany**. `ChatThreadViewModel.sendImage()` i `ChatThreadViewModel.sendVoice()` mają `// TODO 5.4: obsługa błędu (retry) — na razie tylko log.` (ok. linii 391 i 469). Nie ma ani ponawiania, ani komunikatu dla użytkownika — nieudana wysyłka znika bez śladu w logcat. Do domknięcia przed premierą.

### 6. Kody QR

Jeden surowy link profilowy: `https://mini.verbigem.com/u/<uid>` (`usersPublic` jest publiczne, więc podpisany token byłby nadmiarowy). Jeden parser `ProfileLinks.uidFromUrl` obsługuje skaner i App Links — zmiana schematu nie rozjeżdża się między kodem generującym a czytającym.

- **Mój kod QR** — `MyQrScreen` + ZXing `core` 3.5.3 (`data/QRBitmap.kt`), trasa `Screen.MyQr`.
- **Skaner** — `ScanScreen` na GMS Code Scanner (`play-services-code-scanner` 18.3.0). Obcy link → komunikat „to nie kod Verbigem", nie otwieramy obcych stron. Trasa `Screen.Scan`.
- **App Links** — `intent-filter` VIEW z `autoVerify="true"`. Obsługa w `MainActivity.handleDeepLink` + `AppNavigation.openProfileUid`.
  > ⚠️ **App Links są dziś NIESKONFIGURUROWANE (stan na v1.0.39).** Pliku `assetlinks.json`
  > **nie ma** w repo — brak `mini/public/.well-known/`, brak `mini/dist/.well-known/`,
  > brak śladu w historii gita. Przez to `https://mini.verbigem.com/.well-known/assetlinks.json`
  > zwraca 404, weryfikacja `autoVerify` się nie udaje i link profilowy otwiera się
  > w przeglądarce zamiast w apce. **Skaner QR działa niezależnie** — dlatego objaw łatwo przeoczyć.
  >
  > Naprawa: utworzyć `mini/public/.well-known/assetlinks.json` z odciskami SHA256 i zdeployować
  > hosting miniego. Debug: `keytool -list -v -keystore ~/.android/debug.keystore -alias
  > androiddebugkey` (hasło `android`). **Release: SHA z Google Play Console → App signing**,
  > nie z lokalnego keystore'a (Play podpisuje APK własnym kluczem). Odciski muszą być też
  > wpisane w Firebase: Project settings → Your apps → SHA certificate fingerprints.

### 7. Profil i Design System

- Motywy: **Calm 🌊**, **Sharp ⚡**, **Playful 🎨**. Tryby: **Dzień ☀️** / **Noc 🌙**.
- Wybór języka interfejsu i domyślnej pary językowej. Wektorowe flagi SVG.
- Karta **Polityka prywatności** otwierająca `mini.verbigem.com/privacy/` w przeglądarce, w języku interfejsu.
- Karta **O aplikacji** (`R.string.about_label`) pod polityką prywatności: `Wersja <versionName> · build <versionCode>` z `BuildConfig` + link **Co nowego** otwierający `AppLinks.whatsNew(uiLang)` — czyli `https://mini.verbigem.com/android/changelog[-<lang>].html` (hostowany statycznie, ten sam skrypt `genChangelogHtml.mjs` co strona www; **NIE** `/whatsnew/` — Firebase catch-all rewrite serwowałby stronę webappy zamiast treści).

---

## ❓ Ikony: kliknięcie = akcja, długie kliknięcie = pomoc (od v41)

**Globalna reguła UI.** Każda ikona w aplikacji:

- **kliknięcie** → wykonuje zadanie, do którego ikona została stworzona,
- **długie kliknięcie** → otwiera okno z opisem: czym jest, co robi, jak używać.

Dodatkowo każdy ekran ma w nagłówku **logo świetlika + tytuł + przycisk „?"** po prawej
stronie — „?" otwiera od razu okno z opisem całej strony.

### Infrastruktura — `ui/components/HelpDialog.kt`

Jeden plik, z którego korzystają wszystkie ekrany. Nie wolno implementować pomocy
drugi raz obok — każda nowa ikona bierze stąd komponent.

| Element | Do czego |
|---|---|
| `HelpWindowState` + `rememberHelpWindowState()` | stan okna; **jedna instancja na ekran**, nie na ikonę |
| `HelpWindow(state)` | `Dialog` + `Surface`; wołaj **na końcu ekranu** (raz, obok stanu) |
| `Modifier.helpClickable(onClick, onLongClick)` | wrapper na `combinedClickable` (`@OptIn(ExperimentalFoundationApi)`) |
| `HelpIconButton(...)` | ikona-akcja: tap = akcja, długi tap = pomoc |
| `HelpFramedIconButton(icon, caption, ...)` | ikona **w ramce** + podpis 11.sp (mikrofon / aparat / aparat Pro) |
| `QuestionMarkButton(onClick)` | przycisk „?" |
| `ScreenHeader(title, helpState, helpTitle, helpText)` | logo + tytuł + opcjonalny `subtitle` + `trailing` + „?" |

### ⚠️ Pułapki, na których ta reguła się wykłada

1. **Nie dokładaj `helpClickable` do elementu, który już ma własny `clickable`.**
   `IconButton` / `Button` mają wewnętrzny `clickable` — wygrywa on i **long-press ginie
   po cichu** (zero błędu w logach). Dlatego `HelpIconButton` to `Box` + `helpClickable`,
   a nie `IconButton`.
2. **`stringResource` rozwiązuj u wywołującego — i NIGDY wewnątrz lambdy kliknięcia.**
   `HelpWindowState.show(title, text)` bierze gotowe `String`. `stringResource()` jest
   **`@Composable`**, a lambda `onClick` / `onLongClick` w `helpClickable` / `Button` /
   `QuestionMarkButton` **nie jest** kompozycyjna — wywołanie w jej wnętrzu to błąd
   kompilacji `e: @Composable invocations can only happen from the context of a @Composable
   function` (tak sypały się `ProfileScreen`, `ContactCardScreen`, `ContactsScreen` w v42).
   Wzorzec naprawy: wyciągnij `val t = stringResource(R.string.help_x)` w scope
   composable i dopiero wtedy `onClick = { help.show(t, ...) }`.
3. **Teksty pomocy to `R.string.help_*` — obowiązkowo we wszystkich 6 językach.**
   Reguła z sekcji *Wielojęzyczność* dotyczy ich tak samo jak etykiet. Klucz `help_close`
   (przycisk zamknięcia) i `help_open` (contentDescription „?") są współdzielone.
4. **Opisy silników pod ikonami zostały USUNIĘTE** — zastąpiły je okna pomocy
   (`EngineChoice.helpTitleResId` / `helpTextResId`). `descriptionResId` zostało w enumie,
   ale już się nie wyświetla; nie przywracaj go do UI.
5. **Menu dolne też ma okna pomocy** (`NavItem.helpResId`) i własny `rememberHelpWindowState()`
   wewnątrz `BottomNav` — pasek jest współdzielony z `AppNavigation`, więc nie może
   korzystać ze stanu ekranu.
6. **Wewnątrz `Dialog` `LocalContext` wraca do bazowej aktywności** (locale systemu, nie wybrany
   język interfejsu). Dlatego `stringResource(R.string.help_close)` w `HelpWindow` ignorował
   preferencję użytkownika i przycisk „Rozumiem" zawsze wyświetlał się po polsku — nawet przy
   angielskim UI. Naprawa: `HelpWindow` przechwytuje `LocalContext.current` PRZED otwarciem
   `Dialog{}` i odtwarza go przez `CompositionLocalProvider(LocalContext provides localizedContext)`
   wewnątrz. Nie używaj `stringResource` bezpośrednio w `Dialog` bez tego opakowania — dotyczy
   też własnych dialogów, nie tylko `HelpWindow`.
7. **`WindowInsets.isImeVisible` wymaga własnego importu.** To extension property:
   `import androidx.compose.foundation.layout.isImeVisible` (obok `...layout.WindowInsets`).
   Bez tego importu kompilator zgłasza `Unresolved reference 'isImeVisible'`, mimo że
   `WindowInsets` jest zaimportowany — patrz `ConversationScreen` (ma oba importy).
8. **Dosuwanie widoku nad klawiaturą (`BringIntoViewRequester`) to dwa opt-iny.**
   `LaunchedEffect(isImeVisible) { delay(250); requester.bringIntoView() }` +
   `.bringIntoViewRequester(requester)` wymagają
   `@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)` na composable.
   Sam `ExperimentalLayoutApi` nie wystarcza — to osobna adnotacja
   (`androidx.compose.foundation.ExperimentalFoundationApi`, a nie `...layout`).
   `delay(250)` jest celowy: klawiatura wjeżdża ~250 ms po fokusie, wcześniej
   `bringIntoView()` policzy zły offset.
9. **`BuildConfig.VERSION_NAME` / `VERSION_CODE` wymagają `buildFeatures { buildConfig = true }`.**
   AGP 8+ ma to domyślnie wyłączone; bez flagi każdy ekran odwołujący się do `BuildConfig`
   (karta „O aplikacji" w Profilu) sypie `Unresolved reference 'BuildConfig'`.
10. **`enabled` w `helpClickable` wyłącza tylko AKCJĘ, nigdy pomoc.**
    `combinedClickable(enabled = false)` wyłącza też `onLongClick` — więc na
    nieaktywnej kontrolce (pusty tekst → „Tłumacz", darmowe konto → silniki,
    brak zdjęcia → przyciski OCR) pomoc znikała bez śladu. Dlatego
    `helpClickable` zawsze przekazuje `enabled = true` i guarduje dopiero
    `onClick = { if (enabled) onClick() }`. Nie „naprawiaj" tego z powrotem.

### ➕ Jak dodać nowy silnik (checklista, sprawdzona na Pro 7B)

1. `ModelTier` — nowa pozycja z `fileName`, `approxBytes`, `minRamBytes`.
   **`requiresGpu = true`**, jeśli model bez GPU nie ma sensu (jak `PRO_7B`) —
   wtedy na urządzeniach bez backendu `blockReason()` zwraca `NO_GPU` i silnik znika
   z listy, zamiast kusić 2.9 GB pobierania, po którym użytkownik dostanie 1.6 tok/s.
2. `ModelDownloader.URL_*` + wpis w `urlFor()`.
3. `EngineChoice` — nowa pozycja: `id`, ikona (emoji), `captionResId`,
   `helpTitleResId`, `helpTextResId`, `isProOnly`, **`modelTier`**.
   Brak `modelTier` = silnik bez własnego GGUF (BOTH, ONLINE).
4. **5 stringów × 6 języków**: `engine_X_label`, `engine_X_desc`,
   `engine_caption_X`, `help_engine_X_title`, `help_engine_X`.
   Zweryfikuj skryptem porównującym zbiór `<string name=...>` w `values/` z każdym locale.
5. `TranslatorViewModel.translate()` — `when` jest wyczerpujący, kompilator wymusi
   obsługę nowej pozycji. Silniki jedno-modelowe idą jedną gałęzią przez `engine.modelTier`.
6. Gating: `computeAvailableEngines()` filtruje po `ModelDownloader.blockReason()` —
   nowy silnik z dużym `minRamBytes` **sam zniknie** na słabszych urządzeniach.
   Kolejność w `blockReason()` ma znaczenie: `NO_GPU` jest sprawdzany **przed**
   pamięcią i miejscem na dysku, żeby komunikat był właściwy („brak GPU", nie „brak RAM").

⚠️ **Pułapka overloadów:** `downloadModel(tier: ModelTier)` i
`downloadModel(isAccurate: Boolean = false)` — **tylko jeden może mieć wartość
domyślną**, inaczej wywołanie bez argumentów jest niejednoznaczne i nie kompiluje się.
To samo dotyczy `translate` / `translateSegmented` / `startModelDownload`.

### Konwencja podpisów

Podpisy ikon są zawsze **11.sp** — ta sama wielkość co w menu dolnym. Tam, gdzie
Milosz nie podał treści okna, treść jest wygenerowana i trzyma się schematu:
*czym jest → co robi → jak używać → co się dzieje z danymi*.

### 📱 Dolny pasek — zawsze MAX 5 ikon

`BottomNav` renderuje pasek współdzielony przez `AppNavigation` (`showBottomNav`).
**Twarda reguła: pasek ma dokładnie 5 pozycji** — Tłumacz, Rozmowa, Czat, Kontakty,
Profil. Nie dodawaj szóstej ikony „bo ekran X nie ma nawigacji".

- Ekran, który potrzebuje nawigacji, ale nie pasuje do paska, i tak pokazuje `BottomNav`
  (wystarczy, że jego `Screen.route` jest w `AppNavigation.showBottomNav`) — nawigacja
  wraca przez ikonę Tłumacza/Profilu, a wejście do ekranu jest jednym tapem z innego
  ekranu (wzorzec: OCR z Tłumacza przez `HelpFramedIconButton`).
- v41 dodało OCR jako szóstą pozycję. Cofnięte — pasek wizualnie się rozjeżdżał,
  a wejście do OCR nie było krótsze niż przez Tłumacza.

---

## 📋 Czat i Kontakty — dokument towarzyszący

Rozbudowa Czatu i Kontaktów jest rozpisana w **`chat_kontakty.md`** (katalog główny repo): diagnoza stanu, macierz możliwości technicznych, architektura docelowa, ryzyka. **Prace nad czatem i kontaktami zaczynamy od lektury tamtego pliku.**

Ustalenia, które wprost wynikają z tamtego planu i są już w kodzie:

- Tłumaczenie u odbiorcy (decyzja D1) + cache w `chat_translations`.
- **Nie da się zaimportować listy kontaktów z WhatsApp ani Telegrama** (brak API). Źródłem listy jest książka telefoniczna + opcjonalnie `.vcf`, a WhatsApp / SMS / e-mail są kanałami dostarczenia.
- **Weryfikacja numeru jest leniwa** — przy pierwszym wejściu w Czat lub Kontakty, nie przy rejestracji.
- Cloud Functions (matching + FCM) wymagają planu **Blaze** (wykupiony, decyzja D6).

---

## 🔒 Polityka prywatności (opublikowana)

**URL do zgłoszenia w Google Play:** `https://mini.verbigem.com/privacy/` · wersje: `/privacy/pl/`, `/en/`, `/de/`, `/es/`, `/zh/`, `/tr/` · kontakt: **privacy@verbigem.com**.

**Źródło treści:** `verbigem/mini/scripts/build_privacy.py` — jedyne źródło prawdy, generuje statyczne HTML do `public/privacy/` **i** `dist/privacy/`.

```bash
cd verbigem/mini && python scripts/build_privacy.py
cd verbigem/mini && firebase deploy --only hosting --project mini-verbigem
```

- **Dlaczego `public/` i `dist/` naraz:** `firebase deploy --only hosting` zastępuje hosting zawartością `dist/`; `public/` musi być, żeby treść przetrwała przyszły `npm run build` (Vite kopiuje `public/` → `dist/`, ale najpierw czyści `dist/`).
- **Struktura URL-i:** katalogi (`/privacy/pl/index.html`), **nie** płaskie pliki — `firebase.json` ma catch-all rewrite `** → /index.html`, więc brak pliku = strona webappy. `/privacy/pl` dostaje 301 → `/privacy/pl/`.
- **⚠️ Cache:** `/privacy/**` ma `public, max-age=3600, must-revalidate`. **Nigdy nie dawaj tam `immutable`** — Cloudflare zamroziłby politykę na rok (ta sama pułapka co przy `/android/**`). Styl jest **inlinowany** właśnie dlatego, że reguła `**/*.css` ma `immutable`.

| Miejsce | Plik | Co robi |
|---|---|---|
| Profil → karta „Polityka prywatności" | `ProfileScreen.kt` | otwiera `/privacy/<uiLang>/` w przeglądarce |
| Kontakty → prominent disclosure | `ContactsPermissionScreen.kt` | ekran wyjaśnienia **przed** systemowym dialogiem `READ_CONTACTS` (wymóg Play) |

URL-e buduje **`data/AppLinks.kt`** (jedno źródło prawdy): `privacyPolicy(uiLang)` i `privacyPolicyFor(context)`. Oba otwierają **przeglądarkę, nie WebView**.

**Zasada spójności:** treść disclosure (stringi `contacts_perm_*` × 6) musi zgadzać się z opublikowaną polityką. Zmiana polityki na stronie **nie wymaga** nowego APK; zmiana stringów — tak.

**`READ_CONTACTS`** jest w manifeście, ale aplikacja prosi o nie wyłącznie po ekranie wyjaśnienia. Numery nie opuszczają urządzenia — do chmury (Cloud Function `matchContacts`) idą wyłącznie skróty SHA-256 + HMAC.

---

## 🎨 Branding — ikona launchera i logo webapp

- **Ikona apki (adaptive icon):** `ic_launcher_background.xml` = `CalmDayBg` (#F7F5F1, krem z tła stron w motywie domyślnym — wcześniej #2C6B85 wyglądało jak „paskudne zielone tło"), `ic_launcher_foreground.xml` = `<inset>` 20% na `ic_launcher_firefly.png` (RGBA, przezroczyste tło). minSDK 26 → nie trzeba rasterowych mipmap.
  ⚠️ **Nie podmieniaj na nieprzezroczyste PNG** — `logov.png` (RGB, białe tło wypieczone) zostało celowo odrzucone; używaj **tylko** RGBA.
- **Gęstości launchera:** te same pliki `ic_launcher_firefly.png` są w `drawable-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/` w rozmiarach **108/162/216/324/432 px** (Android adaptive icon safe zone 108dp). Skalowane z `mini/public/logo-firefly.png` (724×724) filtrem **Lanczos** (`Pillow.Image.LANCZOS`, `optimize=True`). Źródło 724px jest małą gęstością dla drobnego grafu sieci neuronowej w logo — jeśli Milosz podsyła nowy plik źródłowy (np. **2048×2048 RGBA** z oryginalnego wektora), przebudować gęstości jednym skryptem w `app/scripts/genLauncherIcons.py` (do dopisania) i jakość skoczy bez zmian w kodzie.
- **Logo webapp (mini):** `verbigem/mini/public/logo-firefly.png` → `/logo-firefly.png`, wyświetlane przed `<h1>Mini Verbigem</h1>`. Vite kopiuje `public/` → `dist/` przy `npm run build`, więc zmiana pliku wymaga przebudowy webappy (zob. *Flow wydania*, krok 5).

---

## 🛠️ Architektura techniczna

```
app/src/main/
│   ├── cpp/
│   │   ├── CMakeLists.txt              # libverbigem_llama.so
│   │   ├── llama_jni.cpp               # Mostek JNI C++ do wnioskowania GGUF
│   │   └── llama.cpp/                  # Vendored llama.cpp (gitignored), commit f5e85d43 + patch STQ (PR #22836)
│   │       └── ggml/src/ggml-cpu/llamafile/sgemm.cpp  # fp16→fp32 fallback dla NDK 26
│   │   └── patches/                # stq1_0.patch — JEDYNA kopia kernela STQ1_0 w repo (406 linii)
├── java/com/verbigem/app/
│   ├── MainActivity.kt             # Entry point, Edge-to-Edge Compose, dialog auto-update, deep linki
│   ├── VerbigemApplication.kt      # Firebase + SyncManager + ConnectivityObserver + App Check + FCM
│   ├── ads/
│   │   └── AdsConsent.kt               # Zgody UMP (RODO/EOG) + inicjalizacja AdMob — kolejnosc wymuszona kodem
│   ├── data/
│   │   ├── ConnectivityObserver.kt  # callbackFlow na NetworkCallback — emituje isOnline
│   │   ├── local/                  # Room v9: History, OcrHistory, TtsConfig, PendingDelete,
│   │   │                           #   ChatRoomEntities (chat_translations, chat_outbox, chat_reads,
│   │   │                           #   chat_deleted_messages, chat_hidden), external_* + DataStore
│   │   ├── model/                  # LangCode, UserProfile, PublicProfile, ChatMessage, ChatSummary,
│   │   │                           #   Friendship, EngineChoice, TranslationHistory, TtsConfig,
│   │   │                           #   ModelTier (FAST/ACCURATE/PRO_7B), ModelTierBlockReason
│   │   ├── AppLinks.kt             # Polityka prywatności, ProfileLinks, InviteLinks, openUrl(), shareText()
│   │   ├── PhoneContactsImporter.kt, PhoneNumbers.kt, VcfImporter.kt, OutboundChannel.kt,
│   │   ├── MessageSearch.kt, QRBitmap.kt
│   │   └── repository/             # Auth, Chat, History, OcrHistory, ProTts, PeopleMayKnow,
│   │                               #   ExternalThread, Storage + SyncManager, TtsConfigSync
│   ├── engine/
│   │   ├── HyMt2NativeEngine.kt    # Natywny silnik Hy-MT2: prompt, translateSegmented, sanityzacja
│   │   ├── ModelDownloader.kt      # WZNAWIALNE pobieranie GGUF (Range) + gating urządzenia
│   │   ├── CpuTopology.kt          # inferenceThreads(): wszystkie rdzenie, kapa 8 (zmierzone)
│   │   ├── GlossaryPrompt.kt       # terminologia → blok Tencenta (filtr + kapy, n_ctx=1024)
│   │   ├── GpuAcceleration.kt      # sonda GPU: GGML_BACKENDS x możliwości urządzenia
│   │   ├── SpeechManager.kt        # Android STT (SpeechRecognizer) + TTS
│   │   ├── OcrManager.kt           # Google ML Kit Text Recognition
│   │   ├── OnlineApiEngine.kt      # Tłumaczenie online: 3 modele kuratorskie (proxy OpenRouter) + :free z własnym kluczem
│   │   ├── ProTtsEngine.kt         # Płatne TTS przez OpenRouter (/audio/speech)
│   │   └── UpdateManager.kt        # Auto-update: Hosting → OkHttp → instalacja APK
│   ├── jni/LlamaNativeBridge.kt    # JNI external fun
│   └── ui/
│       ├── components/             # FlagIcon, LangSelect, BottomNav, EnginePicker, DownloadDialog,
│       │                           #   AdBannerView (AdMob: AndroidView + AdView.loadAd, placeholder
│       │                           #   dopoki AdsConsent.adsReady), ProFeatureButton (Pro + grayscale + tooltip),
│       │                           #   HelpDialog (HelpWindow/helpClickable/HelpIconButton/
│       │                           #   HelpFramedIconButton/QuestionMarkButton/ScreenHeader)
│       ├── navigation/             # AppNavigation, Screen
│       ├── screens/                # Translator, Conversation, ChatList, ChatThread, ContactCard,
│       │                           #   Contacts (+ContactsPermission), Ocr (+CropOverlay), ExternalThread,
│       │                           #   Profile, MyQr, Scan, Login, Glossary (słownik, Room v9)
│       └── theme/                  # Color, Theme, Type (Calm/Sharp/Playful × Day/Night)
```

**Migracje Room (skrót):** v2→v3 `pending_deletes` (kolejka tombstone'ów) · v3→v4 `ocr_history` (+ kolumna `collection` w `PendingDeleteEntity`) · v5→v6 cztery tabele czatu · v6→v7 `chat_hidden` · v7→v8 `external_contacts` + `external_outbox` · **v8→v9 `glossary`** (słownik użytkownika, patrz niżej).

⚠️ **Room WALIDUJE schemat PO migracji — `CREATE TABLE` z głowy to rosyjska ruletka.** Po każdej migracji Room odpala `onValidateSchema` i rzuca `IllegalStateException: Migration didn't properly handle: <tabela>` przy każdej różnicy względem encji. **Autorytatywne DDL jest w `app/build/generated/ksp/debug/java/.../AppDatabase_Impl.java` — skopiuj je stamtąd.** Room porównuje `name` / `notNull` / `affinity` / `primaryKeyPosition` oraz `defaultValue`, ale `defaultValue` **tylko wtedy, gdy encja sama je deklaruje** (`@ColumnInfo(defaultValue=…)`). Zwykłe `= ""` w Kotlinie to domyślna wartość konstruktora, nie kolumny — generowany `TableInfo.Column` dostaje `null`, więc klauzula `DEFAULT` w migracji jest tolerowana (tak żyje `MIGRATION_7_8`). Nie ma `fallbackToDestructiveMigration` i nie wolno go dodać — to kasuje dane użytkowników.

---

## 🔤 Jak działa tłumaczenie

Tłumaczenie jest **w 100% lokalne** (offline, bez serwera) — tekst trafia do modelu **Hy-MT2-1.8B** uruchomionego natywnie przez vendored **llama.cpp** (kernel `STQ1_0`, PR #22836) przez mostek **C++/JNI**.

```
EditText (Compose) → TranslatorViewModel.translate()   [Dispatchers.Default]
   → HyMt2NativeEngine.translate(text, from, to, tier: ModelTier, onPartial)
        1. ensureModelLoaded()  — ładuje .gguf z filesDir/models/ (mmap)
        2. buildPrompt()        — "Translate the following segment into <TARGET>,
                                  without additional explanation：<TEXT>"
        3. generateNativeStreaming()  — JNI (C++)
   → llama_jni.cpp: llama_chat_apply_template("hunyuan-dense") → llama_tokenize
        → llama_decode (pętla autoregresyjna)
        → po każdym tokenie llama_token_to_piece → bufor wyrazów
        → gdy nowy token zaczyna się od spacji → flush wyrazu przez onToken(piece)
   → TokenStreamCallback.onToken → accumulated.append; onPartial(acc)
   → TranslatorViewModel → _primaryResult / _secondaryResult (StateFlow)
   → TranslatorScreen — Text() z nowym wyrazem (recompose, bez migotania)
```

**Kluczowe fakty:**

- **Modele (`ModelTier`):** `FAST` = `Hy-MT2-1.8B-1.25Bit.gguf` (~440 MB) · `ACCURATE` = `Hy-MT2-1.8B-Q4_K_M.gguf` (~1.1 GB) · `PRO_7B` = `Hy-MT2-7B-UD-Q2_K_XL.gguf` (~2.91 GB, Pro). Pobierane z Hugging Face przez `ModelDownloader`.
- **Wznawialne pobieranie (od v43):** `ModelDownloader` wysyła `Range: bytes=N-` i dopisuje do `<name>.gguf.tmp`. Obsługuje 206 (wznowienie), 200 (serwer zignorował Range → restart od zera) i 416 (mamy już całość → rename). **Po błędzie `.tmp` jest celowo NIE usuwane** — następna próba wznawia zamiast ściągać 2.9 GB od nowa. `cancelPartial()` usuwa ręcznie.
- **Gating urządzenia:** `ModelDownloader.blockReason()` sprawdza `ActivityManager.MemoryInfo.totalMem` vs `ModelTier.minRamBytes` i `StatFs(filesDir).availableBytes` vs `approxBytes * 1.2` → `LOW_RAM` / `NO_SPACE`. ⚠️ `totalMem` zwraca RAM *użyteczny*, zawsze niższy niż marketingowy — telefon „6 GB" raportuje ~5.6 GB, dlatego próg PRO_7B to 5 GB, nie 6.
- **Wątki = WSZYSTKIE rdzenie, kapa 8 (`CpuTopology`) — ZMIERZONE, nie zgadywane.** ⚠️ Ta reguła była wcześniej odwrotna („tylko duże rdzenie") i pomiar ją obalił. Pełna tabela w `CpuTopology.kt` i `docs/SILNIK_PRO_RESEARCH.md` §5. Skrót (llama-bench, `-p 64 -n 64`, Snapdragon 685): prompt-processing rośnie z wątkami zawsze (+27% od 4→8 na 1.8B), decode na małym modelu *spada* (7.99→7.17) ale na 7B *rośnie*. Tłumaczenie jest **prompt-ciężkie** (szablon + tekst = 30–100 tokenów wejścia), więc więcej wątków wygrywa: zdanie 30/10 na 1.25-bit to 3.68 s @8 wątków vs 4.15 s @4. **Nie wracaj do „tylko duże rdzenie" — to była ~11% regresja.**
- ⚠️ **KV cache: f16, nie q8_0.** Zmierzone: q8_0 daje 6.60 vs 7.17 tok/s na 1.25-bit (narzut dekwantyzacji > oszczędność przepustowości przy krótkim kontekście, który tu mamy).
- ⚠️ **Sampler: greedy — celowo, temat ZAMKNIĘTY (§10 + §10b).** Tencent zaleca `temp 0.7 / top_p 0.6 / top_k 20 / rep 1.05`. Zmierzone na urządzeniu, 6 zdań × 2 tiery, sampler zalecany **po 3 razy**: na Q4_K_M bez różnicy; na 1.25-bit próbkowanie dodaje wariancję **w obie strony** — jedno losowanie lepsze, jedno takie samo, jedno ewidentna bzdura („Rada **przetoczyła** raport", „Serwer jest **niefunkcyjny**"). ⚠️ Wcześniejszy sygnał „sampling naprawia 2/4" był **szumem pojedynczego losowania** — nie wnioskuj o samplerze z jednej próbki. Greedy jest dodatkowo **w 100% deterministyczny** (dwa przebiegi = 24/24 identycznych wyników). Nie zmieniaj tego bez nowego, wielokrotnego pomiaru.
- ⚠️ **Prompt nie jest dźwignią jakości — nie rób churnu (§10b).** Obecne sformułowanie („Translate the following segment into X, without additional explanation：" + pełnoszerokości dwukropek) i oficjalne Tencenta dają w praktyce **identyczne** tłumaczenia (4/6 i 3/6 zdań co do znaku); w jednym miejscu oficjalny jest gorszy (literówka „Prosim" zamiast „Prosimy"). Dźwignią jest **tier**, nie prompt.
- ⚠️ **Pułapka narzędziowa: `llama-completion -p` NIE symuluje aplikacji.** Wkłada tekst do slotu **systemowego** (`<bos>{prompt}<｜hy_User｜>`), a aplikacja do roli **user** (`<｜hy_User｜>{prompt}<｜hy_Assistant｜>`). Wyniki z `llama-completion` bywają kompletnie inne — potrafił wygenerować `Razorowowowow…`, czego aplikacja nie robi. Do wiernych testów jest `_probe.cpp` (replikuje ścieżkę JNI: `hunyuan-dense`, rola user, `n_ctx=1024`, `n_batch=512`).
- 📖 **Glosariusz użytkownika (od v9 bazy) — jedyna działająca funkcja „Pro" bez GPU.** Tabela `glossary` + `GlossaryRepository` (singleton z cache w pamięci) + `GlossaryPrompt`. Wpisy są kluczowane **parą języków** (`sourceLang`→`targetLang`), bo „board" to słowo angielskie, a słownik DE→PL to inna lista niż EN→PL. `buildPrompt()` dokłada blok Tencenta **tylko gdy któryś termin faktycznie występuje w tekście** — w przeciwnym razie prompt jest identyczny jak wcześniej (zero kosztu w typowym przypadku).
  - **Dopasowanie:** `\bTERMIN\b` dla terminów ASCII (żeby `art` nie odpaliło się na `party`), substring dla tych zaczynających/kończących się znakiem nie-ASCII (`\b`/`\w` są w Javie ASCII-only, więc `sądowa` nie da się obstawić granicami). Flaga `caseSensitive` istnieje dla akronimów — `IT` nie może się odpalić na angielskie „it".
  - **Kapy:** `MAX_TERMS = 12`, `MAX_BLOCK_CHARS = 600` (n_ctx to 1024). Po przekroczeniu nadmiarowe wpisy są **odrzucane**, nie ucinane — urwana linia byłaby gorsza niż jej brak.
  - **Cache jest konieczny:** `translateSegmented` woła `translate()` raz na ~400-znakowy segment; bez cache każdy segment robiłby odczyt z dysku wewnątrz pętli dekodowania.
  - **UI:** `GlossaryScreen` + `GlossaryViewModel`, trasa `Screen.Glossary`, wejście z Profilu (sekcja „Słownik"). Para języków wybierana dwoma `LangSelect`; przycisk „Dodaj" jest nieaktywny, gdy języki są te same albo któreś pole jest puste (`save()` i tak by odrzuciło).
  - **Nie jest gated na Pro** — w aplikacji nie ma jeszcze płatności, więc ekran Pro-only byłby niewidoczny dla wszystkich. Żeby zmienić: sprawdź `UserProfile.isPro` przy wejściu w Profilu (jeden punkt).
  - Zweryfikowane na Redmi Note 13: `user_version = 9`, tabela + oba indeksy, ekran renderuje się bez crashu.
  - ⚠️ **`adb shell input tap` jest na tym telefonie ZABLOKOWANE** (`INJECT_EVENTS`), więc ekranu nie da się przetestować automatycznie. Żeby w ogóle go uruchomić: tymczasowo podstaw `startDestination = Screen.Glossary.route`, zbuduj, `uiautomator dump`, potem **przywróć** — inaczej telefon zostaje na ekranie słownika.
- 💡 **Zmierzone: glosariusz działa, styl nie.** Instrukcja terminologiczna Tencent (`Reference the following translations: X translates to Y`) jest respektowana **na obu tierach**, też na 1.25-bit — jedyna realna funkcja „Pro" bez GPU i bez pobierania. Instrukcja stylu (`...must strictly conform to [formal]`) **nie działa** (na 1.25-bit formal i informal dały identyczny wynik). `docs/SILNIK_PRO_RESEARCH.md` §11.
- **GPU: wykrywanie w runtime (`GpuAcceleration`), nie hardcodowanie.** Decyzja = iloczyn dwóch rzeczy: (1) co jest wkompilowane — `BuildConfig.GGML_BACKENDS` w `app/build.gradle.kts` (dziś `"CPU"`), (2) co to urządzenie potrafi. `gpuLayers()` zwraca 99 albo 0. Aplikacja jest publiczna: stary telefon to poprawny przypadek, nie błąd.
- ⚠️ **OpenCL: `dlopen` to ZA MAŁO — biała lista SoC (`GGML_OPENCL_ALLOWED_SOCS`, domyślnie PUSTA = wyłączone).** Zmierzone na Adreno 610: `libOpenCL.so` ładuje się bezbłędnie, ale (a) ggml zbudowany na OpenCL 3.0 **twardo abortuje proces** na urządzeniach OpenCL 2.0 (platforma raportuje 3.0, device 2.0 → `GGML_ASSERT` w `ggml-opencl.cpp:212`), (b) zbudowany na 2.0 jest **4× wolniejszy od CPU** (decode 1.22 vs 5.17 tok/s przy `-ngl 1`), (c) przy pełnym offloadzie segfault. Szczegóły: `docs/SILNIK_PRO_RESEARCH.md` §7b. **Nie wpisuj SoC na listę bez prawdziwego pomiaru.**
- 🧠 **Pro 7B wymaga GPU (`ModelTier.PRO_7B.requiresGpu = true`).** Zmierzone 1.64 tok/s na CPU (Snapdragon 685) = ~37 s za zdanie. `blockReason()` zwraca `NO_GPU` i silnik znika z listy, zamiast sprzedawać komuś 2.9 GB pobierania, po którym dostanie 1.6 tok/s. Włącza się **sam**, gdy `GGML_BACKENDS` dostanie `OPENCL`/`VULKAN` i urządzenie to udźwignie.
- **Pobieranie > 1 GB przez sieć komórkową:** ostrzeżenie (nie blokada) + przycisk „Pobierz mimo to" (`ModelDownloadState.MeteredWarning`). Blokada na sztywno byłaby zła przy nielimitowanych taryfach. ⚠️ `onConfirmMetered` **musi** przekazać `allowMetered = true`, inaczej użytkownik kręci się w kółko.
- ⚠️ **Szablon czatu `hunyuan-dense` jest obowiązkowy.** Surowy prompt bez niego daje bełkot — model oczekuje tokenów `<|hy_User|>` / `<|hy_Assistant|>`.
- **Streaming wyrazami:** natywna pętla wysyła do UI **ukończone wyrazy**, nie surowe subwordy (`trans`+`lat`+`ion` zostają w buforze do granicy słowa), więc UI nie migocze literami.
- ⚠️ **Tłumaczenie segmentami (`translateSegmented`) — Hy-MT2 to model segmentowy:** dostaje cały akapit i tłumaczy tylko PIERWSZE zdanie, po czym daje EOS. Żeby przetłumaczyć CAŁY tekst, wejście jest dzielone na segmenty (~400 znaków) na granicach zdań (`.!?\n`), nadmiarowo długie zdanie łamane dalej po słowach, każdy segment tłumaczony osobnym wywołaniem, wynik składany `\n\n`. Dla krótkiego zdania zachowuje się jak `translate()`. `TranslatorViewModel` i `OcrViewModel` wołają `translateSegmented` dla silników lokalnych (Fast/Accurate/Both); online (DeepSeek) idzie bez zmian. Szczegóły: skill `hy-mt2-offline-translation` → `references/segmented-translation.md` (**przeczytaj przed modyfikacją silnika tłumaczenia**).
- **Sanityzacja:** `sanitizeTranslation()` usuwa znaczniki `<think>`, wiodące cudzysłowy, etykiety „Tłumaczenie:". **NIE** ucina do pierwszego akapitu — cała obsługa wielu zdań jest w `translateSegmented`.
- **Języki:** model wymaga **angielskich nazw** w prompcie (`LangCode.englishName`), nie kodów ISO.
- **Wydajność:** native lib budowany jako Release (`-DCMAKE_BUILD_TYPE=Release`) + KleidiAI; ~3–4 tok/s na słabszym ARM. Decode na CPU jest **ograniczony przepustowością pamięci** — czas na token ≈ rozmiar wag / przepustowość RAM, więc drabinka rozmiarów GGUF jest jednocześnie drabinką prędkości. Szczegółowa analiza i drabinka 7B: **`docs/SILNIK_PRO_RESEARCH.md`**.
- ⚠️ **KleidiAI nie pomaga na starych rdzeniach:** szybkie ścieżki wymagają `dotprod` / `i8mm` (ARMv8.2+). Cortex-A73 (Snapdragon 685) to ARMv8.0 — llama.cpp spada na generyczny NEON.

---

## 💎 Czytaj Pro (płatne TTS przez OpenRouter)

Dostępne tylko dla użytkowników Pro (`UserProfile.isPro`). Zamiast darmowego `TextToSpeech` (offline) używa `https://openrouter.ai/api/v1/audio/speech`.

**Widoczność dla free:** ikona głośnika Pro (💎) jest **zawsze wyświetlana** — wyszarzona (kolor `muted`), po kliknięciu daje **tooltip** (`pro_feature_tooltip`, 6 języków) zamiast odtwarzania. Dla Pro jest aktywna (kolor `accent`) i odpala `ProTtsEngine`. Komponent: `ui/components/ProFeatureButton.kt` (`TooltipBox` + `PlainTooltip`, `ExperimentalMaterial3Api`) — współdzielony z OCR Pro.

- `ProTtsEngine.speak(text, lang, config)` — POST `model`, `input`, `voice`, `response_format=mp3`; odtwarza mp3 przez `MediaPlayer`.
- `TtsConfig` — `apiKey`, `defaultModelId`, `chineseModelId`, `defaultVoice`, `chineseVoice`, `updatedAt`; `modelIdFor(lang)` / `voiceFor(lang)` wybierają osobny model dla `LangCode.ZH`.
- `ProTtsRepository` + `TtsConfigDao`/`TtsConfigEntity` — cache w Room (`tts_config`, 1 wiersz, id=1). `TtsConfigSync` przy starcie pobiera `app_config/tts` i nadpisuje lokalne, gdy `remote.updatedAt >= local.updatedAt`.
- **Modele:** domyślny `google/gemini-3.1-flash-tts-preview` (70+ języków), chiński `fish-audio/s2.1-pro`. Odrzucone: `hexgrad/kokoro-82m` (brak PL/TR).
- **Konfiguracja:** `apiKey` wpisuje się ręcznie do Firestore (`app_config/tts`) do czasu powstania webappu admina. App nie ma UI do wpisywania klucza.

---

## ☁️ Tłumaczenie online (OpenRouter)

Silnik `ONLINE` w `EngineChoice` (ikona ☁️). Tłumaczy przez chmurę, gdy brak modelu
lokalnego lub dla lepszej jakości. Dwa niezależne źródła:

1. **3 modele kuratorskie (płatne, rozliczane z portfela)** — `OnlineModels.CURATED`
   w `data/model/OnlineModel.kt`: `deepseek/deepseek-v4-flash-latest` (domyślny,
   oznaczony „polecany"), `google/gemini-3.8-flash`, `anthropic/claude-opus-5`. Idą przez
   proxy Cloud Function **`deepseekProxy`** (trzyma klucz Verbigema, OpenRouter), które
   przekazuje wybrany `model` **1:1** do OpenRouter. ⚠️ Proxy **musi** czytać pole `model`
   — wcześniej hardkodowało `deepseek-chat` i ignorowało wybór (plus appka wysyła
   `fromLang`/`toLang`, a proxy czytało `from`/`to` → odrzucało każde żądanie). Poprawka w
   `mini/functions/index.js` (kierunek: OpenRouter, sekret `OPENROUTER_API_KEY`).
2. **Własny klucz użytkownika + modele `:free`** — w Profilu (karta „Własne API") wpisujesz
   klucz OpenRouter (`AppLinks.openRouterKeys()` → openrouter.ai/keys). Wtedy w liście modeli
   online pojawiają się **dynamicznie pobierane** modele `:free` (`OnlineApiEngine.fetchFreeModels`:
   GET `/api/v1/models`, filtruj `id` kończące się na `:free` + cena 0, sortuj wg
   `context_length`). Lista **NIE jest hardkodowana** — free modele zmieniają się codziennie.

**Gating portfelem (`walletCents`):** modele płatne są nieaktywne, gdy
`KEY_WALLET_CENTS <= 0` (mirror `UserProfile.walletCreditsCents` w `PreferencesManager`).
Wtedy płatna translacja online kończy się `R.string.online_no_credits`. Modele `:free`
(z własnym kluczem) działają **BEZ środków**. Silnik `ONLINE` w `EnginePicker` jest aktywny,
gdy `isPro || hasOwnKey`.

**Rozliczanie (proxy `deepseekProxy` / `visionProxy` w `mini/functions/index.js`):**
obie funkcje wołają OpenRouter i pobierają **rzeczywisty koszt z pola `usage.cost`** w
odpowiedzi, doliczając `MARGIN_PCT` (10%) marży — **bez żadnej tabeli cen** (działa przy
każdej zmianie ceny u dostawcy). `requireProUser` wymaga tylko `walletCreditsCents > 0`
(kredyty = „pro" de facto, bez osobnego `plan === 'pro'`). Wymagany sekret:
`OPENROUTER_API_KEY` (klucz Verbigema, trzymany w Secret Manager).

- `OnlineApiEngine.translate(text, from, to, model, apiKey?)` — `apiKey != null` → OpenRouter
  bezpośrednio (Bearer, własny klucz, `:free`); `apiKey == null` → proxy (kuratorskie).
- UI: `ProfileScreen` ma 3 nowe karty (Pobrane modele + usuwanie czerwonym koszem,
  Model online, Własne API z tutorialem), `OnlineModelRow` (wybór + odznaka „polecany"
  dla domyślnego), `AlertDialog` potwierdzający usunięcie modelu.
- Zmiana modelu: `ProfileViewModel.setOnlineModel(id)` → `KEY_ONLINE_MODEL`
  (domyślnie `OnlineModels.DEFAULT_ID`).
- **Doładowanie portfela w apce:** karta „Status konta" w `ProfileScreen` ma guzik
  **Doładuj portfel**, który otwiera dialog z 3 pakietami (300/500/1000 kredytów →
  `wallet3/5/10`). `ProfileViewModel.topUp(type)` woła Cloud Function **`createCheckout`**
  (Paddle, sandbox), tworzącą transakcję z `customData.uid` i zwracającą hosted
  `checkout.url`; apka otwiera go w przeglądarce. Po opłaceniu `paddleWebhook`
  (`transaction.completed`, `customData.type` zaczynające się na `wallet`) dopisuje
  `wallet.creditsCents` — portfel odświeża się na żywo (snapshota `users/{uid}`).
  ⚠️ `walletTopUp` to funkcja **admin-only** (ręczne dopisywanie) — NIE używana z apki.
- **Usuń reklamy (od v1.0.46):** obok „Doładuj portfel" jest guzik **Usuń reklamy** z 4
  pakietami `$1/$3/$5/$10 → 1/3/5/10 msc` (`noAds1/3/5/10`). To **przedpłata jednorazowa**
  (ceny Paddle bez `billingCycle`) — NIE abonament; ten sam `createCheckout`, a
  `paddleWebhook` przy `customData.type` zaczynającym się na `noads` ustawia
  `noAdsUntil = teraz + miesiące × 30 dni` (dolicza się do już wykupionego okresu).
  **Po zakupie konto przechodzi na PRO 💎** (webhook ustawia `plan: 'pro'`) — w karcie
  statusu widać wtedy **saldo portfela** (również **0.00**, gdy nie doładowany) oraz
  **licznik dni do wznowienia reklam** (`noads_resume_in`). Katalog LIVE: produkt
  `pro_01m2133ymye9s0dxhtgyh3tqma` + 4 ceny w `NOADS_PRICES` (`functions/index.js`
  w repo `mini`) — zob. `scripts/provision-no-ads-onetime-live.mjs`.

---

## 📢 Reklamy (AdMob — GMA Next-Gen SDK)

Prawdziwy baner na dole ekranu Tłumacza, tylko dla kont Free. **SDK nowej generacji**,
nie klasyczny `play-services-ads`.

| Rzecz | Gdzie |
|---|---|
| Zależności | `libs.versions.toml` → `gma-ads` (`ads-mobile-sdk:1.2.1`) + `ump-user-messaging` (`user-messaging-platform:4.0.0`) |
| **ID aplikacji i jednostki — JEDYNE miejsce** | `app/build.gradle.kts`, stałe `admobAppId` / `admobBannerUnitId` (→ `BuildConfig.ADMOB_APP_ID` / `ADMOB_BANNER_UNIT_ID`) |
| App ID w manifeście | `AndroidManifest.xml` — `meta-data com.google.android.gms.ads.APPLICATION_ID` = `${admobAppId}` (**wymóg UMP**) |
| Zgody + inicjalizacja | `ads/AdsConsent.kt` |
| Baner (Compose) | `ui/components/AdBannerView.kt` (`AndroidView` + `AdView.loadAd`) |
| Wywołanie zgód | `MainActivity.onCreate` → `lifecycleScope.launch { AdsConsent.refresh(this) }` |

### ⚠️ Kolejność: zgoda → SDK → reklama (wymuszona kodem, nie konwencją)

Google wprost ostrzega: SDK potrafi wstępnie pobrać reklamy **już w trakcie inicjalizacji**.
Dlatego `MobileAds.initialize()` odpala się **dopiero gdy `canRequestAds()` zwróci true**,
czyli po `requestConsentInfoUpdate()` + ewentualnym formularzu UMP. Baner czyta
`AdsConsent.adsReady` i do tego momentu pokazuje placeholder. **Nie zamieniaj tej
kolejności** — request reklamowy przed zgodą w EOG to najszybsza droga do zamknięcia
konta w AdMobie.

- `canRequestAds()` zwraca **`false` zawsze, dopóki nie wywoła się
  `requestConsentInfoUpdate()`** — nawet gdy zgoda z poprzedniej sesji jest ważna.
  Sprawdzamy je więc dopiero po odświeżeniu.
- Formularz UMP pokazuje się **tylko gdy jest wymagany** (EOG / UK / CH). W Paragwaju,
  USA czy Chinach to no-op i od razu leci inicjalizacja SDK.
- `MobileAds.initialize()` idzie na `Dispatchers.IO` — na głównym wątku grozi ANR.

### ⚠️ NIE kluczuj ID reklam po `BuildConfig.DEBUG`

APK dystrybuowany auto-update'm jest buildem **debugowym** (patrz App Check). Gdyby
testowe ID włączały się od `BuildConfig.DEBUG`, realni użytkownicy dostawaliby reklamy
testowe do końca świata. Przełącznik jest ręczny: obie stałe w `build.gradle.kts`.

Testowe ID Google'a (`ca-app-pub-3940256099942544/…`) działają z dowolnym App ID i nie
łamią polityk — można ich używać, póki nie ma prawdziwej jednostki banera.

### Status: wypuszczone w v1.0.49 (versionCode 50)

ID produkcyjne w `build.gradle.kts` od 2026-09-09. SDK domergował do manifestu dwa
uprawnienia: `com.google.android.gms.permission.AD_ID` i
`android.permission.ACCESS_ADSERVICES_AD_ID` — to one wymuszają deklarację Data Safety
poniżej.

### ⚠️ Do domknięcia (tylko w konsolach, nic w kodzie)

1. **Data Safety w Play Console** — zadeklarować zbieranie identyfikatora reklamowego
   (`AD_ID`) i danych o użytkowaniu. Bez tego kolejne wydanie na produkcję dostanie
   ostrzeżenie/blokadę.
2. **AdMob → Aplikacje → Verbigem** — sprawdzić, czy aplikacja jest „gotowa do
   wyświetlania reklam" (nowa jednostka potrzebuje zwykle kilku godzin, zanim zacznie
   serwować).

### Wejście do ustawień prywatności (wymóg dla EOG) — zrobione

Karta **„Ustawienia prywatności reklam"** w Profilu, pod polityką prywatności. Pokazuje
się **tylko gdy `AdsConsent.privacyOptionsRequired`** — czyli wyłącznie dla użytkowników
z EOG / UK / CH (reszta świata jej nie zobaczy) i nie dla kont Pro (te nie widzą reklam,
więc nie ma czego wycofywać). Woła `AdsConsent.showPrivacyOptions(activity)`; Activity
bierzemy przez `context.findActivity()`, bo UMP nie przyjmie zwykłego kontekstu.
Stringi: `privacy_ad_settings` / `privacy_ad_settings_desc` × 6 języków.

---

## 🌐 Reklamy w webappie — AdMob tu NIE zadziała

**AdMob obsługuje wyłącznie aplikacje mobilne.** Na stronę potrzebny jest osobny produkt:
**Google AdSense** (osobna rejestracja + weryfikacja domeny). Checklista:

1. Zgłosić `mini.verbigem.com` do AdSense i przejść weryfikację domeny.
2. **`mini/public/ads.txt`** → `google.com, pub-<TWOJE-ID>, DIRECT, f08c47fec0942fa0`
   (Vite kopiuje `public/` → `dist/`).
3. ☠️ **Polityka prywatności kłamie.** `mini/scripts/build_privacy.py` (wszystkie 6 języków)
   ma zdanie *„Nie używamy zestawów SDK reklamowych ani narzędzi analitycznych"* — przed
   wstawieniem reklam **musi** tam pojawić się sekcja o cookies i Google jako dostawcy
   reklam (wymóg AdSense). Regeneracja: `python scripts/build_privacy.py` + deploy hostingu.
4. ☠️ **`index.html` obiecuje „bez reklam"** — meta `description`, `og:description`
   i JSON-LD. Trzy miejsca do poprawki.
5. **SPA:** mini to `react-router` — zwykły `<ins class="adsbygoogle">` nie odświeża się
   przy zmianie trasy. Albo Auto Ads (sam nasłuchuje), albo ręczne
   `(adsbygoogle = window.adsbygoogle || []).push({})` w `AdBanner.tsx` przy zmianie
   lokalizacji.
6. **Zgody (CMP)** — jak UMP w Androidzie: AdSense wymaga certyfikowanego CMP dla EOG.
   ⚠️ Deploy: procedura z sekcji *„mini.verbigem.com to TA SAMA webapp"* — `npm run build`
   przebudowuje całą stronę, nie tylko reklamy.

---

## 🔄 Synchronizacja danych (startup sync, last-write-wins)

`SyncManager.syncNow()` uruchamiany w `VerbigemApplication.onCreate()` (korutyna IO, `SupervisorJob`), raz przy starcie po zalogowaniu Firebase.

**Zakres (per `uid`):** profil (`users/{uid}`) · historia (`users/{uid}/history/{syncId}`) · TTS Pro (`app_config/tts`).

**Merge (last-write-wins po `updatedAt`):** lokalne → remote, gdy `remote` nie istnieje lub `local.updatedAt >= remote.updatedAt` (`set(merge)`); remote → local, gdy `remote.updatedAt > local.updatedAt` (`upsertFromRemote`).

**Usuwanie (tombstone, kluczowe):** lokalnie kasujemy wiersz **fizycznie**, ale zapisujemy `syncId` do tabeli `pending_deletes`. Przy następnym syncu wysyłamy tombstone `{syncId, deleted:true, updatedAt}`. Dzięki temu usunięcia **propagują się** między urządzeniami (nie „odżywają"), działają po usunięciu **offline**, a lokalna baza nie puchnie.

**Delta-sync na timestampach:** każda kolekcja (`history`, `ocr_history`) ma w DataStore watermark `lastSyncHistory` / `lastSyncOcr`.
- **Push:** tylko wiersze z `updatedAt > lastSyncX` (`getLocalSince(since)`).
- **Pull:** Firestore `WHERE updatedAt > lastSyncX ORDER BY updatedAt` — serwer zwraca tylko nowsze dokumenty (w tym tombstone'y).
- Po syncu: `lastSyncX = max(dotychczasowe, największe updatedAt z pushed + pulled)` — kolejny sync przesuwa się tylko do przodu.

**Cap historii: max 200 wpisów** (obie listy). Po każdym `addHistory` i po każdym syncu repo woła `pruneToLimit()` (`DELETE … WHERE id NOT IN (SELECT id … ORDER BY timestamp DESC LIMIT 200)`). Stała na sztywno w SQL.

**Infinity-scroll (offset paging, bez Paging 3):** repo eksponuje `getPage(offset, limit)` (`ORDER BY timestamp DESC LIMIT :limit OFFSET :offset`), VM trzyma `historyItems` + `loadMoreHistory()` (pageSize 20) + `resetHistory()`. `allHistory` (pełny Flow) zostaje TYLKO dla startup sync'u. UWAGA: Paging 3 został usunięty — w lokalnym Gradle cache brak JAR-ów `paging-runtime`/`paging-compose` (offline build).

**Reaktywny sync — dwa dodatkowe triggery:**
- **Po każdej zmianie historii** — `TranslatorViewModel.addHistoryAndSync()`, `OcrViewModel.addHistory()` odpalają `SyncManager.syncNow()` zaraz po zapisie lokalnym.
- **Na włączenie sieci** — `ConnectivityObserver` (`callbackFlow` na `NetworkCallback`) nasłuchuje `onAvailable`; `VerbigemApplication` wywołuje sync na przejściu `false→true`. Rozwiązuje „telefon + tablet offline, po połączeniu tylko jedna historia się wgrała".
- Startup sync zostaje jako fallback dla zimnego startu. Sync odpalany jest też **natychmiast po każdym usunięciu** (`deleteHistory`, gdy online).

**Głośnik — animacja tylko na czytanej karcie.** Stan to `speakingSyncId` / `speakingProSyncId` (`null` lub syncId czytanego wiersza); karta porównuje `item.syncId == speakingSyncId`. Poprzednio był jeden współdzielony `isSpeaking: Boolean` i animacja włączała się na wszystkich kartach naraz.

**Odczyt w języku WŁASNEGO wiersza.** `speakHistory(item)` / `speakProHistory(item)` czytają w `LangCode.fromCode(item.targetLang)` — języku zapisanym przy tym tłumaczeniu, nie w bieżącym języku UI. Wynik/edycja OCR czytają w bieżącym `targetLang`.

**Filter logcat:** `SyncManager`.

---

## 📦 Auto-aktualizacja APK

`UpdateManager` + dialog w `MainActivity` (`StartupGate` / `UpdateDownloadDialog`). Po starcie (raz, `LaunchedEffect`) odczytuje **`/updates/version.json`** z Firebase Hosting (`UpdateManager.updateJsonUrl`):
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
Gdy `info.versionCode > currentVersionCode()` → `AlertDialog` (`update_available_title/body/action/later`). W trakcie pobierania `UpdateDownloadDialog`: pasek + procenty + licznik MB (`update_downloading_title/body/percent/bytes`). Błąd pobierania jest **widoczny** (`update_failed_title`, `update_failed`) i daje `update_retry` — wcześniej szedł tylko do logcatu, a dialog wisiał na 0% bez wyjścia.

### Jak działa postęp pobierania (tu były dwa błędy — nie powielaj ich)

- `UpdateManager.downloadAndInstall` emituje `MutableStateFlow<DownloadProgress>` (`bytesRead`, `totalBytes`; `totalBytes == 0` = brak `Content-Length` → `fraction == -1f`, pasek nieoznaczony + sam licznik MB). **Każdy tick to nowa instancja** — `MutableStateFlow` deduplikuje po `equals()`, więc stary model (`MutableStateFlow<Float?>`, w którym `-1f` powtarzał się w kółko) był po cichu gubiony i nie generował rekompozycji.
- Tick leci co **64 KB albo 250 ms** (`EMIT_EVERY_BYTES` / `EMIT_EVERY_MS`).
- ⚠️ **Flow żyje w `MainActivity`, nie w `remember {}`** (`MainActivity.downloadProgress`), a `StartupGate` czyta go `collectAsState()` **w swoim własnym scopie** i przekazuje wartość jako **parametr** do `UpdateDownloadDialog`. `AlertDialog` renderuje się do osobnego okna = **subkompozycja**: gdy stan czytany jest wyłącznie wewnątrz treści dialogu, rodzic się nie rekomponuje i dialog pokazuje wartość z pierwszej kompozycji (objaw: „pasek zamarznięty na 0%").
- **Diagnoza:** logcat `UpdateManager` wypisuje co ~1 MB `Download progress: X KB / Y KB`, po końcu `Download finished, bytes=… (content-length=…)`.

### ⚠️ Źródło pliku update — DWA pliki, nie pomyl

Katalog `dist/` w `verbigem/mini`, deployowany przez `firebase deploy --only hosting --project mini-verbigem` (ten sam projekt Firebase `mini-verbigem` co webappa i Firestore).

- **`dist/updates/version.json` — TEN, KTÓRY CZYTA APPKA.** Trzymany ręcznie. Jako jedyny ma w `apkUrl` **cache-buster `?v=N`** (`...app-debug-v25.apk?v=25`): query omija pułapkę `Cache-Control: immutable` na `/android/**` (CDN cache'uje URL z query jako osobny obiekt), więc nowy `versionCode` → nowy `?v=` → świeże bajty bez purgowania Cloudflara.
- `dist/android/version.json` — generowany przez plugin `injectBuildId` w `vite.config.ts` przy każdym `npm run build`. Appka go **NIE czyta**, ale trzymaj go zgodnego z `/updates/` — inaczej pierwszy `npm run build` cofnie `versionCode` do wartości z `vite.config.ts`.

**Ścieżki:** `onPlayStore == false` (AKTYWNA) → `downloadAndInstall()` pobiera APK przez **OkHttp 4.12** (własna instancja `okhttp3.OkHttpClient`, NIE systemowy `com.android.okhttp`) na `Dispatchers.IO`. Własny `okhttp3.Dns` (lookup przez `InetAddress.getAllByName`) omija zepsuty resolver na niektórych ROM (Xiaomi MIUI + Private DNS rzuca `Unable to resolve host` dla `*.web.app`) — **używamy `.com`, nie `.web.app`**. Weryfikacja po pobraniu: `length >= 1 MB` (odrzuca przypadkowy HTML) → instalacja `Intent.ACTION_INSTALL_PACKAGE` + `FileProvider`. `onPlayStore == true` → `openPlayStore()`.

**NIE używamy już GitHub (repo / raw / API) do update'ów.** Firestore `app_config/update` to tylko fallback w `fetchUpdateInfo()` (gdy Hosting niedostępny).

**Wymagane w manifeście:** `REQUEST_INSTALL_PACKAGES`, `<provider>` FileProvider z `android:authorities="${applicationId}.fileprovider"`.

**Filter logcat:** `UpdateManager` (`Starting APK download`, `Download started/finished`, `Download error`, `Install launch failed`).

### ⚠️ Pułapka: `immutable` cache na `/android/**` — NIGDY nie nadpisuj istniejącej nazwy APK

`firebase.json` ustawia dla `/android/**` nagłówek `Cache-Control: public, max-age=31536000, immutable`. W praktyce: **każdy plik pod `/android/` jest zamrażany w CDN (Cloudflare) na rok**.

- Nowa, **nieużywana wcześniej** nazwa (`app-debug-v24.apk`) → nie ma jej w cache → świeża treść. ✅
- **Nadpisanie istniejącej nazwy** (`app-debug-v23.apk` wrzucony drugi raz z inną treścią) → Firebase przyjmie nowe bajty, ale CDN nadal serwuje starą kopię; użytkownik pobierze POPRZEDNIĄ wersję. ❌

Diagnoza:
```
curl -s https://mini.verbigem.com/android/app-debug-v23.apk | sha256sum          # stary hash
curl -s "https://mini.verbigem.com/android/app-debug-v23.apk?cb=$RANDOM" | sha256sum  # nowy hash
curl -sI https://mini.verbigem.com/android/app-debug-v23.apk | grep -iE 'last-modified|age'
```
Jeśli `?cb=` daje inny hash niż czysty URL ⇒ to cache CDN, nie Firebase (Firebase **ma** nowy plik; potwierdza to domena `*.web.app`, która cache'uje osobno).

**Wniosek: zawsze wypuszczaj nowy `versionCode` = nowa nazwa pliku.** Nadpisanie tej samej nazwy wymagałoby purgu w Cloudflare (brak tokena w repo). Co serwuje serwer, sprawdzisz: `aapt2 dump badging <plik.apk> | head -1` (`C:/Users/milo/AppData/Local/Android/Sdk/build-tools/36.0.0/aapt2.exe`).

### ⚠️ `mini.verbigem.com` to TA SAMA webapp — deploy APK nie może jej przebudować

`verbigem/mini` serwuje na `mini.verbigem.com` aplikację **Mini Verbigem — Translator**. `firebase deploy --only hosting` **zastępuje całą zawartość hostingu** zawartością `dist/` — plików, których w `dist/` nie ma, zostaną **USUNIĘTE z serwera**.

- `npm run build` w `mini` zmienia hashe assetów (`assets/index-*.js`), bo wynik zależy od wersji zależności w `node_modules`, nie tylko od `src/`. **Przebudowa = cicha podmiana działającej strony.**
- **Bezpieczna procedura:** `dist/` odtworzony **co do bajtu** ze stanu na serwerze + zmienione TYLKO `android/version.json` i nowy APK.
  1. kopia `dist/android/` (Vite czyści `dist/`),
  2. pobranie wdrożonych plików — lista + ścieżki w `mini/.firebase/hosting.ZGlzdA.cache` (`ZGlzdA` = base64 „dist"), każdy `curl -o dist/$p https://mini.verbigem.com/$p`,
  3. sprawdzenie `find dist -type f` vs lista z cache — **żadnego nowego i żadnego brakującego pliku**,
  4. podmiana `android/version.json` + wrzucenie APK.
  Wtedy `firebase deploy` zgłasza `uploading new files [0/1]` — webapp nietknięta (potwierdzenie: `/version.json` z `BUILD_ID` nie zmienia wartości).

**Osobne projekty — nie pomyl:** `mini` → projekt `mini-verbigem` (mini.verbigem.com). `webapp` → projekt `verbigem-app-7k2` (verbigem.com). Deploy z `mini` **nie ma fizycznie jak** nadpisać `webapp`.

### 🐛 Uśpiony błąd: `npm run build` w mini sypie błędem (tesseract.js)

`src/ocr/OcrPage.tsx` robi `await import('tesseract.js')`, ale pakietu nie było w `package.json` ani w `package-lock.json` — był tylko w `node_modules`, które potem wyczyszczono. Skutek: `tsc -b` → `TS2307`, `vite build` → `Rollup failed to resolve import`. Naprawione przez `npm install tesseract.js --save`.

⚠️ Wersja `tesseract.js` wchodzi w skład bundle'a, więc jej zmiana **zmienia hashe assetów** — po reinstalacji nie zakładaj, że build odtworzy wdrożoną stronę co do bajtu.

---

## 🌍 Wielojęzyczność — ZAKAZ hardkodowania tekstów UI

Aplikacja jest wielojęzyczna (**PL, EN, DE, ES, ZH, TR**). **Pod karą nie wolno** hardkodować żadnych tekstów interfejsu (etykiet, komunikatów, tooltipów, opisów) w kodzie Kotlin/Compose — ani po polsku, ani po angielsku.

1. Każdy widoczny tekst UI musi pochodzić z `stringResource(R.string.xxx)`.
2. Zasoby: `app/src/main/res/values/strings.xml` (**domyślny = angielski**) oraz `values-pl/`, `values-de/`, `values-es/`, `values-zh/`, `values-tr/`.
3. Etykiety silników (`EngineChoice`) i tooltipy używają `descriptionResId` / `labelResId` mapowanych na `R.string.*` (nie `labelKey` z hardkodowanym tekstem).
4. Komunikaty błędów z warstwy `engine/*` bierzemy przez `context.getString(R.string.xxx)`.
5. **Po dodaniu `string` do `values/strings.xml` należy dodać go do wszystkich pozostałych `values-xx/strings.xml`** (nawet jako tymczasowy angielski fallback).
6. Klucze akcji historii/result: `action_copy`, `action_share`, `action_read`, `action_read_pro`, `action_delete`. Dialog update: `update_available_title/body/action/later`. Reklama: `ad_banner_label/text`.
7. Teksty okien pomocy: `help_*` (≈50 kluczy × 6 języków). Współdzielone: `help_close`, `help_open`. Per ekran: `help_intro_<ekran>` (+ wariant `_title`). Podpisy ikon: `input_caption_*`, `engine_caption_*`. **Najpierw `values/strings.xml`, potem reszta** — skryptem `python` można sprawdzić, czy żaden klucz nie został pominięty:
   ```bash
   python -c "import re,os;base={m for m in re.findall(r'<string name=\"([^\"]+)\"',open('app/src/main/res/values/strings.xml',encoding='utf-8').read())};[print(d,sorted(base-set(re.findall(r'<string name=\"([^\"]+)\"',open(f'app/src/main/res/{d}/strings.xml',encoding=\"utf-8\").read())))) for d in ['values-pl','values-de','values-es','values-zh','values-tr']]"
   ```

⚠️ **Reguła dla kontekstu:** każdy kontekst podmieniany w `LocalContext` **MUSI dziedziczyć po `ContextWrapper`** i mieć Activity u podstawy. `MainActivity.LocalizationWrapper` używał `createConfigurationContext(config)` — to goły `ContextImpl`, więc łańcuch `baseContext` się urywał i `findActivity()` zwracał `null` (objawy: „no activity" w Phone Auth, crash `rememberLauncherForActivityResult` w `OcrScreen`). Naprawione klasą `LocalizedContext(base, locale) : ContextWrapper(base)`, która nadpisuje tylko `getResources()`/`getAssets()`.

---

## 🔥 Firebase — konfiguracja projektu

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
  - ☠️ **REST z tokenem właściciela projektu OMJA reguły bezpieczeństwa.** Backfill potrafi zapisać cudzy dokument, którego aplikacja nie ruszy. Dlatego `backfill_faza0.js` i `backfill_searchtext.js` domyślnie robią dry-run i wymagają `--apply`.

---

## ☁️ Cloud Functions (`functions/`)

Backend czatu. **Wymaga planu Blaze** (wykupiony). Kod w `functions/src/`, kompilacja do `functions/lib/` (gitignored). **Runtime: `nodejs22`** (`firebase.json` → `runtime`, `engines.node` w `functions/package.json`).

| Funkcja | Typ | Co robi |
|---|---|---|
| `onMessageCreated` | trigger `chats/{chatId}/messages/{msgId}` | push FCM do pozostałych członków czatu |
| `matchContacts` | callable | dopasowanie kontaktów po HMAC numeru telefonu |
| `inviteByPhone` | callable | zapraszanie numerów bez konta |
| `verifyPhone` | callable | zapis faktu „to konto ma zweryfikowany numer" |
| `onPhoneVerified` | trigger `users/{uid}` | uzgadnia `phoneDirectory`, rozwiązuje zaproszenia |
| `onMessageSearchIndex` | trigger `chats/{chatId}/messages/{msgId}` | zapisuje znormalizowane `searchText` |
| `suggestFriends` | callable | „Możesz znać" — znajomi moich znajomych (3.9) |

### Jak deployować

```bash
cd functions
npm install         # raz, po klonie
npm run build       # tsc -> lib/  (deploy i tak to robi przez predeploy w firebase.json)

# z katalogu głównego projektu:
firebase deploy --only firestore:rules --project mini-verbigem   # po zmianie reguł
firebase functions:log --project mini-verbigem                   # logi
```

`firebase.json` ma `predeploy: ["npm --prefix \"$RESOURCE_DIR\" run build"]`, więc deploy sam kompiluje — `npm run build` ręcznie słuzy tylko złapaniu błędu typów bez czekania na upload.

⚠️ **Deploy katalogu `functions/`: lokalny serwer discovery ładuje `lib/index.js` i ma domyślnie 10 s.** Na tej maszynie zimny start Node + `firebase-admin` to za mało → `User code failed to load. Cannot determine backend specification. Timeout after 10000`.

```bash
export FUNCTIONS_DISCOVERY_TIMEOUT=60
```

### ☠️ NIGDY nie deployuj funkcji bez wylistowania nazw

Projekt `mini-verbigem` jest **współdzielony z webappem `verbigem/mini`**, który ma własny katalog `functions/` i sześć działających funkcji produkcyjnych.
⚠️ **Od 2026-09-08 wszystkie funkcje webminiego są w `us-central1`** (zgodnie z regionem projektu Firebase). Wcześniej były w `europe-west1`, co psuło APK: `FirebaseFunctions.getInstance()` bez regionu woła domyślnie `us-central1`, więc `createCheckout` (doładowanie portfela) w ogóle nie istniał z punktu widzenia aplikacji.

| Funkcja | Region | Po co |
|---|---|---|
| `deepseekProxy` | us-central1 | tłumaczenie online — OpenRouter, przekazuje wybrany model 1:1 + rozlicza z `usage.cost` |
| `paddleWebhook` | us-central1 | płatności — webhook Paddle |
| `portalSession` | us-central1 | portal klienta Paddle |
| `visionProxy` | us-central1 | OCR online |
| `walletTopUp` | us-central1 | doładowanie portfela |
| `createCheckout` | us-central1 | otwiera checkout Paddle (APK: doładowanie portfela) |

Oba projekty mają `codebase: default`, więc Firebase widzi **jeden** zbiór funkcji. `firebase deploy --only functions` uruchomiony stąd uznaje tamte pięć za osierocone i chce je **usunąć**. W trybie nieinteraktywnym na szczęście się wykłada (`Aborting because deletion cannot proceed in non-interactive mode`) — z `--force` po prostu by je skasowało: **płatności, OCR i portfel przestałyby działać.**

Zawsze podawaj nazwy:

```bash
firebase deploy --only functions:onMessageCreated,functions:matchContacts,\
functions:inviteByPhone,functions:verifyPhone,functions:onPhoneVerified,\
functions:onMessageSearchIndex \
  --project mini-verbigem
```

Dopiero nadanie obu projektom różnych `codebase` w `firebase.json` (np. `android` i `mini`) trwale rozwiązałoby problem — wymagałoby przewalczenia już wdrożonych funkcji, więc na razie zostawiamy jak jest i uważamy.

⚠️ **Pierwszy deploy funkcji 2. gen na projekcie ZAWSZE sypie błędem Eventarc:** `Permission denied while using the Eventarc Service Agent`. Uprawnienia Service Agent propagują się z opóźnieniem — **powtórzyć deploy po kilku minutach**, nie szukać błędu w kodzie.

⚠️ **Po udanym deployu CLI żąda polityki czyszczenia obrazów** (kontenery w Artifact Registry rosną i kosztują):

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

⚠️ **Rotacja pieprzu unieważnia wszystkie dopasowania** — stare hashe w `phoneDirectory` przestaną się zgadzać; zmiana wymaga przeliczenia katalogu, nie tylko podmiany sekretu. Funkcja nie wdroży się bez ustawionego sekretu (deploy pyta o to automatycznie).

### Weryfikacja numeru telefonu (E.164, Phone Auth)

**Aplikacja NIGDY nie wysyła numeru do naszego backendu.** Kolejność:

1. Firebase Phone Auth wysyła SMS i dowiązuje numer do konta (**`linkWithCredential`**, nie `signInWithCredential` — konto już istnieje).
2. Firebase wkłada zweryfikowany numer E.164 do tokena ID.
3. `getIdToken(true)` odświeża token — bez tego funkcja widziałaby token sprzed dodania numeru.
4. `verifyPhone` czyta `request.auth.token.phone_number` i zapisuje na `users/{uid}` tylko `phoneVerified` + `phoneHash` + `phoneVerifiedAt`. **Nie ma parametru do sfałszowania** — klient nie jest pytany o numer.
5. `onPhoneVerified` (trigger) dopisuje `phoneDirectory/{hmac}` i rozwiązuje zaproszenia oczekujące.

☠️ **`requireSmsValidation(true)` w `PhoneAuthOptions` NIE WOLNO używać** przy zwykłej weryfikacji numeru. Ta flaga istnieje **TYLKO** dla MFA i rzuca `IllegalArgumentException: You cannot require sms validation without setting a multi-factor session` (w v37 → crash przy każdym kliknięciu „wyślij SMS"; naprawione w v38). Instant verification: `onVerificationCompleted` → `PhoneCodeRequest.AutoVerified` → `linkWithCredential`.

☠️ **SMS nie wyjdzie, dopóki nie odblokujesz regionu (status 17006).** W nowym projekcie Firebase domyślna polityka to `allowlistOnly` z **pustą listą regionów** — SMS nie wychodzi **nigdzie**, choć dostawca „Phone" jest włączony, a odciski SHA się zgadzają. Objaw: `FirebaseAuthException` `ERROR_OPERATION_NOT_ALLOWED` (17006) z dopiskiem `[ SMS unable to be sent until this region enabled by the app developer. ]`. **Pułapka: ten sam kod dostajesz przy wyłączonym dostawcy logowania** — przez to wyglądał na problem z odciskiem SHA i kosztował cały wieczór. `PhoneVerificationViewModel.messageFor` rozróżnia te dwa przypadki po słowie „region".

```bash
node check_sms_region.js          # drukuje smsRegionConfig
node set_sms_region.js PY,PL      # allowlistOnly.allowedRegions = [PY, PL]
```

Ręcznie: Firebase Console → **Authentication → Settings → SMS region policy**. Każdy region to realny koszt SMS-a i ryzyko SMS-pumpingu — lista powinna zostać krótka.

☠️ **Przed pierwszym testem trzeba wpisać odciski SHA certyfikatu** (Firebase Console → Project settings → aplikacja Android → **Add fingerprint**), potem pobrać nowy `google-services.json`. **Dopisz oba: SHA-1 *i* SHA-256** — Phone Auth weryfikuje przez Play Integrity i potrafi odrzucić build, dla którego widzi tylko jeden. Debug keystore (`C:\Users\milo\.android\debug.keystore`, hasło `android`):

```
SHA1:   EC:9D:EB:58:CD:F2:48:3A:7E:FE:2B:73:C2:C7:90:1B:9D:6D:3C:CC
SHA256: A4:2A:45:FF:D5:25:B9:02:8C:12:2B:BE:8A:92:FE:D9:F0:3D:A7:5C:9F:D1:CA:4D:90:98:8D:CA:AD:EC:CA:94
```

**Normalizacja numerów:** matching porównuje **skróty**, nie łańcuchy — `0981 123 456` i `+595981123456` haszują się zupełnie inaczej, więc każdy numer musi być pełnym E.164 **przed** haszowaniem. `data/PhoneNumbers.kt` używa `PhoneNumberUtils.formatNumberToE164` (libphonenumber jest w systemie — nie dokładamy zależności). Bez numeru kierunkowego kraj się zgaduje (SIM, potem locale), więc `e164Candidates` zwraca **listę** i haszujemy każdą. ⚠️ **Zmiana normalizacji po weryfikacji pierwszych numerów oznacza przeliczenie całego `phoneDirectory`** — rób ją tylko, gdy katalog jest pusty.

### App Check

Inicjowany w `VerbigemApplication`; dostawca zależy od wariantu (`src/debug` → `DebugAppCheckProviderFactory`, `src/release` → Play Integrity). `firebase-appcheck-debug` jest wyłącznie w `debugImplementation`, więc build release'owy fizycznie nie potrafi sam siebie poświadczyć.

⚠️ **`matchContacts` ma `enforceAppCheck: false` i to NIE jest przeoczenie.** APK dystrybuowany przez auto-update to build **debugowy**, a Play Integrity nie poświadcza aplikacji, których nie zainstalował Play Store. Włączenie tego dziś odrzucałoby każdego użytkownika, nie tylko nadużywających.

Debugowy token do wpisania ręcznie (Console → App Check → **Debug tokens**) pojawia się w logcat po tagiem `FirebaseAppCheck`; jest per instalacja (ponownie po reinstalacji).

📌 **TODO: włączyć enforcement App Check PO dodaniu appki do Google Play.** Gdy opublikujesz pierwszy **Play-signed release** i przełączysz dystrybucję na sklep (`onPlayStore: true` w `version.json`), zrób:
1. Console → **App Check** → zarejestruj appkę Android, provider **Play Integrity**, dodaj **SHA-256 certyfikatu podpisującego Play** (Play Console → Setup → App integrity).
2. App Check → **APIs** → włącz `enforce` na **Firebase Auth**, **Firestore** i **Storage** (osobno, usługa po usłudze).
3. `functions/src/contacts.ts` → `matchContacts` na `enforceAppCheck: true` (TODO w kodzie mówi gdzie).
4. Wycofaj debug-tokeny.
⚠️ Bez kroku 1 (Play-signed build + SHA z Play) wymuszanie odrzuci wszystkich użytkowników — **nie włączaj `enforce` na debugowej dystrybucji auto-update**.

### Powiadomienia push — decyzje, których nie wolno zmienić niechcący

1. **Wiadomość FCM jest `data-only` (bez pola `notification`).** Z polem `notification` Android sam renderuje powiadomienie w tle i **nie wywołuje `onMessageReceived`** — akcje „Odpowiedz" / „Oznacz jako przeczytane" działałyby tylko przy otwartej aplikacji. Data-only daje pełną kontrolę; ceną jest podatność na Doze, dlatego `priority: "high"` + TTL 4 tyg.
2. **Kanał `verbigem_messages`.** Identyfikator jest po obu stronach: `functions/src/messaging.ts` i `VerbigemNotifications.ensureChannel()`. Zmiana w jednym miejscu bez drugiego = ciche zniknięcie powiadomień na Androidzie 8+.
3. **Podgląd treści DOMYŚLNIE WYŁĄCZONY.** `buildBody()` zwraca „Nowa wiadomość", chyba że `app_config/notifications` ma `showMessagePreview == true`. Push wychodzi z urządzenia i przechodzi przez serwery Google — to inna historia prywatności niż „tłumaczenie dzieje się na Twoim telefonie". Bezpieczeństwo niejawne: brak dokumentu = podgląd wyłączony. Przełącznik w Firestore, da się włączyć bez nowego APK.
4. **Treść podglądu z `senderTranslation`, nie z `text`.** Podpowiedź nadawcy powstała *w języku odbiorcy* (decyzja D1) — to jedyna wersja, którą ten człowiek przeczyta. Surowy `text` jest w języku nadawcy.
5. **Wyciszenie honorowane w chmurze**, nie na urządzeniu (`muted === true` na `users/{odbiorca}/contacts/{nadawca}`) — wyciszony czat nie budzi telefonu.
6. **Token = ID dokumentu.** FCM zwraca `messaging/registration-token-not-registered` dla martwych tokenów; funkcja kasuje je po ID, bez odczytu.

Po stronie aplikacji: `VerbigemMessagingService` (odbiera), `FcmTokenManager` (`users/{uid}/fcmTokens/{token}`, rejestracja po odtworzeniu sesji, usunięcie przy wylogowaniu), `VerbigemNotifications` (kanał, grupa per czat, MessagingStyle, akcje), `NotificationActionReceiver` (odpowiedź + przeczytane pod `goAsync()`, bo robią zapis do Firestore).

Uprawnienie `POST_NOTIFICATIONS` (Android 13+) jest proszone **raz, przy pierwszym otwarciu skrzynki czatu** — nie na starcie aplikacji (użytkownik nie ma wtedy powodu chcieć powiadomień, a Android przestaje pytać po dwóch odmowach). Flaga `asked_notif_perm` w DataStore.

---

## 🚀 Jak uruchomić projekt

1. Otwórz katalog `c:\Users\milo\verbigem\android` w **Android Studio**.
2. Poczekaj na synchronizację Gradle (`Sync Project with Gradle Files`).
3. Podłącz urządzenie z Androidem lub uruchom emulator (`arm64-v8a` lub `x86_64`).
4. Kliknij **Run** (`Shift + F10`) lub zbuduj APK:
   ```bash
   ./gradlew assembleDebug
   ```

**Build (Windows, bash):** JDK 21 w `C:/Users/milo/.jdks/jbr-21.0.11` (ustaw `JAVA_HOME`). Natywny build NDK (~2–3 min) kompiluje `libverbigem_llama.so` (Release + KleidiAI + Vulkan).

### Flow wydania (auto-update end-to-end — BEZ KABLA)

Poniższa lista to **JEDYNE źródło prawdy** dla wypuszczania wersji (wcześniejsza wersja skrótu błędnie kazała edytować `dist/android/version.json` ręcznie, co rozjeżdżało się z generowaniem pliku przez Vite). **Zanim wykonasz kroki, przeczytaj pułapki wyżej:** „⚠️ Źródło pliku update — DWA pliki, nie pomyl", „⚠️ Pułapka: `immutable` cache na `/android/**`", „⚠️ `mini.verbigem.com` to TA SAMA webapp".

1. Podbić `versionCode`/`versionName` w `app/build.gradle.kts` (konwencja: `versionName` = `1.0.(versionCode - 1)`, np. code 25 → name `1.0.24`).
2. Build APK → `app/build/outputs/apk/debug/app-debug.apk`. Preferowana komenda (bezpośrednio przez wrapper Javy — w niektórych środowiskach `cmd.exe` jest blokowany i `gradlew.bat` nie przejdzie):
   ```bash
   JAVA_HOME="C:/Users/milo/.jdks/jbr-21.0.11" ANDROID_HOME="C:/Users/milo/AppData/Local/Android/Sdk" \
     "C:/Users/milo/.jdks/jbr-21.0.11/bin/java.exe" \
     -classpath "gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain \
     assembleDebug --console=plain
   ```
3. APK → `verbigem/mini/dist/android/app-debug-vN.apk` (N = nowy versionCode).
4. **`verbigem/mini/vite.config.ts`** (plugin `injectBuildId`) → `versionCode`, `versionName`, `apkUrl` = `https://mini.verbigem.com/android/app-debug-vN.apk?v=N` (**z cache-busterem `?v=N`**), `updatedAt`. Źródło prawdy dla przyszłych buildów Vite.
5. **`verbigem/mini/dist/updates/version.json`** — TE SAME wartości (to ten plik czyta appka). ⚠️ **Nie uruchamiaj `npm run build`**, chyba że celowo przebudowujesz webapp. Ręczna edycja jest bezpieczna wtedy i tylko wtedy, gdy krok 4 zrobiłeś identycznie — inaczej pierwszy `npm run build` cofnie `versionCode`.
6. `cd verbigem/mini && firebase deploy --only hosting --project mini-verbigem`. Przed deployem sprawdź `find dist -type f`, czy nie brakuje plików webappy.
7. Zweryfikuj: `curl -s https://mini.verbigem.com/updates/version.json` (**nie `/android/`!**) musi zwrócić nowy `versionCode` i `apkUrl` z `?v=N`. W razie wątpliwości `curl -s ".../android/app-debug-vN.apk?v=N" | sha256sum`.
   - Jeśli zmieniono reguły Firestore (np. nowa kolekcja): `cd verbigem/android && firebase deploy --only firestore:rules --project mini-verbigem`. App czyta `app_config/*` PRZED loginem (read:true), a zapisuje do `users/{uid}/history` i `users/{uid}/ocr_history` (oba `allow read,write` dla właściciela).
8. Test: zainstaluj starszy APK (niższy versionCode) → otwórz → dialog update → pobierz nowy.

### Repozytorium i zasady pracy

**Git:** `https://github.com/ihletru/verbigem_android` (branch `master`, prywatne). `.gitignore` wyklucza: `build/`, `llama_master/`, `*.gguf`, `model_probe/`, `build_log*`, `crash_log*`, `local.properties`, `.workbuddy-ai/`.

**Po każdej zmianie większej niż kosmetyczna: zaktualizuj README, potem commit + `git push`.**

Projekt `mini` (webapp + hosting) ma własne repozytorium (`verbigem-mini`, prywatne); deploy hostingu idzie przez `firebase deploy --project mini-verbigem`.
