# Test wewnętrzny Verbigem w Google Play

Zastępuje `PLAY_CONSOLE_FILLIN.md`. Tamten był pisany pod **Closed testing**
(12 testerów × 14 dni), a idziemy ścieżką **Test wewnętrzny (Internal
testing)** — to dwie różne rzeczy i dlatego opis nie pasował do tego, co
widziałeś w konsoli.

---

## 1. Test wewnętrzny ≠ test zamknięty

|  | Test wewnętrzny | Test zamknięty |
|---|---|---|
| Testerzy | do 100 adresów | lista e-mail |
| Minimum | **1 osoba** | 12 (wymóg dla nowych kont przed produkcją) |
| Czas | od razu po recenzji aplikacji | min. 14 dni ciągłych |
| Cel | szybkie sprawdzenie buildu na realnych urządzeniach | spełnienie wymogu Google przed dopuszczeniem do produkcji |

**Na tym etapie wystarczy 2–3 osoby.** 12 testerów × 14 dni (wg researchu z
`PLAY_PUBLISHING_PLAN.md`, sierpień 2026) będzie potrzebne dopiero **przed
wyjściem do produkcji** — to osobny krok, nie teraz.

---

## 2. Fakty techniczne (z repo i z buildu — pewne)

| Parametr | Wartość |
|---|---|
| Pakiet | `com.verbigem.app` (flavor `play`) |
| Sideload | osobny pakiet `com.verbigem.app.sideload` (flavor `standalone`) — **nie instaluje się obok wersji Play** |
| Wersja | `versionCode` = `versionName` patch: **63 / `1.0.63`** (w `app/build.gradle.kts`). Play nie przyjmie powtórzonego `versionCode` — przy poprawce do już wgranej wersji podbij kod. |
| `minSdk` / `targetSdk` | 26 / 36 (wymóg Google od 31.08.2026 dla nowych aplikacji — spełniony) |
| Klucz uploadu | `app/release-keystore.jks`, alias `verbigem` |
| SHA-1 klucza uploadu | `1A:9B:77:0A:68:1D:77:F5:91:F4:5C:01:AA:FD:5C:74:FF:9C:8A:1F` |
| Certyfikat do wgrania | `play-upload-cert.pem` (klucz publiczny — bezpieczny) |
| Polityka prywatności | `https://mini.verbigem.com/privacy/` (działa, HTTP 200) |
| `extractNativeLibs` | `false` (w merged manifeście) — biblioteki `.so` mapowane wprost z APK, Play dba o wyrównanie przy generowaniu APK z AAB |
| AAB | `app/build/outputs/bundle/playRelease/app-play-release.aab` (~18,5 MB) |

---

## 3. Checklista konfiguracji — bez tego testerzy nie pobiorą apki

### Konfiguracja (Setup)
- [ ] **Podpisywanie aplikacji** (App integrity / App signing) — **włącz Play App
      Signing**. Albo wgraj `play-upload-cert.pem`, albo wybierz klucz
      wygenerowany przez Google. Bez tego Google nie podpisze APK i testerzy
      dostaną „aplikacja nie istnieje".
- [ ] **Dostęp do aplikacji / uprawnienia** — zadeklaruj kamerę, mikrofon i
      kontakty z uzasadnieniem (OCR, STT, wyszukiwanie znajomych).

### Zasady → Zawartość aplikacji (Policy → App content)
- [ ] **Bezpieczeństwo danych (Data safety)** — wg `PLAY_PUBLISHING_PLAN.md` §3.1:
      e-mail, historia tłumaczeń i OCR, zdjęcia (OCR lokalnie), audio (STT
      lokalnie); **udostępniane stronom trzecim**: tekst wysyłany do OpenRouter
      (tylko funkcje Pro). Zaznacz szyfrowanie w transmisji i możliwość usunięcia danych.
- [ ] **Ocena treści (Content rating)** — kwestionariusz IARC, kategoria
      Narzędzia / Produktywność, zaznaczyć reklamy (AdMob).
- [ ] **Odbiorcy (Target audience)** — 18+.
- [ ] **Reklamy (Ads)** — „Tak, używam AdMob".
- [ ] **Polityka prywatności** — URL `https://mini.verbigem.com/privacy/`.

### Obecność w sklepie (Store listing)
Wymagane, żeby recenzja w ogóle ruszyła:
- [ ] nazwa aplikacji (np. „Verbigem — tłumacz offline"),
- [ ] krótki opis (80 zn.) i pełny opis — PL (+ EN),
- [ ] ikona 512×512,
- [ ] min. 2 screenshoty telefonu,
- [ ] grafika 1024×500.

> Dopóki tego nie ma, Play trzyma aplikację ze statusem „Niesprawdzona" i
> **tymczasową nazwą** `…com.verbigem.app (unreviewed)`. Testerzy nie mogą wtedy
> pobrać działającej wersji.

---

## 4. Jak dodać testera (kolejność ma znaczenie)

1. Play Console → **Testowanie → Test wewnętrzny → Testerzy**.
2. Dodaj adresy e-mail testerów (muszą to być konta Google: Gmail lub Workspace).
3. Skopiuj **link udostępniania** — `https://play.google.com/apps/testing/com.verbigem.app`.
4. Każdy tester musi zrobić dwie rzeczy, w tej kolejności:
   1. otworzyć link **na telefonie, zalogowanym na to samo konto Google**,
      którego e-mail podałeś → kliknąć **„Zostań testerem"**,
   2. dopiero potem wejść w **„Pobierz w Sklepie Play"** / „Otwórz w Sklepie Play".

⚠️ **Najczęstszy powód „aplikacja nie istnieje":** tester nie kliknął „Zostań
testerem" albo otworzył link na innym koncie Google niż zaproszony.

---

## 5. Diagnostyka — objaw → prawdziwa przyczyna

| Objaw | Przyczyna | Co robić |
|---|---|---|
| „Aplikacja nie istnieje" | tester nie zaakceptował linku / inne konto Google | wysłać link ponownie, sprawdzić konto Google na telefonie |
| „Pobrało się 8 MB, a APK ma 30 MB" | **to normalne.** Play pokazuje rozmiar skompresowanego modułu `base` AAB (~8,3 MB). Debug-sideload APK (~30 MB) ma nieskompresowane `.so`. | nic — nie jest to objaw błędu |
| Instalacja kończy się błędem od razu | stara wersja sideload `com.verbigem.app` jest zainstalowana i ma **inny podpis** → `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | odinstalować starą wersję przed instalacją z Play |
| Apka się instaluje, ale wywala się przy starcie; w logu `NullPointerException` w `ActivityThread.performLaunchActivity:4324` | pole Activity zainicjowane `applicationContext` **w deklaracji** — `applicationContext` jest `null` przed `attachBaseContext`. Android 14+ rzuca NPE (starsze wersje zwracały `sEmptyContext`). | `by lazy { ... }` albo inicjalizacja w `onCreate` po `super.onCreate()`. **Naprawione w v1.0.63** (`MainActivity.updateManager`) |

---

## 6. Historia tego dokumentu (żeby nie powtórzyć błędu)

`PLAY_CONSOLE_FILLIN.md` pisałem z głowy, zakładając ścieżkę Closed testing i
zgadując nazwy menu. Efekt: dokument oderwany od rzeczywistości. Ten plik
powstaje **po** przejściu realnych kroków i zawiera tylko:
- fakty z repo i z buildu (sekcja 2),
- wymagania Google, które są stałe (sekcja 3),
- objawy, które faktycznie zaobserwowaliśmy (sekcja 5).

Nazwy pozycji menu w konsoli mogą się różnić — poniżej otwarte pytania.

---

## 7. Do potwierdzenia z Twojej konsoli (uściślę dokument)

1. Czy **Podpisywanie aplikacji** jest już włączone?
2. Które pozycje w **Zawartość aplikacji** są wypełnione, a których brakuje?
3. Czy aukcja w **Obecność w sklepie** jest zapisana (nazwa / opis / ikona)?
4. Ilu testerów jest na liście i czy kliknęli „Zostań testerem"?
