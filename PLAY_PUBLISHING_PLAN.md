# Plan publikacji Verbigem w Google Play (2026)

Status aplikacji: **niegotowa** (README: "jeszcze nie jest gotowa"), ale przygotowania
procesowe/techniczne można zacząć teraz. Ten dokument = plan + research, NIE jest
wdrożeniem. Zmiany w kodzie wykonujemy dopiero po akceptacji decyzji z sekcji 6.

Autor kontekstu: agent. Język techniczny; polskie opisy dla milo.

---

## 0. Profil wydawcy (USTALONE)

- **Osoba fizyczna**, nie firma.
- Rezydent **Paragwaju**, obywatel **Polski**.
- Konto Play Console: indywidualne (Personal).
- **Kraj konta = Paragwaj** (adres korespondencyjny paragwajski). Tożsamość potwierdzana
  **polskim dokumentem** (paszport/dowód). Adres musi być realny do odbioru ew. korespondencji.
- **Brak konta merchant** — funkcja Pro (OpenRouter TTS) to rozliczenie serwer→serwer
  poza aplikacją (klucz w Firestore, płatność przez OpenRouter). Nie używamy Play Billing,
  więc na tym etapie konto merchant NIE jest wymagane. Jeśli kiedyś dodamy zakupy w appce
  (subskrypcja Pro przez Play), będzie trzeba założyć merchant i podać dane podatkowe.

### 0.1 Strategia kanałów (USTALONE): OBA kanały
- **Play:** `applicationId = "com.verbigem.app"` (flavor `play`, bez `REQUEST_INSTALL_PACKAGES`,
  tylko `openPlayStore()`).
- **Sideload Firebase:** `applicationId = "com.verbigem.app.sideload"` (flavor `standalone`,
  zachowuje self-update z `mini.verbigem.com`).
- **UWAGA migracyjna:** istniejące instalacje sideload mają `com.verbigem.app` — po zmianie
  suffixu `.sideload` nie dostaną auto-updatu (inny package). Mała baza użytkowników →
  akceptowalne; trzeba ogłosić reinstall z nowym linkiem. `versionCode` wspólny licznik
  dla obu kanałów (Play wymaga tylko rosnącego w swoim package).
- Hosting polityki: **`mini.verbigem.com/privacy`** (ten sam Firebase Hosting, już pod
  Cloudflare).

---

## 1. Jak wygląda proces w 2026 (research)

Źródła: support.google.com/googleplay/android-developer, developer.android.com,
play.google.com/developer-content-policy (sierpień 2026).

### 1.1 Konto + weryfikacja tożsamości
- Jednorazowa opłata **$25** za założenie konta (w rynkach ustalonych; dla niektórych
  regionów / kont zakładanych po 2025 r. Google wprowadził też opłatę roczną — **zweryfikuj
  kwotę przy zakładaniu konta**).
- Wymagane dane: nazwa dewelopera, imię i nazwisko (legal name), e-mail kontaktowy,
  telefon kontaktowy, adres (proof of address), **dokument tożsamości ze zdjęciem**
  (polski paszport / dowód).
- Dla osoby fizycznej w Paragwaju: jako "country of residence" podaj Paragwaj (adres, na
  który przyjdzie ew. korespondencja). Tożsamość potwierdzasz polskim dokumentem.

### 1.2 Android Developer Verification (nowość 2026)
- Osobny od Play program: od **30 września 2026** weryfikacja dewelopera obowiązuje też
  apek sideloadowanych na certyfikowanych urządzeniach w wybranych regionach/sklepach.
- Dla publikacji w Play tożsamość i tak jest weryfikowana przy zakładaniu konta — ten
  punkt dotyka głównie **kanału sideload (Firebase)**, jeśli zostawiamy go obok Play.

### 1.3 Wymogi techniczne (co już mamy, a czego brakuje)
| Wymóg (2026) | Stan w repo | Uwaga |
|---|---|---|
| Target API 35 (od 31.08.2026 istniejące appki; nowe appki później 36) | `targetSdk = 35` ✅ | Już OK. Google sygnalizuje przejście **nowych apek na API 36** (prawd. jesień 2026 / 2027) — trzymaj w planie bump do 36 przed wgraniem. |
| 16 KB page size (nowe urządzenia Android 15+) | `-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON` ✅ | Native lib `libverbigem_llama.so` już buduje się z elastycznymi stronami. Sprawdzić APK Analyzerem po buildzie AAB. |
| Format **AAB** (nowe appki) | repo buduje `assembleDebug` (APK) ❌ | Play wymaga **Android App Bundle** → `bundleRelease` / `bundlePlayRelease`. |
| **Play App Signing** | brak (debug key) ❌ | Wymagane. Inny klucz niż sideload. |
| Data Safety form | brak ❌ | Obowiązkowy, musi być zgodny z polityką prywatności. |
| Polityka prywatności (URL) | brak ❌ | Obowiązkowa przed publikacją. |
| Content rating (kwestionariusz) | brak ❌ | Obowiązkowy. |
| Zamknięte testy (nowe konta) | — | **12 testerów, 14 dni ciągłych** przed przejściem do produkcji. |

### 1.4 Zablokowane uprawnienie — KRYTYCZNE
`REQUEST_INSTALL_PACKAGES` + `UpdateManager.downloadAndInstall()` (pobieranie APK z
Firebase Hosting i instalacja przez `ACTION_INSTALL_PACKAGE`) **łamie politykę Play**
("self-update using any method other than Google Play's update mechanism"). Play to
odrzuci przy recenzji. README już ma flagę `onPlayStore` → `openPlayStore()`, ale
**uprawnienie wciąż jest zadeklarowane w manifeście** niezależnie od flagi. Trzeba to
rozdzielić (sekcja 2.1).

---

## 2. Blokery techniczne do rozwiązania PRZED wgraniem

### 2.1 Rozdzielenie kanałów (Gradle product flavors)
Dodajemy dwa smaki:
- **`standalone`** (dotychczasowy kanał Firebase/sideload):
  - zachowuje `REQUEST_INSTALL_PACKAGES`,
  - `UpdateManager.downloadAndInstall()` aktywne (pobieranie APK z `mini.verbigem.com`),
  - `applicationId = "com.verbigem.app.sideload"` (suffix, by współistniał z wersją Play).
- **`play`** (Sklep Play):
  - **usuwa** `REQUEST_INSTALL_PACKAGES` z manifestu (`tools:node="remove"` w flavor-specific
    manifeście lub `tools:remove` na permission),
  - `BuildConfig.PLAY_BUILD = true` → `UpdateManager` **tylko** woła `openPlayStore()`
    (już zaimplementowane pod `onPlayStore`), kod pobierający APK wykluczony z tego smaku,
  - `applicationId = "com.verbigem.app"`.

Dzięki temu recenzja Play widzi apkę, która nie pobiera i nie instaluje APK spoza Sklepu.

### 2.2 Podpisywanie (release, nie debug)
- Play odrzuca debug-key. Potrzebny **release keystore**.
- Play: zapisz się w **Play App Signing** (Google trzyma klucz podpisujący; Ty wgrywasz
  upload key). To inne klucze niż sideload.
- Sideload: własny release keystore (ten sam co dziś używany do `app-debug-vN.apk`, ale
  jako release, z własnym `signingConfig`).
- **NIGDY nie commituj keystore'ów** — dopisać do `.gitignore`.

### 2.3 AAB + minSdk
- Play: `./gradlew bundlePlayRelease`.
- `minSdk = 26` (Android 8) — OK (Play min to 24).

### 2.4 Wersjonowanie
- Play wymaga **ściśle rosnącego** `versionCode`. Pierwszy upload Play może mieć dowolny
  `versionCode ≥ 1`, potem tylko w górę.
- Obecny `versionCode = 15`. Proponuję **wspólny licznik** dla obu kanałów (Play i sideload
  mają ten sam `versionCode`, różny `applicationId` → brak konfliktu sygnatur).
- `version.json` na `mini.verbigem.com/android/`: `UpdateManager` decyduje o akcji po
  `applicationId` (`.sideload` → pobierz APK; `.play` → otwórz Sklep). `playStoreUrl`
  już jest w JSON.

### 2.5 Zmiany stringów
Każda nowa/ zmieniona etykieta → aktualizacja we **wszystkich 6** `values*/strings.xml`
(zakaz hardkodowania — reguła z README).

### 2.6 Testerzy zamkniętych testów (milo NIE ma 12 osób)
Play wymaga **12 testerów przez min. 14 dni ciągłych** przed produkcją (dla nowych kont).
milo nie ma listy → opcje do realizacji:

- **Opcja A (najszybsza, polecana): zaprosić znajomych/rodzinę** przez e-mail w Play Console
  (Closed testing → Testers → "Create email list"). Potrzeba 12 adresów e-mail; tester
  klika link opt-in i instaluje z tracku testowego. Wymóg prawny to "ciągłe" 14 dni —
  w praktyce wystarczy, że testerzy raz dołączą i aplikacja leży w tracku; Google nie
  sprawdza aktywności, tylko czas od pierwszego dołączenia do 12 osób.
- **Opcja B: społeczności dev** — ogłoszenie na forach (np. r/androiddev, grupy
  Telegram/Discord polskich deweloperów) z prośbą o e-mail do testów. Ryzyko: obcy ludzie
  na Twoim tracku testowym (ale to tylko zamknięte testy, widoczne tylko dla zaproszonych).
- **Opcja C: usługi "get 12 testers"** (płatne platformy typu "14-day tester" z
  app-store-testing) — daje 12 fake/realnych testerów na 14 dni. Koszt, ale bez szukania
  ludzi. Tylko jeśli A/B zawiodą.

Rekomendacja: **Opcja A** (poproś w rodzinie/znajomych o 12 e-maili) — zero kosztów,
zgodne z zasadami Play, pełna kontrola.

---

## 3. Data Safety + Polityka prywatności

### 3.1 Co aplikacja zbiera/przetwarza (do zadeklarowania)
- **Email** (Firebase Auth) — wymagane do konta.
- **Historia tłumaczeń + OCR** (Firestore, `users/{uid}/history`, `ocr_history`) — treść
  generowana przez użytkownika.
- **Kontakty / znajomi** (Firestore `friendships`).
- **Zdjęcia** (OCR z aparatu/galerii) — **przetwarzanie na urządzeniu** (ML Kit), ale
  przetłumaczony tekst trafia do Firestore.
- **Audio / mikrofon** (STT `SpeechRecognizer`) — na urządzeniu, wynik tekstowy idzie dalej.
- **Tekst wysyłany do OpenRouter** (tylko gdy użytkownik użyje Pro TTS) — **udostępniany
  stronie trzeciej** (OpenRouter). Zadeklarować jako "shared with third parties".
- **Model GGUF** pobierany z Hugging Face — plik modelu, nie dane użytkownika.

### 3.2 Polityka prywatności (URL)
Musi istnieć publiczny URL. Opcje:
- podstrona na `mini.verbigem.com/privacy` (ten sam Firebase Hosting),
- Google Sites,
- GitHub Pages (repo jest prywatne — Pages z prywatnego repo tylko dla kont Enterprise;
  odpadają, chyba że osobne publiczne repo tylko na politykę).
Rekomendacja: `mini.verbigem.com/privacy` (jeden hosting, już skonfigurowany Cloudflare).

### 3.3 Data Safety form (w Play Console)
Wypełnić zgodnie z 3.1: "Data collected" (email, app activity, photos, audio, contacts),
"Shared with third parties" (OpenRouter dla Pro). Nie zaniżać — niezgodność = odrzucenie.

---

## 4. Plan krok po kroku (fazy)

**Faza 0 — Decyzje (milo)** — kraj konta, strategia kanałów, lista testerów, hosting
polityki. (sekcja 6)

**Faza 1 — Konto + weryfikacja** (dni–tyg.)
1. Założyć Play Console (individual), opłacić $25.
2. Przesłać dokument tożsamości + adres + telefon.
3. Poczekać na weryfikację (czasem kilka dni–tygodni).

**Faza 2 — Zmiany w kodzie** (implementacja, po akceptacji Fazy 0)
1. Gradle flavors `play` / `standalone` (2.1).
2. Release signing + Play App Signing (2.2).
3. `bundlePlayRelease` (2.3), test 16 KB alignment w APK Analyzer.
4. Wersjonowanie (2.4).
5. Aktualizacja stringów w 6 językach (2.5).
6. Build `play` + ręczny test na telefonie (logcat `UpdateManager`: przy `onPlayStore`
   otwiera Sklep, NIE pobiera APK).

**Faza 3 — Polityka + Data Safety**
1. Napisać politykę prywatności, wrzucić na `mini.verbigem.com/privacy`.
2. Wgrać AAB do **Internal / Closed testing** track.
3. Wypełnić Data Safety form (zgodny z polityką).
4. Ustawić content rating (kwestionariusz).

**Faza 4 — Zamknięte testy** (min. 14 dni)
1. Dodać 12 testerów (e-maile) do closed testing.
2. Utrzymać 14 dni ciągłych testów.
3. Zbierać feedback, poprawki (nowy `versionCode`).

**Faza 5 — Produkcja**
0. **App Check enforcement** (gdy pierwszy **Play-signed release** jest już w Sklepie):
   zob. README → *### App Check* (pełny TODO). Kolejność: zarejestruj appkę w Firebase
   App Check (provider **Play Integrity**) + dodaj **SHA-256 certyfikatu podpisującego
   Play** (Play Console → Setup → App integrity); włącz `enforce` na **Firebase Auth**,
   **Firestore** i **Storage**; w `functions/src/contacts.ts` przestaw `matchContacts`
   na `enforceAppCheck: true`; wycofaj debug-tokeny.
   ⚠️ **NIE** włączaj `enforce` na debug-sideload dystrybucji auto-update — Play Integrity
   nie poświadczy appki, której nie zainstalował Play, i odrzuci wszystkich userów.
1. Zgłosić do produkcji (po udanych testach zamkniętych).
2. Recenzja Play (zwykle kilka dni).
3. Publikacja (Managed Publishing — możesz ręcznie puścić).

---

## 5. Szacowany czas
- Weryfikacja konta: od kilku dni do ~2 tyg.
- Kod (Faza 2): 1–2 dni pracy.
- Zamknięte testy: **minimum 14 dni** (twardy wymóg).
- Recenzja produkcji: kilka dni.
- **Całość: realistycznie 3–5 tygodni** od startu (testy 14-dniowe to najdłuższy element
  nieprzyspieszalny).

---

## 6. Pozostałe do zrobienia przez milo (nie są blokerami kodu)

1. **Zebrać 12 e-maili testerów** (Opcja A z 2.6 — rodzina/znajomi). Wpisać do listy w
   Play Console gdy konto będzie gotowe.
2. **Dokument tożsamości** (polski paszport/dowód) + **adres w Paragwaju** do weryfikacji
   konta. Mieć skan/zeszyt pod ręką przy zakładaniu konta.
3. **Treść polityki prywatności** — agent przygotuje szkic po akceptacji Fazy 2 (lub
   wcześniej, jeśli chcesz). Wrzucić na `mini.verbigem.com/privacy`.
4. **$25 opłaty** za konto Play (sprawdzić przy zakładaniu, czy doszła opłata roczna).

Wszystkie decyzje z sekcji 0 (kraj, kanały, hosting) są USTALONE. Kod (Faza 2) można
implementować po potwierdzeniu, że plan jest OK — nie czeka na powyższe 4 punkty.

---

## 7. Notatki dla agenta (przyszłe sesje)
- Po zmianach kodu (Faza 2) → zaktualizować README (sekcja Auto-aktualizacja: opisać
  flavors `play`/`standalone`, że Play buduje AAB i otwiera Sklep, sideload pobiera APK).
- `REQUEST_INSTALL_PACKAGES` w manifeście głównym musi zostać, ale flavor `play` go usuwa —
  nie usuwać z `app/src/main/AndroidManifest.xml`.
- Klucze podpisujące: dodać ścieżki do `.gitignore`, nigdy nie commitować.
- Wersjonowanie: jeden licznik `versionCode` dla obu kanałów; `version.json` rozróżnia
  akcję po `applicationId`.
