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
   - Natywne rozpoznawanie mowy **SpeechRecognizer (STT)** z podglądem na żywo.
   - Po skończeniu mowy: natychmiastowe tłumaczenie modelem **Hy-MT2-1.8B** i automatyczny odczyt głosem **TextToSpeech (TTS)** dla drugiej osoby.
   - Automatyczne przełączanie aktywnej strony rozmowy.

3. **Czat zdalny 1:1 (Chat)**:
   - Komunikator czasu rzeczywistego (Firebase Firestore).
   - Wiadomości są tłumaczone na urządzeniu nadawcy przed wysłaniem na język odbiorcy.
   - Podgląd oryginału i odsłuch audio.

4. **Kontakty i Znajomi (Contacts)**:
   - Wyszukiwanie użytkowników po nicku/e-mailu.
   - Przyjmowanie i odrzucanie zaproszeń.

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
   - OCR ma teraz **BottomNav menu** (dodany `Screen.Ocr.route` do `showBottomNav` w `AppNavigation`),
     więc użytkownik może przejść do niego z innych ekranów i wrócić.

6. **Profil i Design System**:
   - Motywy: **Calm 🌊**, **Sharp ⚡**, **Playful 🎨**.
   - Tryby: **Dzień (Day ☀️)** / **Noc (Night 🌙)**.
   - Wybór języka interfejsu i domyślnej pary językowej.
   - Wektorowe flagi SVG dla wszystkich języków.

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
│   │   ├── local/                  # Room DB (HistoryEntity, HistoryDao, OcrHistoryEntity, OcrHistoryDao — lokalna, oddzielna historia OCR, bez syncu; TtsConfigEntity, TtsConfigDao, PendingDeleteEntity, PendingDeleteDao) + DataStore Preferences
│   │   ├── model/                  # LangCode, UserProfile, ChatMessage, Friendship, EngineChoice, TranslationHistory, TtsConfig
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
│       ├── screens/                # TranslatorScreen (+HistoryCard, ResultCard), ConversationScreen, ChatScreen, ContactsScreen, OcrScreen (+CropOverlay), ProfileScreen, LoginScreen
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

`UpdateManager` + dialog w `MainActivity` (`UpdatePromptHost`). Po starcie (raz, `LaunchedEffect`)
odczytuje `/android/version.json` z **Firebase Hosting** pod domeną `mini.verbigem.com`:
```
https://mini.verbigem.com/android/version.json
```
```json
{
  "versionCode": 15,
  "versionName": "1.0.14",
  "apkUrl": "https://mini.verbigem.com/android/app-debug-v15.apk",
  "playStoreUrl": "https://play.google.com/store/apps/details?id=com.verbigem.app",
  "onPlayStore": false,
  "minSupportedCode": 1,
  "updatedAt": "2026-09-01"
}
```
gdy `info.versionCode > currentVersionCode()` → `AlertDialog` (stringi: `update_available_title`,
`update_available_body`, `update_action`, `update_later`). W trakcie pobierania pokazuje się drugi dialog
z `LinearProgressIndicator` (`update_downloading_title` / `update_downloading_body`) — **naprawiono
progress bar**: wcześniej dialog czytał `progressState.value` bezpośrednio (surowy read `StateFlow.value`,
który NIE rejestruje obserwatora Compose), więc Composable się nie rekomponował i pasek stał w miejscu
(zawsze 0%). Teraz dialog używa `progress by progressState.collectAsState()` (odczyt `val progress`
zadeklarowany w `StartupGate`) — każda zmiana `progress` (0f..1f, lub `-1f` = nieoznaczony) wywołuje
rekompozycję i pasek idzie do przodu. `UpdateManager.downloadAndInstall` emituje postęp przez
`MutableStateFlow<Float?>` (`null` = nie zaczął, `-1f` = bez Content-Length, `0f..1f` = ułamek).

**Źródło pliku update:** katalog `dist/android/` w projekcie webapp (`verbigem/mini`), deployowany
przez `firebase deploy --only hosting --project mini-verbigem` (ten sam projekt Firebase `mini-verbigem`
co webappa i Firestore). `dist/android/version.json` generowany jest automatycznie przy każdym buildzie
Vite (plugin `injectBuildId` w `vite.config.ts`) — nie trzeba ręcznie edytować JSON-a.
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
   ustaw `versionCode` = N, `versionName` = nowa nazwa, `apkUrl` = `https://mini.verbigem.com/android/app-debug-vN.apk`,
   `updatedAt`. **NIE edytuj ręcznie `dist/android/version.json`** — ten plik jest generowany na nowo przy każdym
   `npm run build` z wartości z `vite.config.ts`. Ręczna edycja dist zostałaby nadpisana przez build i rozjechała się
   z wgrywanym APK (serwer podawałby starszy `versionCode` niż zainstalowana apka → brak promptu o update).
5. `cd verbigem/mini && npm run build` — `vite.config.ts` wygeneruje automatycznie
   `dist/android/version.json` z nowym `versionCode`/`updatedAt`.
   - ⚠️ **Vite czyści `dist/` przed buildem** (`emptyOutDir` = true, bo `dist` leży w katalogu
     projektu) — **wszystkie `dist/android/*.apk` znikną**. Przed `npm run build` zrób kopię
     zapasową katalogu `dist/android/` i przywróć go po buildzie, inaczej zdeployujesz
     sam `version.json` bez APK (albo — gdy `dist` jest niekompletny — zdeployujesz hosting
     bez `index.html` i wywalisz stronę).
6. `cd verbigem/mini && firebase deploy --only hosting --project mini-verbigem`.
7. Gotowe — appka na telefonie sama wykryje nową wersję przy starcie i zaproponuje update.
8. Po deployu **zweryfikuj**: `https://mini.verbigem.com/android/version.json` musi zwracać
   nowy `versionCode`. Nagłówki w `firebase.json` są tak ustawione, że `/android/version.json`
   ma `no-store` (podgląd w przeglądarce jest wiarygodny bez twardego reloadu).

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
  - `users/{uid}/history/{syncId}` — historia tłumaczeń (sync, last-write-wins).
  - `users/{uid}/friendships`, `chats/{chatId}/messages` — czat/kontakty.
  - `app_config/tts` — konfiguracja TTS Pro (modele OpenRouter + apiKey).
  - `app_config/update` — metadane auto-update (versionCode, apkUrl, onPlayStore).
- **CLI:** `firebase.cmd` (npm global, `C:/Users/milo/AppData/Roaming/npm`). Zapis dokumentów:
  `firebase firestore:set` NIE istnieje w tym CLI — użyto Firestore REST API (Node.js) z
  tokenem z `%USERPROFILE%\.config\configstore\firebase-tools.json`.
- **Reguły dostępu (`firestore.rules` w repo):** deploy przez
  `firebase deploy --only firestore:rules --project mini-verbigem`. Wymagane reguły dla
  auto-update i TTS: `match /app_config/{doc} { allow read: if true; allow write: if false; }`
  (app czyta `app_config/update` i `app_config/tts` PRZED loginem). Bez tego dokumenty są
  blokowane (`PERMISSION_DENIED`) i app nie wykrywa update'u ani nie pobiera konfiguracji TTS.
- **Pliki konfiguracyjne w repo:** `firebase.json` (wskazuje `firestore.rules`), `.firebaserc`
  (projekt `mini-verbigem`).

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
   (konwencja: `versionName` = `1.0.(versionCode - 1)`, np. code 23 → name `1.0.22`).
2. Build APK (wrapper Javy, patrz wyżej) → `app/build/outputs/apk/debug/app-debug.apk`.
3. Kopia zapasowa `verbigem/mini/dist/android/` (**Vite ją wyczyści** w kroku 5).
4. APK → `verbigem/mini/dist/android/app-debug-vN.apk` (N = nowy versionCode).
5. `verbigem/mini/vite.config.ts` (plugin `injectBuildId`) → `versionCode`, `versionName`,
   `apkUrl` = `https://mini.verbigem.com/android/app-debug-vN.apk`.
6. `cd verbigem/mini && npm run build` → generuje `dist/android/version.json`;
   przywrócić APK-i z kopii zapasowej.
7. `cd verbigem/mini && firebase deploy --only hosting --project mini-verbigem`.
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
