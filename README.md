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
│   ├── MainActivity.kt             # Punkt wejścia i Edge-to-Edge Compose + dialog auto-update
│   ├── VerbigemApplication.kt      # Inicjalizacja Firebase + startowa synchronizacja (SyncManager)
│   ├── data/
│   │   ├── local/                  # Room DB (HistoryEntity, HistoryDao, TtsConfigEntity, TtsConfigDao) + DataStore Preferences
│   │   ├── model/                  # LangCode, UserProfile, ChatMessage, Friendship, EngineChoice, TranslationHistory, TtsConfig
│   │   └── repository/             # AuthRepository, ChatRepository, HistoryRepository, ProTtsRepository, SyncManager, TtsConfigSync
│   ├── engine/
│   │   ├── HyMt2NativeEngine.kt    # Natywny silnik Hy-MT2 z promptem  i czyszczeniem
│   │   ├── ModelDownloader.kt      # Pobieranie i cache modeli GGUF z Hugging Face
│   │   ├── SpeechManager.kt        # Natywne Android STT (SpeechRecognizer) + TTS
│   │   ├── OcrManager.kt           # Google ML Kit Text Recognition
│   │   ├── OnlineApiEngine.kt      # Chmurowy proxy DeepSeek
│   │   ├── ProTtsEngine.kt         # Płatne TTS przez OpenRouter (/audio/speech)
│   │   └── UpdateManager.kt        # Auto-update: Firestore → DownloadManager → instalacja APK
│   ├── jni/
│   │   └── LlamaNativeBridge.kt    # JNI deklaracje external fun
│   └── ui/
│       ├── components/             # FlagIcon, LangSelect, BottomNav, EnginePicker, DownloadDialog, AdBannerView
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
- **Sanityzacja:** `sanitizeTranslation()` na końcu usuwa ew. znaczniki `<think>`,
  wiodące cudzysłowy i etykiety typu "Tłumaczenie:", oraz bierze tylko pierwszy
  akapit (model czasem dopisuje wyjaśnienia).
- **Języki:** model wymaga **angielskich nazw** języków w prompcie
  (`LangCode.englishName`), nie kodów ISO.
- **Wydajność:** native lib budowany jako **Release** (`-DCMAKE_BUILD_TYPE=Release`)
  + KleidiAI; przy ~3–4 tok/s na telefonie ze słabszym ARM tekst pojawia się wyraz
  po wyrazie w akceptowalnym tempie.

---

## 💎 Czytaj Pro (płatne TTS przez OpenRouter)

Funkcja dostępna tylko dla użytkowników Pro (`UserProfile.isPro`). Zamiast darmowego
`TextToSpeech` (offline) używa chmurowego API OpenRouter (`/v1/audio/speech`).

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
   - Usuwanie: lokalne wiersze, których `syncId` zniknął z remote (tylko gdy remote niepuste,
     by nie wyczyścić danych przy błędzie odczytu).
3. **TTS Pro** (`app_config/tts`) — przez `TtsConfigSync`.

**Schemat Room (wersja 2, migracja MIGRATION_1_2):**
- `translation_history`: dodano kolumny `syncId TEXT`, `updatedAt INTEGER` (domyślnie puste/0
  dla starych wierszy).
- nowa tabela `tts_config` (id, apiKey, defaultModelId, chineseModelId, defaultVoice, chineseVoice, updatedAt).
- `HistoryDao`: `insert`, `update`, `getBySyncId`, `upsertBySyncId`, `deleteById`, `deleteBySyncId`, `clearAll`.
- `TranslationHistory.create()` generuje `syncId` (UUID) + `updatedAt` (teraz).

**Filter logcat:** `SyncManager`.

---

## 📦 Auto-aktualizacja APK

`UpdateManager` + dialog w `MainActivity` (`UpdatePromptHost`). Po starcie (raz, `LaunchedEffect`)
odczytuje `app_config/update` z Firestore:
```
{
  "versionCode": 2,
  "versionName": "1.0.1",
  "apkUrl": "https://github.com/ihletru/verbigem_android/releases/download/v1.0.1/app-debug-v2.apk",
  "playStoreUrl": "https://play.google.com/store/apps/details?id=com.verbigem.app",
  "onPlayStore": false,
  "minSupportedCode": 1
}
```
Jeśli `info.versionCode > currentVersionCode()` → `AlertDialog` (stringi: `update_available_title`,
`update_available_body`, `update_action`, `update_later`).

**Ścieżki:**
- `onPlayStore == false` (AKTYWNA): `downloadAndInstall()` → `DownloadManager` pobiera APK
  do `getExternalFilesDir(DIRECTORY_DOWNLOADS)` → po `ACTION_DOWNLOAD_COMPLETE` instalacja
  przez `Intent.ACTION_INSTALL_PACKAGE` + `FileProvider` (`${packageName}.fileprovider`,
  ścieżka w `res/xml/file_paths.xml`). Wymaga uprawnienia `REQUEST_INSTALL_PACKAGES`.
- `onPlayStore == true` (po rejestracji w Google Play): `openPlayStore()` → otwiera sklep.

**Wymagane w manifestcie:** `REQUEST_INSTALL_PACKAGES`, `<provider>` FileProvider z
`android:authorities="${applicationId}.fileprovider"`.

**Filter logcat:** `UpdateManager`.

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

### Flow wydania (auto-update end-to-end)
1. Podbić `versionCode`/`versionName` w `app/build.gradle.kts`.
2. `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
3. `gh release create vX.Y.Z app-debug.apk` (repo `ihletru/verbigem_android`, prywatne).
4. Zaktualizować `app_config/update` w Firestore: `versionCode` + `apkUrl`
   (`https://github.com/ihletru/verbigem_android/releases/download/vX.Y.Z/app-debug.apk`).
5. Test: zainstaluj starszy APK (niższy versionCode) → otwórz → dialog update → pobierz nowy.

**Repozytorium git:** `https://github.com/ihletru/verbigem_android` (branch `master`, prywatne).
`.gitignore` wyklucza: `build/`, `llama_master/`, `*.gguf`, `model_probe/`, `build_log*`,
`crash_log*`, `app-debug-v2.apk`, `local.properties`.
