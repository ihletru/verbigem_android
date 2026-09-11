# Verbigem Android — Natywny Tłumacz Hy-MT2 (100% Kotlin + NDK)

> 📦 **Aktualna wersja: `v1.0.68`** (versionCode 68) —
> [Releases](https://github.com/ihletru/verbigem_android/releases) ·
> [Historia zmian (CHANGELOG.md)](CHANGELOG.md) ·
> [Co nowego na stronie (6 języków)](https://mini.verbigem.com/android/changelog.html)

> ℹ️ Wersja trzymana jest w `app/build.gradle.kts` (`versionCode` / `versionName`).
> 🔢 **Konwencja od 2026-09-10: `versionCode` = patch w `versionName`** (68 → `1.0.68`).
> 🏪 **Google Play (flavor `play`):** ostatni zbudowany AAB to **1.0.68 (versionCode 68)** — czeka na ręczne wgranie. `versionCode` w Play **musi rosnąć**.
> 🤖 **Jesteś agentem AI? Zacznij od [`AGENTS.md`](AGENTS.md)** — tam jest tablica routingu.

Natywna aplikacja na Androida stworzona w **100% w języku Kotlin** (Jetpack Compose) z natywnym silnikiem wnioskowania **Hy-MT2-1.8B** (Tencent Hunyuan) w formacie **GGUF** przez mostek **C++/JNI (llama.cpp NDK)**.

Wzorowana na architekturze i funkcjach `mini.verbigem.com` (`verbigem/mini`).

---

## ⚠️ Ten plik jest tylko indeksem

Dawniej cała dokumentacja żyła tutaj — 1190 linii, 111 kB, cztery różne dokumenty zlepione w jeden (landing page + instrukcja + księga pułapek + dziennik decyzji). Nikt tego nie aktualizował: nagłówek wciąż pokazywał `v1.0.59`, gdy kod był na `1.0.68`.

**Treść została przeniesiona do [`docs/`](docs/). Ten plik trzyma tylko wizytówkę, quick start i mapę.**

**Po każdej zmianie większej niż kosmetycznej aktualizujesz właściwy plik w `docs/`, potem commit + push.**

---

## 📚 Mapa dokumentacji

| Plik | Co zawiera | Kiedy czytać |
|---|---|---|
| **[`AGENTS.md`](AGENTS.md)** | Tablica routingu: zadanie → plik do wczytania | Zawsze na początku sesji |
| [`docs/funkcje.md`](docs/funkcje.md) | Opis 7 ekranów (Translator, Rozmowa, Czat, Kontakty, OCR, QR, Profil) | Gdy dodajesz funkcję |
| [`docs/architektura.md`](docs/architektura.md) | Moduły, warstwy, synchronizacja Firestore | Gdy zmieniasz strukturę |
| [`docs/tlumaczenie.md`](docs/tlumaczenie.md) | Silniki, tłumaczenie online, Czytaj Pro, portfel, Paddle | Gdy ruszasz tłumaczenie |
| [`docs/jezyki-ui.md`](docs/jezyki-ui.md) | ZAKAZ hardkodowania tekstów, 6 języków, `uiLocale` | Gdy dodajesz tekst UI |
| [`docs/ikony-i-pomoc.md`](docs/ikony-i-pomoc.md) | klik = akcja, długie kliknięcie = pomoc; bottom nav | Gdy dodajesz ikonę |
| [`docs/reklamy.md`](docs/reklamy.md) | AdMob (apka) + AdSense (webapp) | Gdy ruszasz reklamy |
| [`docs/dystrybucja.md`](docs/dystrybucja.md) | AAB vs APK, auto-update APK, branding | Gdy wypuszczasz wersję |
| [`docs/firebase.md`](docs/firebase.md) | Projekt Firebase, App Check, push, Phone Auth | Gdy ruszasz Firebase |
| [`docs/functions.md`](docs/functions.md) | Cloud Functions, sekrety, webhook Paddle | Gdy deployujesz funkcje |
| [`docs/uruchomienie.md`](docs/uruchomienie.md) | Build, flow wydania, zasady repo | Gdy stawiasz środowisko / wydajesz |
| [`docs/prywatnosc.md`](docs/prywatnosc.md) | Polityka prywatności, dane, usuwanie konta | Gdy ruszasz dane użytkownika |
| [`docs/czat-i-kontakty.md`](docs/czat-i-kontakty.md) | Plan rozbudowy czatu i kontaktów | Gdy ruszasz czat |

Badania i wątki poboczne: `docs/SILNIK_PRO_RESEARCH.md`, `docs/PRO_MODEL_RESEARCH_2.md`.

Procedury operacyjne Play Store (wciąż w katalogu głównym): `PLAY_INTERNAL_TESTING.md`, `PLAY_CONSOLE_FILLIN.md`, `PLAY_PUBLISHING_PLAN.md`, `PLAY_RELEASE_NOTES_EN.md`, `Dystrybucja aplikacji Android i iPhone.md`.

---

## 🔥 Zanim zaczniesz — 8 żelaznych reguł

Skrót z linkami. Pełne uzasadnienie jest w plikach źródłowych — tam też je poprawiaj.

1. **Nie hardkoduj tekstów UI** — każdy string do `res/values-*/strings.xml` we wszystkich 6 językach. → [`docs/jezyki-ui.md`](docs/jezyki-ui.md)
2. **Wersja = patch.** `versionCode` = numer patch w `versionName` (68 → `1.0.68`). Jedno źródło prawdy: `app/build.gradle.kts`. → [`docs/dystrybucja.md`](docs/dystrybucja.md)
3. **NIGDY nie deployuj funkcji bez wylistowania nazw** — `firebase deploy --only functions` podmienia wszystko naraz. → [`docs/functions.md`](docs/functions.md)
4. **Nie nadpisuj istniejącej nazwy APK na `/android/**`** — tam jest cache `immutable`. → [`docs/dystrybucja.md`](docs/dystrybucja.md)
5. **Kolejność reklam: zgoda → SDK → reklama** (wymuszone kodem, nie konwencją). → [`docs/reklamy.md`](docs/reklamy.md)
6. **klik = akcja, długie kliknięcie = pomoc.** BottomNav maksymalnie 5 ikon. → [`docs/ikony-i-pomoc.md`](docs/ikony-i-pomoc.md)
7. **Dialog to osobna kompozycja** → używaj `LocalizedDialog` / `LocalizedAlertDialog`, inaczej `LocalContext` wraca do języka telefonu. → [`docs/jezyki-ui.md`](docs/jezyki-ui.md)
8. **Silnik CPU only.** Wątki = wszystkie rdzenie z kapą 8 (`CpuTopology` — zmierzone, nie wracaj do „tylko duże rdzenie"). GPU/OpenCL wyłączone: `GGML_BACKENDS="CPU"`, biała lista SoC pusta. → [`docs/tlumaczenie.md`](docs/tlumaczenie.md)

---

## 🚀 Quick start

Otwórz `c:\Users\milo\verbigem\android` w Android Studio, zsynchronizuj Gradle, uruchom na urządzeniu `arm64-v8a`. Build z linii (Windows — `gradlew.bat` bywa blokowany przez `cmd.exe`):

```bash
JAVA_HOME="C:/Users/milo/.jdks/jbr-21.0.11" ANDROID_HOME="C:/Users/milo/AppData/Local/Android/Sdk" \
  "C:/Users/milo/.jdks/jbr-21.0.11/bin/java.exe" \
  -classpath "gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain \
  assembleDebug --console=plain
```

Natywny build NDK (~2–3 min) kompiluje `libverbigem_llama.so`. Szczegóły i pełny flow wydania: **[`docs/uruchomienie.md`](docs/uruchomienie.md)**.

---

## 🗂 Repozytorium i zasady pracy

**Git:** `https://github.com/ihletru/verbigem_android` (branch `master`, prywatne). `.gitignore` wyklucza `build/`, `llama_master/`, `*.gguf`, `model_probe/`, `build_log*`, `crash_log*`, `local.properties`, `.workbuddy-ai/`.

**Po każdej zmianie większej niż kosmetyczna: zaktualizuj odpowiedni plik w `docs/`, potem commit + `git push`.**

Projekt `mini` (webapp + hosting) ma własne repozytorium (`verbigem-mini`, prywatne); deploy hostingu: `firebase deploy --only hosting --project mini-verbigem`.
