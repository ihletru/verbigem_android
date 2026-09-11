> 📄 Fragment dokumentacji Verbigem Android. Indeks: [README.md](../README.md)

# 🌍 Wielojęzyczność — ZAKAZ hardkodowania tekstów UI

Aplikacja jest wielojęzyczna (**PL, EN, DE, ES, ZH, TR**). **Pod karą nie wolno** hardkodować żadnych tekstów interfejsu (etykiet, komunikatów, tooltipów, opisów) w kodzie Kotlin/Compose — ani po polsku, ani po angielsku.

1. Każdy widoczny tekst UI musi pochodzić z `stringResource(R.string.xxx)`.
2. Zasoby: `app/src/main/res/values/strings.xml` (**domyślny = angielski**) oraz `values-pl/`, `values-de/`, `values-es/`, `values-zh/`, `values-tr/`.
3. Etykiety silników (`EngineChoice`) i tooltipy używają `descriptionResId` / `labelResId` mapowanych na `R.string.*` (nie `labelKey` z hardkodowanym tekstem).
4. Komunikaty błędów z warstwy `engine/*` bierzemy przez `context.uiString(R.string.xxx)` — **nie** `getString`, bo kontekstem silnika jest `Application`, które nie zna języka wybranego w aplikacji (patrz reguła `uiString` niżej).
5. **Po dodaniu `string` do `values/strings.xml` należy dodać go do wszystkich pozostałych `values-xx/strings.xml`** (nawet jako tymczasowy angielski fallback).
6. Klucze akcji historii/result: `action_copy`, `action_share`, `action_read`, `action_read_pro`, `action_delete`. Dialog update: `update_available_title/body/action/later`. Reklama: `ad_banner_label/text`.
7. Teksty okien pomocy: `help_*` (≈50 kluczy × 6 języków). Współdzielone: `help_close`, `help_open`. Per ekran: `help_intro_<ekran>` (+ wariant `_title`). Podpisy ikon: `input_caption_*`, `engine_caption_*`. **Najpierw `values/strings.xml`, potem reszta** — skryptem `python` można sprawdzić, czy żaden klucz nie został pominięty:
   ```bash
   python -c "import re,os;base={m for m in re.findall(r'<string name=\"([^\"]+)\"',open('app/src/main/res/values/strings.xml',encoding='utf-8').read())};[print(d,sorted(base-set(re.findall(r'<string name=\"([^\"]+)\"',open(f'app/src/main/res/{d}/strings.xml',encoding=\"utf-8\").read())))) for d in ['values-pl','values-de','values-es','values-zh','values-tr']]"
   ```
8. **Komunikaty błędów z ViewModeli też podlegają zakazowi** — `_errorMessage.value = "Błąd logowania"` to ten sam grzech co `Text("Błąd logowania")`. Bierzemy `uiString(R.string.xxx)`. Audyt: `grep -rn '_errorMessage.value = .*"' --include=*.kt app/src/main/java` — wszystkie trafienia muszą mieć `uiString`.
   ⚠️ Przejrzane 2026-09-10: 15 takich komunikatów siedziało w `AuthViewModel`, `ConversationViewModel`, `OcrViewModel` i `TranslatorViewModel` — po polsku i po angielsku na mieszance. Zastąpione kluczami `auth_error_*`, `conv_error_translation`, `ocr_error_recognition`, `translation_error_generic`, `read_pro_not_configured`, `read_pro_failed`. Stan po poprawce: **482 klucze × 6 locale, 0 braków** (po v1.0.56, która dodała `voice_permission_denied` i `voice_recognition_error`: **492**; po v1.0.58, która dodała `hide_conversation_failed` i `contacts_search_failed`: **494**; po v1.0.64, która dodała 5 × `model_error_*` + `phone_verify_error_code_expired`: **497**; po v1.0.65, która dodała 10 × `voice_error_*`, 3 × `auth_error_*`, 4 × `update_error_*` i `phone_verify_error_unknown`: **515** — sprawdzone, 0 braków w każdym z 6 locale).

⚠️ **Reguła dla kontekstu:** każdy kontekst podmieniany w `LocalContext` **MUSI dziedziczyć po `ContextWrapper`** i mieć Activity u podstawy. `MainActivity.LocalizationWrapper` używał `createConfigurationContext(config)` — to goły `ContextImpl`, więc łańcuch `baseContext` się urywał i `findActivity()` zwracał `null` (objawy: „no activity" w Phone Auth, crash `rememberLauncherForActivityResult` w `OcrScreen`). Naprawione klasą `LocalizedContext(base, locale) : ContextWrapper(base)`, która nadpisuje tylko `getResources()`/`getAssets()`.

⚠️ **Reguła `uiString` — tekst bierzemy z języka INTERFEJSU, nigdy z `Application`.** `Application` nie wie nic o języku wybranym w aplikacji, więc `getApplication<Application>().getString(...)`, `appContext.getString(...)` i `e.localizedMessage` zwracają **język telefonu** albo **angielski tekst z SDK**. To był systemowy powód zgłoszenia „ustawiłem angielski, a komunikat jest po polsku" w v1.0.65 — dotyczył logowania, tłumaczenia, OCR, rozpoznawania mowy, doładowania konta i okna aktualizacji oraz awaryjnego tekstu powiadomienia w usłudze FCM.

Rozwiązanie: `app/src/main/java/com/verbigem/app/util/UiStrings.kt`.

```kotlin
UiLangState.code = langCode     // raz na kompozycję, w MainActivity.LocalizationWrapper

uiString(R.string.x)            // w ViewModelu — AndroidViewModel ma własne przeciążenie
context.uiString(R.string.x)    // w silniku, ekranie, Toast
```

`UiLangState` trzyma wybrany język i cache `Resources` dla niego; `uiString` tworzy kontekst przez `createConfigurationContext` i bierze z niego **wyłącznie** `resources` (gołego `ContextImpl` nie wolno przekazywać dalej — patrz reguła dla kontekstu wyżej).

Wyjątkiem jest **tekst techniczny do logów** — `Log.e(TAG, "HTTP ${code}", e)` zostaje po angielsku, bo nie jest komunikatem dla użytkownika. Wzorzec: powód do logu, `uiString(...)` na ekran.

⚠️ **Reguła `uiLocale` — to samo dotyczy DAT i LICZB, nie tylko tekstów.** Nic w aplikacji nie woła `Locale.setDefault()`, więc `Locale.getDefault()` zwraca język **telefonu**. Skutek był identyczny jak przy tekstach: interfejs po polsku na hiszpańskim telefonie pokazywał skróty dni tygodnia po hiszpańsku („lun" zamiast „pon") w liście czatów, nazwę wykrytego kraju po angielsku („Poland" zamiast „Polska") na ekranie potwierdzania numeru, a rozmiar modelu z separatorem dziesiętnym telefonu („2.9 GB" zamiast „2,9 GB"). Wszędzie, gdzie wynik zobaczy człowiek, bierzemy `uiLocale` (z tego samego `UiStrings.kt`) zamiast `Locale.getDefault()`:

```kotlin
SimpleDateFormat("EEE", uiLocale)             // nie Locale.getDefault()
String.format(uiLocale, "%.1f", gigabytes)    // nie "%.1f".format(gigabytes)
Locale("", iso).getDisplayCountry(uiLocale)   // nie getDisplayCountry(Locale.getDefault())
```

Audyt: `grep -rn "Locale.getDefault()" --include=*.kt app/src/main/java` — jedyne dozwolone trafienie to `data/PhoneNumbers.kt` (zgadywanie kraju numeru wpisanego bez kierunkowego; tam język telefonu jest właściwym źródłem, bo to nie tekst dla użytkownika, tylko dane wejściowe do parsera).

Audyt: `grep -rn "getApplication<Application>().getString\|appContext.getString\|localizedMessage" --include=*.kt app/src/main/java` — poza `util/UiStrings.kt` (opis problemu w KDoc) i wywołaniami `Log.*` nie powinno nic zwracać.

Drugi przebieg (2026-09-10): `grep -rn "getString(R\.string" --include=*.kt app/src/main/java` — każde trafienie musi być w composable, który ma `val context = LocalContext.current` (czyli kontekst z `LocalizationWrapper`), albo dotyczyć `default_web_client_id` (identyfikator OAuth, nie tekst dla użytkownika). Sprawdzone: `ContactsScreen`, `OcrScreen`, `MyQrScreen`, `TranslatorScreen` mają `LocalContext.current`, a `PhoneVerificationViewModel` dostaje ten kontekst parametrem z ekranu.

⚠️ **Język musi być znany, ZANIM cokolwiek rozwiąże tekst.** `VerbigemApplication.onCreate()` ustawia `UiLangState.code` z DataStore (blokująco, z limitem 2 s) **przed** `VerbigemNotifications.ensureChannel(this)`. Bez tego nazwa kanału powiadomień, jego opis i etykiety akcji („Odpowiedz", „Oznacz jako przeczytane") powstawały w języku telefonu — usługa FCM działa bez kompozycji i bez `LocalContext`, więc nie ma skąd wziąć wybranego języka. Ten sam odczyt usuwa mignięcie polskiego w pierwszej klatce UI (wcześniej `collectAsState(initial = "pl")` dawało jedną klatkę po polsku).

Uwaga: nazwę kanału system zapamiętuje przy pierwszym utworzeniu. Po zmianie języka istniejąca instalacja zachowa starą nazwę w Ustawieniach systemowych — to ograniczenie Androida, nie błąd aplikacji (aktualizacja nazwy wymaga ponownego `createNotificationChannel`, co nadpisuje ustawienia wybrane przez użytkownika, dlatego tego nie robimy).
⚠️ **Reguła dla okien w Compose:** wnętrze `Dialog { }` to **osobna kompozycja**, której `LocalContext` wraca do bazowego Activity (locale urządzenia), a nie do `LocalizedContext`. Okno jest wtedy w języku telefonu, a nie w języku wybranym w aplikacji.

**Dlatego nie używaj `Dialog` / `AlertDialog` bezpośrednio — używaj `LocalizedDialog` / `LocalizedAlertDialog`** z `ui/components/LocalizedDialog.kt`. Te dwa komponenty łapią kontekst przed otwarciem okna i przepisują go do treści (a `LocalizedAlertDialog` do każdej lambdy osobno, bo `AlertDialog` renderuje `title`/`text`/`confirmButton` wewnątrz własnego okna). Przepisanie kontekstu, który i tak był poprawny, nic nie zmienia — więc użycie opakowania jest bezpieczne zawsze.

Wyjątki (opakowanie wpisane ręcznie, świadomie nie migrowane): `HelpDialog.kt` (`HelpWindow`) i `ModelDownloadDialog.kt`. Reszta okien w aplikacji przeszła na opakowania w v1.0.64 — wcześniej **osiem** z nich (`MainActivity` ×2, `ProfileScreen` ×3, `ContactCardScreen`, `ContactsScreen`, `ChatThreadScreen`) było w języku telefonu. Kontrola: `grep -rn "^\s*\(Dialog\|AlertDialog\)(" --include=*.kt app/src/main/java` — poza `LocalizedDialog.kt`, `HelpDialog.kt` i `ModelDownloadDialog.kt` nie powinno nic zwracać.

Jeśli kiedyś musisz użyć surowego `Dialog`:

```kotlin
val localizedContext = LocalContext.current          // złapane na poziomie ekranu
Dialog(onDismissRequest = { ... }) {
    CompositionLocalProvider(LocalContext provides localizedContext) { ... }
}
```

**Objaw w v1.0.63:** okno pobierania modelu było po polsku przy interfejsie ustawionym na angielski.

---
