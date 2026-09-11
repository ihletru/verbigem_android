> 📄 Fragment dokumentacji Verbigem Android. Indeks: [README.md](../README.md)

# 🧩 AAB (Google Play) vs APK (sideload) — co naprawdę się różni

Gdy coś „w APK działało, a w AAB nie", to nie magia — różnic jest skończona lista. Sprawdzać w tej kolejności:

| # | Różnica | Sideload APK (`standalone`, debug) | Play AAB (`play`, release) |
|---|---|---|---|
| 1 | **Podział po języku** | jeden uniwersalny plik, **wszystkie 6 języków** | Play dzieli AAB i domyślnie **też po języku** — telefon dostaje tylko swój język + angielski |
| 2 | R8 / minifikacja | **wyłączona** (`assembleDebug`) | **włączona** (`isMinifyEnabled = true`) — refleksja może zniknąć, jeśli zabraknie reguły w `proguard-rules.pro` |
| 3 | Podpis | debug keystore (`ec9deb58…`) | Play App Signing (`b09748e2…`) — inny certyfikat widzi Firebase, Play Integrity i OAuth |
| 4 | App Check | provider `debug` | provider Play Integrity |
| 5 | `applicationId` | `com.verbigem.app.sideload` | `com.verbigem.app` — **osobne aplikacje**, osobne dane, osobne wpisy w Firebase |
| 6 | Auto-aktualizacja APK | działa (`StartupGate`, `REQUEST_INSTALL_PACKAGES`) | wycięta (`PLAY_BUILD = true`, uprawnienie usunięte w `src/play/AndroidManifest.xml`) |
| 7 | Dostarczanie | jeden plik APK | zestaw splitów (base + ABI + gęstość) |

**Punkt 1 jest najczęstszym zaskoczeniem** i to on odpowiada za „w AAB są tylko polski i angielski". Naprawione w `app/build.gradle.kts`:

```kotlin
bundle {
    language {
        enableSplit = false   // wszystkie języki na każdym urządzeniu
    }
}
```

Podział po gęstości i po ABI zostaje włączony — tam oszczędności są realne i nic nie psują.

**Weryfikacja, czy dzielenie po języku jest wyłączone** (nie zgadywać po rozmiarze AAB — AAB zawsze zawiera wszystkie języki, decyzja siedzi w `BundleConfig.pb`):

```bash
python -c "
import zipfile
b=zipfile.ZipFile('app/build/outputs/bundle/playRelease/app-play-release.aab').read('BundleConfig.pb')
print('LANGUAGE negate=true:', b.count(b'\x08\x03\x10\x01'))"
```

`1` = dzielenie po języku wyłączone. `0` = włączone (domyślne) i wracamy do problemu.

`bundletool` leży w cache Gradle i **nie ma `Main-Class` w manifeście** — uruchamiać przez klasę główną, jak wrapper Gradle:

```bash
java -classpath "<gradle-cache>/bundletool-1.18.3.jar" \
  com.android.tools.build.bundletool.BundleToolMain build-apks --bundle=… --output=… --output-format=DIR
```

---

## 📦 Auto-aktualizacja APK

`UpdateManager` + dialog w `MainActivity` (`StartupGate` / `UpdateDownloadDialog`). Po starcie (raz, `LaunchedEffect`) odczytuje **`/updates/version.json`** z Firebase Hosting (`UpdateManager.updateJsonUrl`):
```
https://mini.verbigem.com/updates/version.json
```
```json
{
  "versionCode": 25,
  "versionName": "1.0.24",
  "apkUrl": "https://mini.verbigem.com/android/app-debug-v25.apk?v=25",
  "playStoreUrl": "https://play.google.com/store/apps/details?id=com.verbigem.app",
  "onPlayStore": false,
  "minSupportedCode": 1,
  "updatedAt": "2026-09-03"
}
```
Gdy `info.versionCode > currentVersionCode()` → `AlertDialog` (`update_available_title/body/action/later`). W trakcie pobierania `UpdateDownloadDialog`: pasek + procenty + licznik MB (`update_downloading_title/body/percent/bytes`). Błąd pobierania jest **widoczny** (`update_failed_title`, `update_failed`) i daje `update_retry` — wcześniej szedł tylko do logcatu, a dialog wisiał na 0% bez wyjścia.

### Jak działa postęp pobierania (tu były dwa błędy — nie powielaj ich)

- `UpdateManager.downloadAndInstall` emituje `MutableStateFlow<DownloadProgress>` (`bytesRead`, `totalBytes`; `totalBytes == 0` = brak `Content-Length` → `fraction == -1f`, pasek nieoznaczony + sam licznik MB). **Każdy tick to nowa instancja** — `MutableStateFlow` deduplikuje po `equals()`, więc stary model (`MutableStateFlow<Float?>`, w którym `-1f` powtarzał się w kółko) był po cichu gubiony i nie generował rekompozycji.
- Tick leci co **64 KB albo 250 ms** (`EMIT_EVERY_BYTES` / `EMIT_EVERY_MS`).
- ⚠️ **Flow żyje w `MainActivity`, nie w `remember {}`** (`MainActivity.downloadProgress`), a `StartupGate` czyta go `collectAsState()` **w swoim własnym scopie** i przekazuje wartość jako **parametr** do `UpdateDownloadDialog`. `AlertDialog` renderuje się do osobnego okna = **subkompozycja**: gdy stan czytany jest wyłącznie wewnątrz treści dialogu, rodzic się nie rekomponuje i dialog pokazuje wartość z pierwszej kompozycji (objaw: „pasek zamarznięty na 0%").
- **Diagnoza:** logcat `UpdateManager` wypisuje co ~1 MB `Download progress: X KB / Y KB`, po końcu `Download finished, bytes=… (content-length=…)`.

### ⚠️ Źródło pliku update — DWA pliki, nie pomyl

Katalog `dist/` w `verbigem/mini`, deployowany przez `firebase deploy --only hosting --project mini-verbigem` (ten sam projekt Firebase `mini-verbigem` co webappa i Firestore).

- **`dist/updates/version.json` — TEN, KTÓRY CZYTA APPKA.** Trzymany ręcznie. Jako jedyny ma w `apkUrl` **cache-buster `?v=N`** (`...app-debug-v25.apk?v=25`): query omija pułapkę `Cache-Control: immutable` na `/android/**` (CDN cache'uje URL z query jako osobny obiekt), więc nowy `versionCode` → nowy `?v=` → świeże bajty bez purgowania Cloudflara.
- `dist/android/version.json` — generowany przez plugin `injectBuildId` w `vite.config.ts` przy każdym `npm run build`. Appka go **NIE czyta**, ale trzymaj go zgodnego z `/updates/` — inaczej pierwszy `npm run build` cofnie `versionCode` do wartości z `vite.config.ts`.

**Ścieżki:** `onPlayStore == false` (AKTYWNA) → `downloadAndInstall()` pobiera APK przez **OkHttp 4.12** (własna instancja `okhttp3.OkHttpClient`, NIE systemowy `com.android.okhttp`) na `Dispatchers.IO`. Własny `okhttp3.Dns` (lookup przez `InetAddress.getAllByName`) omija zepsuty resolver na niektórych ROM (Xiaomi MIUI + Private DNS rzuca `Unable to resolve host` dla `*.web.app`) — **używamy `.com`, nie `.web.app`**. Weryfikacja po pobraniu: `length >= 1 MB` (odrzuca przypadkowy HTML) → instalacja `Intent.ACTION_INSTALL_PACKAGE` + `FileProvider`. `onPlayStore == true` → `openPlayStore()`.

**NIE używamy już GitHub (repo / raw / API) do update'ów.** Firestore `app_config/update` to tylko fallback w `fetchUpdateInfo()` (gdy Hosting niedostępny).

**Wymagane w manifeście:** `REQUEST_INSTALL_PACKAGES`, `<provider>` FileProvider z `android:authorities="${applicationId}.fileprovider"`.

### Kanały dystrybucji: `play` vs `standalone` (od 2026-09-10)

Appka budowana jest w dwóch smakach (`flavorDimensions "distribution"` w `app/build.gradle.kts`):

- **`play`** — wersja do Google Play. `applicationId = com.verbigem.app`,
  `BuildConfig.PLAY_BUILD = true`. W tym smaku `MainActivity` **pomija `StartupGate`**
  (Play sam dostarcza aktualizacje), a `app/src/play/AndroidManifest.xml` usuwa
  uprawnienie `REQUEST_INSTALL_PACKAGES` (`tools:node="remove"`) — inaczej recenzja
  Play odrzuca appkę za samodzielny update. Weryfikacja: `aapt2 dump permissions
  app-play-debug.apk` nie zawiera `REQUEST_INSTALL_PACKAGES`.
- **`standalone`** — sideload z Firebase Hosting. `applicationId =
  com.verbigem.app.sideload`, `BuildConfig.PLAY_BUILD = false`. Zachowuje
  `REQUEST_INSTALL_PACKAGES` i pełny `StartupGate` (pobieranie APK z
  `mini.verbigem.com`). Istniejące instalacje sideload mają `com.verbigem.app`
  (stary package) i po zmianie suffixu `.sideload` **nie dostaną auto-updatu** —
  trzeba ogłosić reinstall z nowym linkiem.

AAB dla Play: `bundlePlayRelease` (podpis przez `signingConfigs.release`, klucz z
`app/release-keystore.jks`, dane w gitignorowanym `keystore.properties`). Play App
Signing przejmuje klucz podpisujący w Sklepie.

**Filter logcat:** `UpdateManager` (`Starting APK download`, `Download started/finished`, `Download error`, `Install launch failed`).

### ⚠️ Pułapka: `immutable` cache na `/android/**` — NIGDY nie nadpisuj istniejącej nazwy APK

`firebase.json` ustawia dla `/android/**` nagłówek `Cache-Control: public, max-age=31536000, immutable`. W praktyce: **każdy plik pod `/android/` jest zamrażany w CDN (Cloudflare) na rok**.

- Nowa, **nieużywana wcześniej** nazwa (`app-debug-v24.apk`) → nie ma jej w cache → świeża treść. ✅
- **Nadpisanie istniejącej nazwy** (`app-debug-v23.apk` wrzucony drugi raz z inną treścią) → Firebase przyjmie nowe bajty, ale CDN nadal serwuje starą kopię; użytkownik pobierze POPRZEDNIĄ wersję. ❌

Diagnoza:
```
curl -s https://mini.verbigem.com/android/app-debug-v23.apk | sha256sum          # stary hash
curl -s "https://mini.verbigem.com/android/app-debug-v23.apk?cb=$RANDOM" | sha256sum  # nowy hash
curl -sI https://mini.verbigem.com/android/app-debug-v23.apk | grep -iE 'last-modified|age'
```
Jeśli `?cb=` daje inny hash niż czysty URL ⇒ to cache CDN, nie Firebase (Firebase **ma** nowy plik; potwierdza to domena `*.web.app`, która cache'uje osobno).

**Wniosek: zawsze wypuszczaj nowy `versionCode` = nowa nazwa pliku.** Nadpisanie tej samej nazwy wymagałoby purgu w Cloudflare (brak tokena w repo). Co serwuje serwer, sprawdzisz: `aapt2 dump badging <plik.apk> | head -1` (`C:/Users/milo/AppData/Local/Android/Sdk/build-tools/36.0.0/aapt2.exe`).

### ⚠️ `mini.verbigem.com` to TA SAMA webapp — deploy APK nie może jej przebudować

`verbigem/mini` serwuje na `mini.verbigem.com` aplikację **Mini Verbigem — Translator**. `firebase deploy --only hosting` **zastępuje całą zawartość hostingu** zawartością `dist/` — plików, których w `dist/` nie ma, zostaną **USUNIĘTE z serwera**.

- `npm run build` w `mini` zmienia hashe assetów (`assets/index-*.js`), bo wynik zależy od wersji zależności w `node_modules`, nie tylko od `src/`. **Przebudowa = cicha podmiana działającej strony.**
- **Bezpieczna procedura:** `dist/` odtworzony **co do bajtu** ze stanu na serwerze + zmienione TYLKO `android/version.json` i nowy APK.
  1. kopia `dist/android/` (Vite czyści `dist/`),
  2. pobranie wdrożonych plików — lista + ścieżki w `mini/.firebase/hosting.ZGlzdA.cache` (`ZGlzdA` = base64 „dist"), każdy `curl -o dist/$p https://mini.verbigem.com/$p`,
  3. sprawdzenie `find dist -type f` vs lista z cache — **żadnego nowego i żadnego brakującego pliku**,
  4. podmiana `android/version.json` + wrzucenie APK.
  Wtedy `firebase deploy` zgłasza `uploading new files [0/1]` — webapp nietknięta (potwierdzenie: `/version.json` z `BUILD_ID` nie zmienia wartości).

**Osobne projekty — nie pomyl:** `mini` → projekt `mini-verbigem` (mini.verbigem.com). `webapp` → projekt `verbigem-app-7k2` (verbigem.com). Deploy z `mini` **nie ma fizycznie jak** nadpisać `webapp`.

### 🐛 Uśpiony błąd: `npm run build` w mini sypie błędem (tesseract.js)

`src/ocr/OcrPage.tsx` robi `await import('tesseract.js')`, ale pakietu nie było w `package.json` ani w `package-lock.json` — był tylko w `node_modules`, które potem wyczyszczono. Skutek: `tsc -b` → `TS2307`, `vite build` → `Rollup failed to resolve import`. Naprawione przez `npm install tesseract.js --save`.

⚠️ Wersja `tesseract.js` wchodzi w skład bundle'a, więc jej zmiana **zmienia hashe assetów** — po reinstalacji nie zakładaj, że build odtworzy wdrożoną stronę co do bajtu.

---

## 🎨 Branding — ikona launchera i logo webapp

- **Ikona apki (adaptive icon):** `ic_launcher_background.xml` = `CalmDayBg` (#F7F5F1, krem z tła stron w motywie domyślnym — wcześniej #2C6B85 wyglądało jak „paskudne zielone tło"), `ic_launcher_foreground.xml` = `<inset>` 20% na `ic_launcher_firefly.png` (RGBA, przezroczyste tło). minSDK 26 → nie trzeba rasterowych mipmap.
  ⚠️ **Nie podmieniaj na nieprzezroczyste PNG** — `logov.png` (RGB, białe tło wypieczone) zostało celowo odrzucone; używaj **tylko** RGBA.
- **Gęstości launchera:** te same pliki `ic_launcher_firefly.png` są w `drawable-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/` w rozmiarach **108/162/216/324/432 px** (Android adaptive icon safe zone 108dp). Skalowane z `mini/public/logo-firefly.png` (724×724) filtrem **Lanczos** (`Pillow.Image.LANCZOS`, `optimize=True`). Źródło 724px jest małą gęstością dla drobnego grafu sieci neuronowej w logo — jeśli Milosz podsyła nowy plik źródłowy (np. **2048×2048 RGBA** z oryginalnego wektora), przebudować gęstości jednym skryptem w `app/scripts/genLauncherIcons.py` (do dopisania) i jakość skoczy bez zmian w kodzie.
- **Logo webapp (mini):** `verbigem/mini/public/logo-firefly.png` → `/logo-firefly.png`, wyświetlane przed `<h1>Mini Verbigem</h1>`. Vite kopiuje `public/` → `dist/` przy `npm run build`, więc zmiana pliku wymaga przebudowy webappy (zob. *Flow wydania*, krok 5).

---
