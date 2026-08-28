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
   - Automatyczny in-app model downloader z Hugging Face.
   - Historia ostatnich tłumaczeń w lokalnej bazie **Room Database (SQLite)**.

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

## 🚀 Jak uruchomić projekt

1. Otwórz katalog `c:\Users\milo\verbigem_android` w **Android Studio**.
2. Poczekaj na automatyczną synchronizację Gradle (`Sync Project with Gradle Files`).
3. Podłącz urządzenie z Androidem lub uruchom emulator (z obsługą `arm64-v8a` lub `x86_64`).
4. Kliknij **Run** (Zielony trójkąt / `Shift + F10`) lub zbuduj APK:
   ```bash
   ./gradlew assembleDebug
   ```
