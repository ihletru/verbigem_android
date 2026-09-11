> 📄 Fragment dokumentacji Verbigem Android. Indeks: [README.md](../README.md)

# 📢 Reklamy (AdMob — GMA Next-Gen SDK)

Prawdziwy baner na dole ekranu Tłumacza, tylko dla kont Free. **SDK nowej generacji**,
nie klasyczny `play-services-ads`.

| Rzecz | Gdzie |
|---|---|
| Zależności | `libs.versions.toml` → `gma-ads` (`ads-mobile-sdk:1.2.1`) + `ump-user-messaging` (`user-messaging-platform:4.0.0`) |
| **ID aplikacji i jednostki — JEDYNE miejsce** | `app/build.gradle.kts`, stałe na górze bloku `android {}`: `admobAppIdPlay` / `admobBannerUnitIdPlay` (defaultConfig) oraz `admobAppIdSideload` / `admobBannerUnitIdSideload` (nadpisanie w smaku `standalone`) → `BuildConfig.ADMOB_APP_ID` / `ADMOB_BANNER_UNIT_ID` |
| App ID w manifeście | `AndroidManifest.xml` — `meta-data com.google.android.gms.ads.APPLICATION_ID` = `${admobAppId}` (**wymóg UMP**) |
| Zgody + inicjalizacja | `ads/AdsConsent.kt` |
| Baner (Compose) | `ui/components/AdBannerView.kt` (`AndroidView` + `AdView.loadAd`) |
| Wywołanie zgód | `MainActivity.onCreate` → `lifecycleScope.launch { AdsConsent.refresh(this) }` |

## ⚠️ Kolejność: zgoda → SDK → reklama (wymuszona kodem, nie konwencją)

Google wprost ostrzega: SDK potrafi wstępnie pobrać reklamy **już w trakcie inicjalizacji**.
Dlatego `MobileAds.initialize()` odpala się **dopiero gdy `canRequestAds()` zwróci true**,
czyli po `requestConsentInfoUpdate()` + ewentualnym formularzu UMP. Baner czyta
`AdsConsent.adsReady` i do tego momentu pokazuje placeholder. **Nie zamieniaj tej
kolejności** — request reklamowy przed zgodą w EOG to najszybsza droga do zamknięcia
konta w AdMobie.

- `canRequestAds()` zwraca **`false` zawsze, dopóki nie wywoła się
  `requestConsentInfoUpdate()`** — nawet gdy zgoda z poprzedniej sesji jest ważna.
  Sprawdzamy je więc dopiero po odświeżeniu.
- Formularz UMP pokazuje się **tylko gdy jest wymagany** (EOG / UK / CH). W Paragwaju,
  USA czy Chinach to no-op i od razu leci inicjalizacja SDK.
- `MobileAds.initialize()` idzie na `Dispatchers.IO` — na głównym wątku grozi ANR.

## ⚠️ NIE kluczuj ID reklam po `BuildConfig.DEBUG`

APK dystrybuowany auto-update'm jest buildem **debugowym** (patrz App Check). Gdyby
testowe ID włączały się od `BuildConfig.DEBUG`, realni użytkownicy dostawaliby reklamy
testowe do końca świata. Przełącznik jest ręczny: obie stałe w `build.gradle.kts`.

Testowe ID Google'a (`ca-app-pub-3940256099942544/…`) działają z dowolnym App ID i nie
łamią polityk — można ich używać, póki nie ma prawdziwej jednostki banera.

## Status: wypuszczone w v1.0.52 (versionCode 53)

ID produkcyjne w `build.gradle.kts` od 2026-09-09. SDK domergował do manifestu dwa
uprawnienia: `com.google.android.gms.permission.AD_ID` i
`android.permission.ACCESS_ADSERVICES_AD_ID` — to one wymuszają deklarację Data Safety
poniżej.

## Awaryjna inicjalizacja SDK po 3 s (dodane w v1.0.52)

UMP potrafi odpowiedzieć `canRequestAds() == false` **mimo że zgoda nie jest wymagana**
— typowa przyczyna: świeżo zatwierdzone konto AdMob bez skonfigurowanego „Privacy &
messaging" / Funding Choices. Wtedy UMP nie ma skąd wziąć formularza i oddaje `false`
w każdym kraju, więc baner wisiałby na placeholderze do końca świata.

`AdsConsent.refresh()` wykrywa tę sytuację i po 3 sekundach odpala
`MobileAds.initialize()` mimo wszystko. **Strażnik EOG**: jeśli użytkownik jest w
EOG/UK i zgoda nie jest uzyskana (`consentStatus == REQUIRED` albo
`privacyOptionsRequirementStatus == REQUIRED`), fallback **nie zadziała** — bez zgody
reklamy nie lecą, zgodnie z zasadami Google (w tym wypadku inicjalizacja SDK
byłaby drogą do zamknięcia konta AdMob).

## ⚠️ Do domknięcia (tylko w konsolach, nic w kodzie)

1. **Data Safety w Play Console** — zadeklarować zbieranie identyfikatora reklamowego
   (`AD_ID`) i danych o użytkowaniu. Bez tego kolejne wydanie na produkcję dostanie
   ostrzeżenie/blokadę.
2. **AdMob → Aplikacje do zatwierdzenia** — `com.verbigem.app` jest na liście ze
   statusem „Nieukończona konfiguracja" i zero wyświetleń mimo rosnących żądań.
   „Dokończ konfigurację" → weryfikacja właściciela (link do Play Store). Bez tego
   serwowanie reklam jest zablokowane niezależnie od kodu.

## Diagnostyka reklam w aplikacji (v1.0.67, karta w Profilu)

Baner, który się nie wypełnia, nie mówi nic o przyczynie — dlatego od v1.0.67 w
Profilu jest karta **„Diagnostyka reklam"**, pokazująca na żywo:

- `MobileAds.isInitialized` (właściwość, **nie** metoda — pułapka w SDK nowej generacji),
- `canRequestAds` i `consentStatus` z UMP,
- która jednostka banera jest ładowana (produkcyjna vs testowa Google),
- **nazwę** ostatniego błędu (`LoadAdError.ErrorCode.name`, np. `ERROR_CODE_NO_FILL`).

Przełącznik **„Reklamy testowe Google"** podmienia jednostkę na
`ca-app-pub-3940256099942544/6300978111` (zawsze się wypełnia). To rozstrzyga, czy
winny jest kod/SDK, czy konto/slot: testowa działa + produkcyjna nie → konsola.
Persystowane w `SharedPreferences("ads_diagnostics")`. Guzik **„Menu debugowania
AdMob"** otwiera `MobileAds.openDebugMenu(activity, unitId)` — podgląd stanu slotu
bez logcata.

⚠️ **Sideload (`com.verbigem.app.sideload`) ma osobny problem:** AdMob weryfikuje
aplikacje per pakiet, a weryfikacja wymaga wpisu w Play Store. Sideload nie ma wpisu
w Play, więc AdMob pokaże tę samą pozycję „Nieukończona konfiguracja" albo
ograniczy serwowanie. Tymczasowe wyjścia: (a) dodać sideload ręcznie w AdMob jako
app non-Play (akceptuje ograniczenie), (b) ukryć baner w smaku standalone przez
`if (!BuildConfig.PLAY_BUILD) return` w `AdBannerView`. Do decyzji.

⚠️ **AdMob rozlicza żądania po App ID z inicjalizacji, NIE po nazwie pakietu.**
Dlatego oba smaki mają teraz własne stałe (`admobAppIdPlay` / `admobAppIdSideload`)
— dopóki dzieliły jedno App ID, żądania sideloadu były przypisywane do aplikacji
Play i w konsoli AdMob nie było osobnego wiersza dla `com.verbigem.app.sideload`.
Po dodaniu sideloadu w AdMob (Aplikacje → Dodaj aplikację → Android → „nie jest
opublikowana w Google Play") AdMob wygeneruje **nowy App ID i nową jednostkę** —
wkleja się je wyłącznie w `admobAppIdSideload` / `admobBannerUnitIdSideload`.
Weryfikacja, że nadpisanie per smak działa: `processPlayDebugMainManifest
processStandaloneDebugMainManifest` + `grep APPLICATION_ID` w obu merged manifestach
oraz `generate*BuildConfig` → `ADMOB_APP_ID` w `standalone/` i `play/`.
2. **AdMob → Aplikacje → Verbigem** — sprawdzić, czy aplikacja jest „gotowa do
   wyświetlania reklam" (nowa jednostka potrzebuje zwykle kilku godzin, zanim zacznie
   serwować).

## Wejście do ustawień prywatności (wymóg dla EOG) — zrobione

Karta **„Ustawienia prywatności reklam"** w Profilu, pod polityką prywatności. Pokazuje
się **tylko gdy `AdsConsent.privacyOptionsRequired`** — czyli wyłącznie dla użytkowników
z EOG / UK / CH (reszta świata jej nie zobaczy) i nie dla kont Pro (te nie widzą reklam,
więc nie ma czego wycofywać). Woła `AdsConsent.showPrivacyOptions(activity)`; Activity
bierzemy przez `context.findActivity()`, bo UMP nie przyjmie zwykłego kontekstu.
Stringi: `privacy_ad_settings` / `privacy_ad_settings_desc` × 6 języków.

---

## 🌐 Reklamy w webappie — AdMob tu NIE zadziała

**AdMob obsługuje wyłącznie aplikacje mobilne.** Na stronę potrzebny jest osobny produkt:
**Google AdSense** (osobna rejestracja + weryfikacja domeny). Checklista:

1. **← TU JESTEŚMY ZABLOKOWANI (stan 2026-09-11).** Menu konta AdSense Milosza ma
   tylko: `Strona główna · Raporty · Płatności · Konto · Opinie`.
   **Nie ma sekcji „Witryny" ani „Reklamy"** — nie da się dodać witryny ani
   utworzyć jednostki reklamowej. W „Informacjach o koncie" widnieje wyłącznie
   „Aktywne usługi: AdMob". **Przyczyna nieustalona** — nie zgadywać, wymaga
   zajrzenia w konto (CDP do Chrome Milosza albo jego zrzut „Strona główna").
2. **`mini/public/ads.txt`** → `google.com, pub-<TWOJE-ID>, DIRECT, f08c47fec0942fa0`
   (Vite kopiuje `public/` → `dist/`). Czeka na `pub-ID`.
3. ~~**Polityka prywatności kłamie.**~~ — ✅ **zrobione 2026-09-09**, wdrożone na produkcję:
   `mini/scripts/build_privacy.py` ma teraz we wszystkich 6 językach uczciwą sekcję o
   reklamach Google (cookies, `google.com/settings/ads`, treść tłumaczeń nie trafia do
   sieci reklamowej) + Google (AdSense/AdMob) w tabeli podmiotów trzecich.
4. ~~**`index.html` obiecuje „bez reklam"**~~ — ✅ **zrobione** (meta `description`,
   `og:description`, JSON-LD).
5. ~~**SPA**~~ — ✅ **zrobione**: `mini/src/billing/AdBanner.tsx` ma realny
   `<ins className="adsbygoogle">` z `push()` per montowanie (obejście dla
   `react-router`). Włącznik to **zmienne środowiskowe** `VITE_ADSENSE_CLIENT` /
   `VITE_ADSENSE_SLOT` (patrz `mini/.env.example`) — bez nich baner zostaje
   placeholderem, więc kod na produkcji jest całkowicie inertny.
   ⚠️ Na localhost realne jednostki NIE są w ogóle renderowane (`import.meta.env.DEV`)
   — tu, w przeciwieństwie do Androida, przełącznik po DEV jest poprawny.
6. **Zgody (CMP)** — jak UMP w Androidzie: AdSense wymaga certyfikowanego CMP dla EOG.
   Bez CMP Google serwuje w EOG reklamy niedopasowane (legalne, ale mniej płatne).
   ⚠️ Deploy: procedura z sekcji *„mini.verbigem.com to TA SAMA webapp"* — `npm run build`
   przebudowuje całą stronę, nie tylko reklamy.

### 🔒 Reklamy za logowaniem wymagają loginu dla robota — to WARUNEK, nie opcja

Źródło: [Wyświetlanie reklam na stronach wymagających logowania](https://support.google.com/adsense/answer/161351?hl=pl)
(Google AdSense Help). Cytat oryginalny:

> Po **aktywacji konta** reklamy Google **mogą** być wyświetlane na stronach Twojej
> witryny wymagających logowania, **pod warunkiem że zostanie utworzony login dla
> robota**. Umożliwi to robotowi indeksującemu AdSense odwiedzanie Twojej witryny
> i wyświetlanie reklam.

Czyli: **bez loginu dla robota reklamy na stronach za logowaniem nie wyświetlą się.**
(To sprostowanie — wcześniej twierdziłem, że brak dostępu robota tylko „obniża
trafność". Oficjalny tekst mówi wprost o warunku. Milosz miał rację od początku.)

Kolejność wymagana przez Google (wszystkie kroki z tego artykułu):

1. **Aktywacja konta AdSense** ← zablokowane, patrz punkt 1 wyżej
2. **Konto → Dostęp i autoryzacja → Dostęp dla robota → „Dodaj dane logowania"**,
   gdzie trzeba podać:
   - **Zastrzeżony katalog lub URL** — adres, do którego robot ma zablokowany dostęp
   - **URL logowania** — strona, na którą robot wchodzi, żeby się zalogować
   - **Metoda logowania** — POST lub GET (w UI Milosza dostępne też „HTTP")
   - **Parametry logowania** — pary klucz–wartość, **„tak aby serwer zwracał plik
     cookie do dostępu po zalogowaniu"**
3. **Weryfikacja witryny w Search Console** (to osobny produkt — `search.google.com/search-console`)

**Dlaczego to nie zadziała z naszym obecnym kodem** (fakty z kodu, nie domysły):

| Wymóg Google | Stan u nas |
|---|---|
| Serwer, który przyjmie POST/GET i **zwróci cookie sesyjne** | `firebase.json` = hosting statyczny + rewrite `**` → `/index.html`. Serwera nie ma. |
| Formularz z `action`/`method` i polami `name` | Wszystkie formularze to React `onSubmit` bez `action`/`method`; inputy bez `name` |
| Metoda HTTP (Basic) | Wymaga `401 WWW-Authenticate` — hosting statyczny tego nie zrobi |
| Endpoint logowania w backendzie | Funkcje to: `deepseekProxy`, `googleTranslateProxy`, `visionProxy`, `walletTopUp`, `createCheckout`, `paddleWebhook`, `portalSession` — **żadnego logowania** |

Logowanie idzie przez **Firebase Auth JS SDK** (XHR do `identitytoolkit`), które
**nie używa cookies** — sesja siedzi w IndexedDB/localStorage. Żeby spełnić wymóg
„serwer zwraca cookie", trzeba by dobudować: Cloud Function przyjmującą POST,
weryfikującą hasło i wystawiającą cookie sesyjne Firebase — **a potem jeszcze
nauczyć SPA czytać to cookie** (dziś `RequireAuth` patrzy wyłącznie na klienta
Firebase). To poważna zmiana w uwierzytelnianiu, nie „dodanie formularza".

⚠️ **Nierozstrzygnięte:** czy robot AdSense wykonuje nasz JavaScript. `/app` to SPA —
bez renderowania JS robot zobaczy pustą skorupkę `index.html`. **Nie zaczynać
przebudowy logowania, dopóki to nie jest pewne.**

### 🚫 Landing celowo BEZ reklam (decyzja 2026-09-11)

Reklamy AdSense mają być **tylko w webappie** (`/app/*`, za `RequireAuth`).
`LandingPage.tsx` nie ma już slotu — w jego miejscu jest komentarz ostrzegawczy.
`AdBanner` jest używany wyłącznie w `Layout` (`App.tsx:36`), a `index.html` nie ma
wpisu AdSense. Efekt: skrypt AdSense ładuje się tylko w `/app`, więc **Auto ads nie
wejdą na publiczną stronę**. Prop `hidePlaceholder` usunięty (był potrzebny tylko
landingowi).

---
