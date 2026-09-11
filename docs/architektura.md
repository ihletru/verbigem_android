> 📄 Fragment dokumentacji Verbigem Android. Indeks: [README.md](../README.md)

# 🛠️ Architektura techniczna

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
