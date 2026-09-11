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
| Sideload | osobny pakiet `com.verbigem.app.sideload` (flavor `standalone`) — inny pakiet, więc **instaluje się obok** wersji Play. Konflikt podpisu (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`) grozi tylko wtedy, gdy na telefonie siedzi **stary** build sideload o pakiecie `com.verbigem.app`. |
| Wersja | `versionCode` = `versionName` patch: **64 / `1.0.64`** (w `app/build.gradle.kts`). Play nie przyjmie powtórzonego `versionCode` — przy poprawce do już wgranej wersji podbij kod. |
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

### Firebase — odcisk podpisu (bez tego Google Sign-In nie działa w wersji z Play)

Google Play podpisuje aplikację **własnym kluczem** (Play App Signing) — innym niż klucz uploadu i innym niż debug. Firebase musi znać ten odcisk; bez niego logowanie przez Google w wersji z Play kończy się błędem „No credentials available", choć w APK sideload działa bez zarzutu.

| Klucz | SHA-1 |
|---|---|
| **Play App Signing** — tym podpisany jest APK, który dostaje telefon | `b09748e2d639f0e28f89b013f5b6f983d70babc7` |
| Klucz uploadu — tym podpisujesz AAB | `1A:9B:77:0A:68:1D:77:F5:91:F4:5C:01:AA:FD:5C:74:FF:9C:8A:1F` |
| Debug — sideload z naszej strony | `ec9deb58cdf2483a7efe2b73c2c7901b9d6d3ccc` |

**Do zrobienia:** Firebase Console → ⚙️ Project settings → **Your apps** → `com.verbigem.app` → **Add fingerprint** → wklej SHA-1 Play App Signing. Dodaj też SHA-256: `c92aba6c54cfe933cd411eca8d1138f8ed9795058190345fe4ce424ba88033b7`. Propagacja zajmuje kilka minut i **nie wymaga przebudowy AAB**.

Uwaga: `app/google-services.json` ma w polu `certificate_hash` odcisk **debug**. To pole czyta tylko plugin Gradle (kontrola spójności) — nie bierze udziału w autoryzacji. O autoryzacji rozstrzyga wyłącznie rejestracja w konsoli Firebase, dlatego sama podmiana pliku niczego nie naprawi.

**Drugi odcisk, o którym łatwo zapomnieć — `com.verbigem.app.sideload`.** Aplikacja ma dwa smaki i **dwa wpisy w Firebase**. Ten drugi („Verbigem Sideload") nie ma **żadnego** klienta OAuth dla Androida — w `google-services.json` figuruje wyłącznie `client_type: 3` (web). Bez klienta typu 1 dla pary *pakiet + odcisk* Google odrzuca logowanie przez Google w wersji sideload, mimo że klucz web jest obecny.

**Do zrobienia:** Firebase Console → ⚙️ Project settings → **Your apps** → `com.verbigem.app.sideload` → **Add fingerprint** → wklej odcisk **debug**: `ec9deb58cdf2483a7efe2b73c2c7901b9d6d3ccc`.

Kolejność ma znaczenie przy wydaniu na stronę: **najpierw odcisk, potem deploy**. APK z pakietem `com.verbigem.app.sideload` bez tego wpisu daje użytkownikom stronę logowania, na której Google nie działa (e-mail/hasło i SMS działają normalnie).

⚠️ Do 2026-09-10 strona podawała do pobrania APK z pakietem **`com.verbigem.app`** — czyli tym samym co wersja z Google Play, tylko podpisany kluczem debug. Skutek: nie dało się mieć obu wersji naraz (ta sama nazwa pakietu, inny podpis), a instalacja jednej blokowała drugą. Poprawione w `mini/` (commit `bf2f2ab`): APK pochodzi teraz ze smaku `standalone` i ma pakiet `.sideload`.

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
| Przycisk „Instalar" wyszarzony, po kliknięciu „No se puede descargar" / „Nie można pobrać" | Play nie mówi wprost, co jest nie tak — w teście wewnętrznym komunikaty są celowo ogólnikowe. Sprawdzać po kolei, nie zgadywać. | patrz lista niżej |
| W wersji z Play są tylko 2 języki interfejsu, a w APK było 6 | Play dzieli AAB **także po języku** — telefon dostaje tylko swój język + angielski. APK sideload to jeden plik ze wszystkimi. | **Naprawione w v1.0.64** — `bundle { language { enableSplit = false } }` w `app/build.gradle.kts`. Weryfikacja: `BundleConfig.pb` w AAB musi zawierać `\x08\x03\x10\x01`. |
| Okno (dialog) w języku telefonu, a nie w wybranym w aplikacji | wnętrze `Dialog { }` to osobna kompozycja — `LocalContext` wraca tam do bazowego Activity. | **Naprawione w v1.0.64** — wszystkie okna przeszły na `LocalizedDialog` / `LocalizedAlertDialog` (`ui/components/LocalizedDialog.kt`); wcześniej 8 z nich było bez opakowania. Nowe okna pisać tylko przez te dwa komponenty. |
| Tester ma „0 wykluczeń" w katalogu urządzeń, a mimo to nie pobiera | ABI i wersja Androida są w porządku — zostają przyczyny „miękkie". | patrz lista niżej |

### „Nie można pobrać" — kolejność sprawdzania

1. **Tester nie kliknął „Zostań testerem"** w linku z maila. Sam link najpierw
   pyta o zgodę, dopiero potem przekierowuje do Sklepu — jeśli tester zamknął
   stronę na pytaniu o zgodę, w konsoli wygląda jak tester, ale nie jest
   aktywowany.
2. **Tester instaluje z wyszukiwania w Sklepie, nie z linku testerskiego.**
   Play pokazuje testerom stronę apki w wynikach wyszukiwania, ale przycisk
   „Instaluj" jest tam zablokowany. Musi wejść przez link opt-in → „Pobierz ze
   Sklepu Play".
3. **Inne konto Google w telefonie** niż to na liście testerów. Sklep Play
   używa tego, które jest wybrane w Sklepie (ikona konta), nie tego, które
   podał tester. Sprawdzić: Ustawienia → Konta → Google.
4. **Cache Sklepu Play** — Ustawienia → Aplikacje → Sklep Play → Wyczyść
   pamięć podręczną i Wyczyść dane → restart telefonu.
5. **Kraj konta testera** poza dystrybucją apki. Tego **nie widać** w katalogu
   urządzeń — to osobne ustawienie (Play Console → Kraje/regiony).
6. **Brak wolnego miejsca** na telefonie.

Diagnostyka bez zgadywania: **Play Console → Test and release → App bundle
explorer → wybierz build → zakładka „Device catalog"**, filtr „Wykluczone" —
pokazuje modele urządzeń i **powód** odcięcia (ABI / wersja Androida / RAM).
Katalog pokazuje modele z bazy Google, więc „0 wykluczeń" nie dowodzi, że
telefon konkretnego testera jest w porządku — ale wyklucza całą klasę przyczyn
technicznych.

⚠️ **AAB jest budowany wyłącznie dla `arm64-v8a`** (`ndk.abiFilters` w
`app/build.gradle.kts`). Telefon 32-bitowy (ARMv7) nie zainstaluje apki, choć
w katalogu urządzeń może nie być wprost wykluczony. Dodanie `armeabi-v7a`
oznacza większy AAB **i** problem z modelami LLM: 32-bit to limit adresowania
ok. 4 GB, więc duże modele (7B) mogą się nie zmieścić w pamięci procesu.

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
