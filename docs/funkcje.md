> 📄 Fragment dokumentacji Verbigem Android. Indeks: [README.md](../README.md)

# 🌟 Kluczowe funkcje

## 1. Translator (ekran główny)

- Tłumaczenie między 6 językami: **PL, EN, ES, ZH, DE, TR**.
- Silniki: ⚡ **Szybki** (1.8B @1.25Bit, ~440 MB) · 🎯 **Dokładny** (1.8B @Q4_K_M, ~1.1 GB) · 🧠 **Pro 7B** (7B @UD-Q2_K_XL, ~2.9 GB) · ⚖️ **Oba (porównaj)** · ☁️ **API online** (fallback DeepSeek z portfelem).
- ⚠️ **Pro 7B jest ukryty na słabszych urządzeniach.** `ModelDownloader.blockReason()` sprawdza RAM ≥ 5 GB i ≥ 1.2× rozmiar wag wolnego miejsca; jeśli warunek nie jest spełniony, ikona w ogóle się nie pojawia (`TranslatorViewModel.availableEngines`). Użytkownik nie może więc pobrać 2.9 GB, których nie da się załadować.
- **Push-to-talk (🎤):** przycisk po lewej od OCR. Trzymaj → nagrywa (SpeechRecognizer STT z podglądem na żywo), puść → dopisuje rozpoznany tekst do pola (**append, nie overwrite**). W trakcie nagrywania mikrofon jest czerwony.
- **Czytaj Pro (💎):** płatne TTS przez **OpenRouter**, konfigurowane w `app_config/tts` na Firestore (domyślny model `google/gemini-3.1-flash-tts-preview`, osobny dla ZH: `fish-audio/s2.1-pro`). Szczegóły niżej.
- **Skasuj** — czyszczenie wejścia i wyniku. Automatyczny downloader modeli z Hugging Face.
- **Historia w Room (SQLite)** — wiersz ma akcje: kopiuj, udostępnij, czytaj (offline), czytaj Pro (💎), skasuj.
- **Startowa synchronizacja z Firestore** (last-write-wins po `updatedAt`): profil, historia, konfiguracja TTS Pro.
- **Auto-aktualizacja APK** z Firebase Hosting — patrz sekcja **📦 Auto-aktualizacja APK**.

## 2. Rozmowa lokalna (Conversation)

- Dwie osoby dzielą jeden telefon (Strona A / Strona B), automatyczne przełączanie aktywnej strony.
- **⚠️ Kolejność modyfikatorów w `ConversationScreen` jest nieprzypadkowa:** `imePadding()` **PRZED** `verticalScroll()`.
  ```kotlin
  Modifier.fillMaxSize().imePadding().padding(16.dp).verticalScroll(scrollState)
  ```
  Użyte PO (`==` wewnątrz) `verticalScroll()` dokłada tylko pusty margines na końcu treści, a viewport zostaje pełnej wysokości — wtedy `bringIntoViewRequester` „przewija pole do widoku" = **na sam dół ekranu, czyli pod klawiaturę**. Odwrócona kolejność zwęża viewport o wysokość IME, więc „do widoku" znaczy „nad klawiaturą".
  Druga połowa naprawy: klawiatura wjeżdża **~250 ms po** focusie, więc sam `onFocusEvent` nie wystarcza — `LaunchedEffect(WindowInsets.isImeVisible)` z `delay(250)` odpala `bringIntoView()` ponownie. Wymagane: `android:windowSoftInputMode="adjustResize"` w manifeście + `enableEdgeToEdge()` w `MainActivity` (bez tego `imePadding()` jest zerem).
- Natywne STT z podglądem na żywo → tłumaczenie Hy-MT2-1.8B → automatyczny odczyt TTS dla rozmówcy.

## 3. Czat zdalny 1:1 (Chat)

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
- **Wyszukiwanie w wiadomościach:** ⚠️ **w przebudowie (faza 5/6 E2E).** Do fazy 5 działało po serwerowym indeksie `searchText` (zakres po prefiksie: `>= q`, `< q + "\uF8FF"`, więc „kot” nie znajdzie „Ala ma kota” — to ograniczenie było napisane wprost w UI). Trigger `onMessageSearchIndex` został **usunięty**, bo indeks to znormalizowana **TREŚĆ** wiadomości, a czat jest szyfrowany end-to-end. Docelowo wyszukiwanie liczy się **lokalnie, na urządzeniu**, po odszyfrowanych wiadomościach z wątku (`data/MessageSearch.kt` zostaje jako normalizacja). ⚠️ **Stan przejściowy: nowe wiadomości są niewyszukiwalne** — wyszukiwanie widzi tylko to, co zaindeksowano przed fazą 5.

## 4. Kontakty i Znajomi (Contacts)

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

## 5. OCR ze zdjęcia/aparatu (Camera OCR)

- **Aparat = PEŁNA ROZDZIELCZOŚĆ** — `ActivityResultContracts.TakePicture()` + `FileProvider` (`${packageName}.fileprovider`, `cache-path path="."`, katalog `cacheDir/ocr_camera/`), potem ten sam `processImageUri` co galeria. **NIE używamy `TakePicturePreview()`** — zwraca thumbnail ~160px, który psuje czytelność OCR.
- `loadBitmap(uri)` czyta **EXIF orientation** i obraca bitmapę do pionu (aparat zapisuje obrócone; galeria auto-obraca, `BitmapFactory` nie).
- OCR: **Google ML Kit Text Recognition** + tłumaczenie Hy-MT2.
- **Crop przed odczytem:** ramka to **ręcznie napisany overlay** (`ui/screens/ocr/CropOverlay.kt`), NIE biblioteka (`cropview/` usunięte — gotowe biblioteki nie działały w tym układzie). Pojedynczy `Canvas` + **jeden** `pointerInput(Unit)`; rozciąganie 4 rogów (hit-radius 34dp) z nieruchomym rogiem przeciwnym; `awaitEachGesture` nie konsumuje gestu, który nie trafił uchwytu, więc strona się przewija. Współrzędne znormalizowane 0f..1f w `OcrViewModel.cropRectFlow`, domyślnie 0.1–0.9.
- ⚠️ **Zachowanie gestów weryfikuje się TYLKO na telefonie po zainstalowaniu APK** — build to nie to samo co działający UX.
- **OCR Pro (💎)** — przycisk obok Aparat/Galeria, dla free wyszarzony z tooltipem `ocr_pro_coming_soon`. Komponent `ProFeatureButton` (współdzielony z głośnikiem Pro).
- **Streaming:** `OcrViewModel.translateText()` woła `translateSegmented(..., onPartial = { ... })` — wypisuje wynik przyrostowo, tak jak Translator.
- **Wybór silnika (od v1.0.53):** OCR ma ten sam `EnginePicker` co Translator (⚡ Szybki / 🎯 Dokładny / ⚖️ Oba / ☁️ Online). Do v1.0.52 był na sztywno na Szybkim (`isAccurate = false`) i nagłówek wyniku miał twardo wpisane „Szybki" — również w locale EN/DE/ES/TR/ZH, gdzie zostawało polskie słowo. Wybór jest **wspólny z Tłumaczem** (ten sam klucz `engine_choice` w DataStore), a lista dostępnych silników idzie z jednej funkcji `availableEngines(context)` (`data/model/EngineChoice.kt`) — nie z dwóch kopii. Brak wag → ten sam `ModelDownloadDialog` co w Tłumaczu. ⚖️ Oba daje dwa wyniki (`translatedText` + `secondaryTranslatedText`), każdy z etykietą.
- **Własna historia OCR** — osobna tabela `ocr_history` i osobna kolekcja Firestore `users/{uid}/ocr_history/{syncId}`; te same zasady last-write-wins + tombstone, ale listy nigdy się nie mieszają. `SyncManager.syncCollection(...)` wołany dla `"history"` i `"ocr_history"`. Karty mają pełen zestaw akcji jak w Translatorze.
- **Ekran OCR nadal pokazuje BottomNav** (jest w `AppNavigation.showBottomNav`), ale **ikona OCR NIE jest w pasku** — dolny pasek ma **zawsze MAX 5 ikon** (Translator, Rozmowa, Czat, Kontakty, Profil). Wcześniej (v41) OCR dostał szóstą pozycję, ale to zapychało pasek i nie skracało drogi do OCR — wejście do niego i tak jest jednym tapem z Tłumacza (ikona aparatu w `HelpFramedIconButton`). Patrz sekcja *„Dolny pasek — zasady"*.
- ✅ **Zamknięte w v1.0.55 (§5.4): zdjęcie i głosówka idą przez kolejkę.** Do v1.0.54 `sendImage()` / `sendVoice()` uploadowały w gołym `try/catch` OBOK `chat_outbox` — nieudana wysyłka znikała bez śladu w logcat. Teraz oba wstawiają wiersz `ChatOutboxEntity` (`type = "image"` z `localUri`, albo `type = "audio"` z `transcript`), a `flushOutbox()` → `flushRow()` robi resztę: upload → OCR → wysyłka. Sukces = wiersz usunięty, błąd = `status = "failed"` i czerwony dymek z „ponów" (`retryFailed()` działa bez zmian). Dymek w kolejce pokazuje miniaturę z `content://` (Coil), więc zdjęcie widać od razu, a ponawianie nie prosi o wybranie go jeszcze raz. Wymagało migracji bazy **v9 → v10** (`MIGRATION_9_10`, 5 × `ALTER TABLE … ADD COLUMN … NOT NULL DEFAULT`).
  - ⚠️ **Pułapka Room, sprawdzona w bytecode'u (2.6.1):** SQLite nie doda kolumny `NOT NULL` bez `DEFAULT`, a Room porównuje `defaultValue` **tylko wtedy, gdy encja je deklaruje** (`createdFrom == 1 && defaultValue != null` w `TableInfo.Column.equals`). Encja ma tu Kotlinowe `= ""` / `= "text"`, które NIE są domyślnymi wartościami kolumny → oczekiwane `defaultValue` to `null` → `DEFAULT ''` w migracji jest tolerowane. Bez tego założenia trzeba by przenosić tabelę (utwórz nową / skopiuj / upuść / zmień nazwę).
- ✅ **Zamknięte w v1.0.57: kolejka nie gubi „drugiej" wiadomości.** `flushOutbox()` zaczynał od `if (flushing) return` — wiersz dodany w trakcie opróżniania (drugie zdjęcie wysłane, gdy pierwsze się jeszcze uploaduje) zostawał na „🕓 wysyłanie" aż do następnego wyzwalacza: powrotu sieci (`ConnectivityObserver`) albo kolejnej wysyłki. Teraz `flushOutbox()` zamiast wychodzić ustawia `reflush = true`, a blok `finally` po zwolnieniu `flushing` odpala opróżnianie raz jeszcze. ⚠️ Nie wołaj `flushOutbox()` rekurencyjnie *przed* `flushing = false` — zakleszczyłoby się na własnej fladze.
  - ⚠️ **Płatna funkcja nie może milczeć:** `speakPro()` łapał wyjątek i tylko logował — brak dźwięku bez słowa wyjaśnienia wygląda na oszustwo. Teraz idzie `showMessage(R.string.read_pro_failed)` (klucz istniał od v1.0.54).
- ✅ **Zamknięte w v1.0.59: liczba mnoga w komunikatach z liczbą.** Trzy klucze miały jedną formę dla każdej wartości — `contacts_import_vcf_done` brzmiał „Imported %1$d contact" (także dla 5), `people_you_may_know_mutual` „%1$d mutual friends" (także dla 1), `contacts_perm_found` „Found %1$d contacts…". Zamienione na `<plurals>` × 6 locale + `pluralStringResource(R.plurals.x, n, n)` w 3 miejscach `ContactsScreen`.
  - ⚠️ **Compose ma osobną funkcję:** `pluralStringResource` (`androidx.compose.ui.res`), **nie** `stringResource` — przeciążenie z liczbą mnogą nazywa się inaczej. Pierwszy `Int` po id to *wybór formy*, drugi (i kolejne) to argumenty formatu — trzeba podać liczbę **dwa razy**: `pluralStringResource(R.plurals.x, n, n)`.
  - ⚠️ **Polska potrzebuje czterech form, nie dwóch:** `one` (1), `few` (2–4), `many` (5+), `other` (0). Ręczny wariant „jeśli 1 to osobny klucz" (`noads_resume_in` / `noads_resume_in_one`) tego nie załatwia i nie skaluje się na kolejne języki.
  - ⚠️ **TR i ZH mają tylko `other`** — turecki nie odmienia rzeczownika po liczebniku („5 ortak arkadaş"), chiński nie odmienia wcale. Brakujące `quantity` w danym locale spada na `other`, ale lint ostrzega — dlatego TR dostaje `one` o tej samej treści.
  - 📋 **Jak to znaleźć:** skan `<string>` z argumentem `%d` i rzeczownikiem po nim. `topup_credits` (300/500/1000) został zwykłym stringiem — nigdy nie przyjmuje 1, więc nie było co naprawiać.
- ✅ **Zamknięte w v1.0.58: „udało się", choć się nie udało.** Dwa miejsca zostawiały po sobie tylko `Log.w`:
  - `ContactCardViewModel.hideConversation()` — nieudany zapis do `chat_hidden` nie dawał żadnego znaku, a `ContactCardScreen` woła `onBack()` **natychmiast** po wywołaniu. Użytkownik wracał do skrzynki, w której rozmowa nadal była. ⚠️ **Tu musi być `Toast`, nie Snackbar** — cokolwiek hostowanego przez ekran znika razem z ekranem (`LaunchedEffect` zostaje anulowany przy zdjęciu kompozytu z kompozycji). Toast jest globalny i przeżywa nawigację; wzorzec już był w kodzie (`OcrScreen`, `MyQrScreen`, `TranslatorScreen`).
  - `ContactsViewModel.searchUsers()` — błąd Firestore ustawiał `_searchResults = emptyList()` i na tym koniec, więc `permission-denied` albo offline wyglądały identycznie jak „nie ma takiego użytkownika". Teraz jest `_searchError: StateFlow<Boolean>` i tekst pod polem wyszukiwania (`contacts_search_failed`), renderowany tylko gdy `searchResults.isEmpty()` — **pusta lista musi znaczyć „nikogo nie ma", a nie „nie udało się zapytać"**.
  - ⚠️ **Pułapka AAPT:** apostrof w wartości `<string>` to błąd kompilacji zasobów (`Resource compilation failed … Can not extract resource`). `Couldn't` → `Couldn\'t`. Objaw niby w `mergeDebugResources`, a komunikat nie wskazuje linii — sprawdzaj ostatnio dodane stringi.
  - 📋 **Skan, który to znalazł:** `catch` w całym `app/src/main/java`, których treść to wyłącznie `Log.*` i komentarze — 19 trafień. Większość jest słusznie niema (kolejki offline, obecność, token FCM — tam porażka musi milczeć). Naprawiono te dwa, gdzie milczenie zmieniało *znaczenie* wyniku. Zasada: **cichy `catch` jest w porządku wtedy i tylko wtedy, gdy porażka nie zmienia tego, co użytkownik myśli, że się stało.**
- ✅ **Zamknięte w v1.0.56: mikrofon przestał milczeć.** Trzy sytuacje kończyły się tylko na `Log.w` i były nieodróżnialne dla użytkownika: brak zgody na `RECORD_AUDIO` (`ChatThreadScreen`, callback `rememberLauncherForActivityResult`), brak STT na urządzeniu i błąd rozpoznawania (`SpeechManager` → `onError`). Teraz każda idzie do jednego `SnackbarHost` nad polem wpisywania — `_uiMessage: StateFlow<String?>` w `ChatThreadViewModel` + `showMessage(resId)` (zasób, nie hardkodowany tekst) + `consumeMessage()`. Ekran wątku nadal **nie ma `Scaffold`** — wystarczy `Box` wokół głównej `Column` i `SnackbarHost(hostState, Modifier.align(BottomCenter))`.
  - ⚠️ **Pułapka Compose:** `stringResource()` jest `@Composable`, więc nie wolno go wołać z callbacku zgody na mikrofon — wynieś `val micDeniedLabel = stringResource(...)` wyżej (ten sam schemat co `sendingLabel` przy statusie dymka).

## 6. Kody QR

Jeden surowy link profilowy: `https://mini.verbigem.com/u/<uid>` (`usersPublic` jest publiczne, więc podpisany token byłby nadmiarowy). Jeden parser `ProfileLinks.uidFromUrl` obsługuje skaner i App Links — zmiana schematu nie rozjeżdża się między kodem generującym a czytającym.

- **Mój kod QR** — `MyQrScreen` + ZXing `core` 3.5.3 (`data/QRBitmap.kt`), trasa `Screen.MyQr`.
  Wejście jest **z dwóch miejsc**: karta w Profilu oraz nagłówek Kontaktów (patrz niżej).
  ⚠️ To ten sam ekran i ta sama trasa — drugie wejście nie duplikuje kodu, tylko dodaje skrót.
- **Skaner** — `ScanScreen` na GMS Code Scanner (`play-services-code-scanner` 18.3.0). Obcy link → komunikat „to nie kod Verbigem", nie otwieramy obcych stron. Trasa `Screen.Scan`.
- **Dwa wejścia w nagłówku Kontaktów (od v1.0.72)** — `ScreenHeader.trailing` to teraz `Row`
  z dwoma ikonami o **różnych kolorach**, bo obie dotyczą kodów QR i bez tego wyglądały jak
  jedna funkcja:
  * **Mój kod QR** — `Icons.Default.QrCode2`, `tint = accent`. Długie przytrzymanie → okno
    pomocy (`qr_my_code` + `help_profile_qr`) wyjaśniające, jak pokazać kod i co zrobić,
    gdy rozmówca nie ma skanera.
  * **Skanuj kod** — `Icons.Default.QrCodeScanner`, `tint = ink`. Zachowanie bez zmian.
  ⚠️ Wcześniej nagłówek używał `Icons.Default.QrCode` — po zmianie obie ikony są wariantami
  `QrCode*`, więc `grep` po `QrCode` zwraca trzy różne rzeczy. Nie usuwaj `QrCode` z Profilu.
- **App Links** — `intent-filter` VIEW z `autoVerify="true"`. Obsługa w `MainActivity.handleDeepLink` + `AppNavigation.openProfileUid`.
  > ✅ **Skonfigurowane i zweryfikowane (sprawdzone 2026-09-10):** `mini/public/.well-known/assetlinks.json`
  > istnieje (commit `3f3c796`), `https://mini.verbigem.com/.well-known/assetlinks.json` zwraca
  > 200, a odcisk w pliku (`A4:2A:45:FF:…:94`) jest identyczny z tym, który zwraca
  > `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey` (hasło `android`).
  > ⚠️ Ten odcisk to **debug keystore** — czyli klucz, którym podpisany jest rozpowszechniany
  > `app-debug.apk`. Przy wydaniu przez Play trzeba **dopisać** do tablicy
  > `sha256_cert_fingerprints` odcisk z Google Play Console → App signing (Play podpisuje
  > APK własnym kluczem) i odcisk z Firebase: Project settings → Your apps → SHA certificate
  > fingerprints. Tablica może trzymać wiele odcisków — nie nadpisuj, dopisuj.
  >
  > ⚠️ **HTTP 200 to za mało, żeby uznać to za działające.** Hosting ma rewrite `**` → `/index.html`,
  > więc brakujący plik też zwróci 200 — tyle że z landing page’em w środku. Weryfikuj treścią:
  > `curl -s -D - https://mini.verbigem.com/.well-known/assetlinks.json` musi dać
  > `Content-Type: application/json` i tablicę z `delegate_permission`, nie `<!doctype html>`.
  > (Mimo `**/.*` w `ignore` w `firebase.json` plik wylatuje na produkcję — sprawdzone 2026-09-10.)

## 7. Profil i Design System

- Motywy: **Calm 🌊**, **Sharp ⚡**, **Playful 🎨**. Tryby: **Dzień ☀️** / **Noc 🌙**.
- Wybór języka interfejsu i domyślnej pary językowej. Wektorowe flagi SVG.
- Karta **Polityka prywatności** otwierająca `mini.verbigem.com/privacy/` w przeglądarce, w języku interfejsu.
- Karta **Kontakt** (`profile_contact_label`) przed kartą wylogowania: tekst
  „Masz pytania, uwagi lub pomysły związane z aplikacją - podziel się nimi. Chętnie wysłuchamy."
  + przycisk `profile_contact_cta` otwierający `mini.verbigem.com/contact/<uiLang>/` w przeglądarce
  (`AppLinks.contact`). Długie przytrzymanie → `help_profile_contact`. **Ten sam tekst i ten sam
  formularz są w webappie** (`ProfilePage.tsx`, `/contact/`) — zmiana treści wymaga obu stron.
- **Nick jest unikalny w całej aplikacji.** Karta nicku zapisuje przez rezerwację, nie bezpośrednio
  do profilu: `NicknameRepository.claim()` robi transakcję na `nicknames/{sha256(trim+lowercase)}`,
  a dopiero po sukcesie `AuthRepository.updateProfile`. Zajęty nick → komunikat
  `profile_nickname_taken` pod polem (bez zmiany profilu), brak sieci → `profile_nickname_error`.
  ⚠️ Kolejność jest istotna: zapis profilu przed rezerwacją zostawiłby użytkownika z nickiem,
  którego nie ma w indeksie unikalności. ⚠️ ID dokumentu to **SHA-256**, nie sam nick — nick może
  zawierać `/`, a ID nie może zaczynać się od `__`. Ten sam algorytm jest w webappie
  (`src/auth/nicknameService.ts`); rozjazd = dwie rezerwacje tego samego nicku.
- Karta **O aplikacji** (`R.string.about_label`) pod polityką prywatności: `Wersja <versionName> · build <versionCode>` z `BuildConfig` + link **Co nowego** otwierający `AppLinks.whatsNew(uiLang)` — czyli `https://mini.verbigem.com/android/changelog[-<lang>].html` (hostowany statycznie, ten sam skrypt `genChangelogHtml.mjs` co strona www; **NIE** `/whatsnew/` — Firebase catch-all rewrite serwowałby stronę webappy zamiast treści).

---

## 📋 Czat i Kontakty — dokument towarzyszący

Rozbudowa Czatu i Kontaktów jest rozpisana w **[`docs/czat-i-kontakty.md`](czat-i-kontakty.md)**: diagnoza stanu, macierz możliwości technicznych, architektura docelowa, ryzyka. **Prace nad czatem i kontaktami zaczynamy od lektury tamtego pliku.**

Ustalenia, które wprost wynikają z tamtego planu i są już w kodzie:

- Tłumaczenie u odbiorcy (decyzja D1) + cache w `chat_translations`.
- **Nie da się zaimportować listy kontaktów z WhatsApp ani Telegrama** (brak API). Źródłem listy jest książka telefoniczna + opcjonalnie `.vcf`, a WhatsApp / SMS / e-mail są kanałami dostarczenia.
- **Weryfikacja numeru jest leniwa** — przy pierwszym wejściu w Czat lub Kontakty, nie przy rejestracji.
- Cloud Functions (matching + FCM) wymagają planu **Blaze** (wykupiony, decyzja D6).

---
