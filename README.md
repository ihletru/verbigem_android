# Verbigem Android — Natywny Tłumacz Hy-MT2 (100% Kotlin + NDK)

Natywna aplikacja na system Android stworzona w **100% w języku Kotlin** z wykorzystaniem **Jetpack Compose** oraz dedykowanego, natywnego silnika wnioskowania **Hy-MT2-1.8B** (Tencent Hunyuan) w formacie **GGUF** przez mostek **C++/JNI (llama.cpp NDK)** z akceleracją sprzętową ARM NEON oraz Vulkan GPU.

Wzorowana na architekturze i funkcjach `mini.verbigem.com` (`lingua-line/mini`).

---

## 🌟 Kluczowe funkcje

1. **Translator (Ekran główny)**:
   - Tłumaczenie pomiędzy 6 językami: **Polski (PL)**, **Angielski (EN)**, **Hiszpański (ES)**, **Chiński (ZH)**, **Niemiecki (DE)**, **Turecki (TR)**.
   - Wybór silników:
     - ⚡ **Hy-MT2 Szybki** (TQ1.25 / 1.25Bit ~440 MB) — najlżejszy, działa na słabszych telefonach,
     - 🎯 **Hy-MT2 Dokładny** (Q4_K_M ~1.1 GB) — bezkompromisowa jakość WMT,
     - ⚖️ **Oba (porównaj)** — jednoczesne generowanie obu wersji,
     - ☁️ **API online** — chmurowy fallback DeepSeek z portfelem.
   - Natywne Text-to-Speech (🔊) i kopiowanie do schowka.
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
   - **Auto-aktualizacja APK:** Firestore (`app_config/update`) trzyma najnowszy
     `versionCode` + link do APK (GitHub Releases). App po wykryciu nowszej wersji
     pyta o zgodę, pobiera i instaluje; po rejestracji w Google Play otwiera sklep.

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
│   ├── MainActivity.kt             # Punkt wejścia i Edge-to-Edge Compose
│   ├── VerbigemApplication.kt      # Inicjalizacja Firebase
│   ├── data/
│   │   ├── local/                  # Room DB (HistoryEntity, HistoryDao) + DataStore Preferences
│   │   ├── model/                  # LangCode, UserProfile, ChatMessage, Friendship, EngineChoice
│   │   └── repository/             # AuthRepository, ChatRepository, HistoryRepository
│   ├── engine/
│   │   ├── HyMt2NativeEngine.kt    # Natywny silnik Hy-MT2 z promptem /no_think i czyszczeniem
│   │   ├── ModelDownloader.kt      # Pobieranie i cache modeli GGUF z Hugging Face
│   │   ├── SpeechManager.kt        # Natywne Android STT (SpeechRecognizer) + TTS
│   │   ├── OcrManager.kt           # Google ML Kit Text Recognition
│   │   └── OnlineApiEngine.kt      # Chmurowy proxy DeepSeek
│   ├── jni/
│   │   └── LlamaNativeBridge.kt    # JNI deklaracje external fun
│   └── ui/
│       ├── components/             # FlagIcon, LangSelect, BottomNav, EnginePicker, DownloadDialog
│       ├── navigation/             # AppNavigation, Screen
│       ├── screens/                # TranslatorScreen, ConversationScreen, ChatScreen, ContactsScreen, OcrScreen, ProfileScreen, LoginScreen
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
- **Sanityzacja:** `sanitizeTranslation()` na końcu usuwa ew. znaczniki `<think>`,
  wiodące cudzysłowy i etykiety typu "Tłumaczenie:", oraz bierze tylko pierwszy
  akapit (model czasem dopisuje wyjaśnienia).
- **Języki:** model wymaga **angielskich nazw** języków w prompcie
  (`LangCode.englishName`), nie kodów ISO.
- **Wydajność:** native lib budowany jako **Release** (`-DCMAKE_BUILD_TYPE=Release`)
  + KleidiAI; przy ~3–4 tok/s na telefonie ze słabszym ARM tekst pojawia się wyraz
  po wyrazie w akceptowalnym tempie.

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

---

## 🚀 Jak uruchomić projekt

1. Otwórz katalog `c:\Users\milo\verbigem_android` w **Android Studio**.
2. Poczekaj na automatyczną synchronizację Gradle (`Sync Project with Gradle Files`).
3. Podłącz urządzenie z Androidem lub uruchom emulator (z obsługą `arm64-v8a` lub `x86_64`).
4. Kliknij **Run** (Zielony trójkąt / `Shift + F10`) lub zbuduj APK:
   ```bash
   ./gradlew assembleDebug
   ```
