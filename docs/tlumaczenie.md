> 📄 Fragment dokumentacji Verbigem Android. Indeks: [README.md](../README.md)

# 🔤 Jak działa tłumaczenie

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
  **Doładuj portfel**, który otwiera dialog z 3 pakietami pokazującymi **realne ceny**
  `$3 / $5 / $10` (klucze `topup_3` / `topup_5` / `topup_10`, typy `wallet3/5/10`).
  1 USD zapłaty = 1 USD salda, więc „kredyty" w starym dialogu były w istocie centami
  (300/500/1000) i wprowadzały w błąd — od v1.0.66 pokazujemy po prostu cenę, jak na stronie.
  `ProfileViewModel.topUp(type)` woła Cloud Function **`createCheckout`**
  (Paddle, LIVE), tworzącą transakcję z `customData.uid` i zwracającą
  `checkout.url`; apka otwiera go w przeglądarce. Po opłaceniu `paddleWebhook`
  (`transaction.completed`, `customData.type` zaczynające się na `wallet`) dopisuje
  `wallet.creditsCents` — portfel odświeża się na żywo (snapshota `users/{uid}`).
  ⚠️ `walletTopUp` to funkcja **admin-only** (ręczne dopisywanie) — NIE używana z apki.

- ⚠️ **`checkout.url` MUSI być ustawione jawnie (v1.0.66).** `createCheckout` wysyła
  `checkout: { url: 'https://mini.verbigem.com/checkout' }` do `paddle.transactions.create`.
  Bez tego Paddle podstawia **domyślny payment link** — w naszym przypadku
  `https://mini.verbigem.com?_ptxn=<id>`, czyli **landing bez Paddle.js**: przeglądarka
  pokazywała stronę główną i kasa nigdy się nie otwierała („płatność przekierowuje do
  webapp zamiast do Paddle"). Paddle **nie serwuje własnej kasy na naszej domenie** —
  zwraca `<checkout.url>?_ptxn=<id>` i wymaga, żeby ta strona ładowała Paddle.js.
  Domena musi być **zatwierdzona w Paddle → Checkout domains** (inaczej 400).
- **Strona kasy `mini.verbigem.com/checkout`** (`mini/src/billing/CheckoutPage.tsx`) —
  trasa **publiczna** w `src/App.tsx`, poza `RequireAuth`: z apki nikt nie ma sesji
  w przeglądarce, a `PaddleListener` montuje się dopiero wewnątrz `RequireAuth`.
  Strona czyta `_ptxn` z URL-a, **usuwa go przez `history.replaceState` jeszcze przed**
  `initializePaddle()` (inaczej Paddle.js sam otworzyłby kasę i otworzyłyby się dwie),
  po czym woła `Paddle.Checkout.open({ transactionId, settings: { variant: 'one-page' } })`.
  Brak `txn` albo brak tokenu → ekran błędu z guzikiem powrotu (klucze `checkout.*`).
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
