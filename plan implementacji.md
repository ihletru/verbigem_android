# Plan implementacji - Verbigem Android

## Status (2026-08-28)

### Zrobione
- [x] i18n (polskie stringi)
- [x] Google Sign-In (default_web_client_id)
- [x] Crash profilu (Timestamp deserialization)
- [x] Firestore PERMISSION_DENIED (usuniete zabronione pola z ensureProfile)
- [x] Natywne inference w C++ (llama.cpp + JNI), pelna pipeline: tokenize -> decode -> greedy sample -> detokenize
- [x] Przyciski UI: kopiuj, udostepnij (share), przeczytaj (TTS) w oknie wyniku
- [x] Model FAST = 1.25Bit ~440MB (Tencent Hy-MT2-1.8B-1.25Bit-GGUF)

### Problemy rozwiązane w tym cyklu
- [x] **Model się ładuje i tokenizuje** (STQ1_0=42 fix + chat template)
  - Weryfikacja na telefonie w toku (PL->EN test)
- [x] **Error text generyczny** (bez nazwy modelu): "Model nie jest zaladowany. Pobierz model w aplikacji."

### Do zrobienia / odlozone
- [ ] Banner reklamowy (odlozony - wymaga rejestracji appki w Google Play)
- [ ] Weryfikacja na telefonie: czy 1.25Bit faktycznie sie laduje i tlumaczy po powyzszej zmianie
- [ ] Testy: PL->EN, EN->PL, inne pary jezykowe
- [ ] Model ACCURATE (Q4_K_M ~1.1GB) - osobny plik, nie testowany w tym cyklu

## Architektura
- Kotlin 100% + Jetpack Compose
- NDK: llama.cpp (C++) przez JNI (libverbigem_llama.so)
- Modele GGUF pobierane z HuggingFace (Tencent) do `filesDir/models/`
- Firestore (Auth + Chat + Profile), Room (historia), ML Kit (OCR), DataStore (prefs)

## Wymagania uzytkownika (stale)
- **Model FAST = 1.25Bit 440MB** (non-negotiable, niezmienny)
- n_gpu_layers = 32 (NIE zmieniac na 0 - czysty CPU to glupota)
- Przy kazdej zmianie kodu: aktualizuj README.md i plan implementacji.md
- Nie podejmowac kluczowych decyzji (model/arch/lib) bez pytania
