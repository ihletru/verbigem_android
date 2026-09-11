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
│   │   ├── local/                  # Room v10, JEDEN PLIK NA KONTO (verbigem_db_<uid>,
│   │   │                           #   AccountScope): History, OcrHistory, TtsConfig, PendingDelete,
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

**Migracje Room (skrót):** v2→v3 `pending_deletes` (kolejka tombstone'ów) · v3→v4 `ocr_history` (+ kolumna `collection` w `PendingDeleteEntity`) · v4→v5 naprawa zepsutych v3/v4 (guard `PRAGMA`) · v5→v6 cztery tabele czatu · v6→v7 `chat_hidden` · v7→v8 `external_contacts` + `external_outbox` · v8→v9 `glossary` (słownik użytkownika, patrz niżej) · **v9→v10** `chat_outbox` dostaje `type`/`attachmentUrl`/`localUri`/`transcript`/`ocrText` (README §5.4).

⚠️ **Room WALIDUJE schemat PO migracji — `CREATE TABLE` z głowy to rosyjska ruletka.** Po każdej migracji Room odpala `onValidateSchema` i rzuca `IllegalStateException: Migration didn't properly handle: <tabela>` przy każdej różnicy względem encji. **Autorytatywne DDL jest w `app/build/generated/ksp/debug/java/.../AppDatabase_Impl.java` — skopiuj je stamtąd.** Room porównuje `name` / `notNull` / `affinity` / `primaryKeyPosition` oraz `defaultValue`, ale `defaultValue` **tylko wtedy, gdy encja sama je deklaruje** (`@ColumnInfo(defaultValue=…)`). Zwykłe `= ""` w Kotlinie to domyślna wartość konstruktora, nie kolumny — generowany `TableInfo.Column` dostaje `null`, więc klauzula `DEFAULT` w migracji jest tolerowana (tak żyje `MIGRATION_7_8`). Nie ma `fallbackToDestructiveMigration` i nie wolno go dodać — to kasuje dane użytkowników.

## Baza lokalna jest PER KONTO (`AccountScope`, v1.0.68)

Do v1.0.68 istniała **jedna wspólna baza** `verbigem_db`, więc na urządzeniu z kilkoma
kontami (Milosz testuje na kilku) każdy zalogowany widział historię tłumaczeń i OCR
poprzednika. Firestore od zawsze był per konto (`users/{uid}/…`) — tylko kopia na
urządzeniu nie była. To był prawdziwy wyciek między kontami, nie kosmetyka.

Naprawa: **jeden plik bazy na konto** — `verbigem_db_<uid>` (`verbigem_db_anon` przed
zalogowaniem). `AccountScope` (`data/local/AccountScope.kt`) trzyma aktualny uid,
a `AppDatabase.getInstance(context)` buduje albo reużywa plik dla niego. Sygnatura
została `getInstance(context)`, więc ~14 miejsc wywołania się nie zmieniło — konto jest
stanem otoczenia, nie parametrem.

Kto ustawia konto:
- `VerbigemApplication.onCreate` — `install(this)` + seed z `currentUser`,
- listener `addAuthStateListener` — pokrywa logowanie **i** wylogowanie,
- `AppNavigation` przed `NavHost` — to ostatnie jest istotne: na zimnym starcie listener
  może jeszcze nie wystrzelić, a ViewModel zdążyłby zbudować repozytorium na złym pliku
  i pokazać pustą historię do końca życia.

⚠️ **Nie zamykamy starej instancji przy zmianie konta.** ViewModele trzymają DAO,
a zamknięcie pliku pod nimi to `IllegalStateException: attempt to re-open an
already-closed object`. Przy wylogowaniu nawigacja robi `popUpTo(0)`, więc ViewModele
giną — ale kolejność nie jest gwarantowana, więc plik zostaje otwarty. Otwartych
plików jest tyle, ile kont w jednej sesji procesu.

⚠️ **Znaczniki synchronizacji są per uid** (`PreferencesManager.lastSyncHistory(uid)`).
Wspólny znacznik zostawiony wysoko przez konto A ukrywa **całą** historię konta B,
bo pull to `whereGreaterThan("updatedAt", lastSync)` — B wyglądałoby na puste, mimo że
Firestore ma dane.

Migracja danych: stara `verbigem_db` jest **adoptowana** przez pierwsze konto, które
zaloguje się po aktualizacji (`renameTo` na `verbigem_db_<uid>` wraz z `-shm`/`-wal`).
Bez tego przepadłyby tabele, których Firestore nie odtworzy: glosariusz, kontakty
zewnętrzne, klucz TTS.

⚠️ **Nadal wspólne na urządzeniu (świadomie):** motyw, język interfejsu, pary języków.
**Do rozważenia:** `KEY_OPENROUTER_KEY` w DataStore też jest wspólny — klucz API jednego
konta jest widoczny dla drugiego na tym samym telefonie.

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
