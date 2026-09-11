> 📄 Fragment dokumentacji Verbigem Android. Indeks: [README.md](../README.md)

# ❓ Ikony: kliknięcie = akcja, długie kliknięcie = pomoc (od v41)

**Globalna reguła UI.** Każda ikona w aplikacji:

- **kliknięcie** → wykonuje zadanie, do którego ikona została stworzona,
- **długie kliknięcie** → otwiera okno z opisem: czym jest, co robi, jak używać.

Dodatkowo każdy ekran ma w nagłówku **logo świetlika + tytuł + przycisk „?"** po prawej
stronie — „?" otwiera od razu okno z opisem całej strony.

## Infrastruktura — `ui/components/HelpDialog.kt`

Jeden plik, z którego korzystają wszystkie ekrany. Nie wolno implementować pomocy
drugi raz obok — każda nowa ikona bierze stąd komponent.

| Element | Do czego |
|---|---|
| `HelpWindowState` + `rememberHelpWindowState()` | stan okna; **jedna instancja na ekran**, nie na ikonę |
| `HelpWindow(state)` | `Dialog` + `Surface`; wołaj **na końcu ekranu** (raz, obok stanu) |
| `Modifier.helpClickable(onClick, onLongClick)` | wrapper na `combinedClickable` (`@OptIn(ExperimentalFoundationApi)`) |
| `HelpIconButton(...)` | ikona-akcja: tap = akcja, długi tap = pomoc |
| `HelpFramedIconButton(icon, caption, ...)` | ikona **w ramce** + podpis 11.sp (mikrofon / aparat / aparat Pro) |
| `QuestionMarkButton(onClick)` | przycisk „?" |
| `ScreenHeader(title, helpState, helpTitle, helpText)` | logo + tytuł + opcjonalny `subtitle` + `trailing` + „?" |

## ⚠️ Pułapki, na których ta reguła się wykłada

1. **Nie dokładaj `helpClickable` do elementu, który już ma własny `clickable`.**
   `IconButton` / `Button` mają wewnętrzny `clickable` — wygrywa on i **long-press ginie
   po cichu** (zero błędu w logach). Dlatego `HelpIconButton` to `Box` + `helpClickable`,
   a nie `IconButton`.
2. **`stringResource` rozwiązuj u wywołującego — i NIGDY wewnątrz lambdy kliknięcia.**
   `HelpWindowState.show(title, text)` bierze gotowe `String`. `stringResource()` jest
   **`@Composable`**, a lambda `onClick` / `onLongClick` w `helpClickable` / `Button` /
   `QuestionMarkButton` **nie jest** kompozycyjna — wywołanie w jej wnętrzu to błąd
   kompilacji `e: @Composable invocations can only happen from the context of a @Composable
   function` (tak sypały się `ProfileScreen`, `ContactCardScreen`, `ContactsScreen` w v42).
   Wzorzec naprawy: wyciągnij `val t = stringResource(R.string.help_x)` w scope
   composable i dopiero wtedy `onClick = { help.show(t, ...) }`.
3. **Teksty pomocy to `R.string.help_*` — obowiązkowo we wszystkich 6 językach.**
   Reguła z sekcji *Wielojęzyczność* dotyczy ich tak samo jak etykiet. Klucz `help_close`
   (przycisk zamknięcia) i `help_open` (contentDescription „?") są współdzielone.
4. **Opisy silników pod ikonami zostały USUNIĘTE** — zastąpiły je okna pomocy
   (`EngineChoice.helpTitleResId` / `helpTextResId`). `descriptionResId` zostało w enumie,
   ale już się nie wyświetla; nie przywracaj go do UI.
5. **Menu dolne też ma okna pomocy** (`NavItem.helpResId`) i własny `rememberHelpWindowState()`
   wewnątrz `BottomNav` — pasek jest współdzielony z `AppNavigation`, więc nie może
   korzystać ze stanu ekranu.
6. **Wewnątrz `Dialog` `LocalContext` wraca do bazowej aktywności** (locale systemu, nie wybrany
   język interfejsu). Dlatego `stringResource(R.string.help_close)` w `HelpWindow` ignorował
   preferencję użytkownika i przycisk „Rozumiem" zawsze wyświetlał się po polsku — nawet przy
   angielskim UI. Naprawa: `HelpWindow` przechwytuje `LocalContext.current` PRZED otwarciem
   `Dialog{}` i odtwarza go przez `CompositionLocalProvider(LocalContext provides localizedContext)`
   wewnątrz. Dla **nowych** okien używaj gotowych `LocalizedDialog` / `LocalizedAlertDialog`
   z `ui/components/LocalizedDialog.kt` — opis w sekcji *Wielojęzyczność*.
7. **`WindowInsets.isImeVisible` wymaga własnego importu.** To extension property:
   `import androidx.compose.foundation.layout.isImeVisible` (obok `...layout.WindowInsets`).
   Bez tego importu kompilator zgłasza `Unresolved reference 'isImeVisible'`, mimo że
   `WindowInsets` jest zaimportowany — patrz `ConversationScreen` (ma oba importy).
8. **Dosuwanie widoku nad klawiaturą (`BringIntoViewRequester`) to dwa opt-iny.**
   `LaunchedEffect(isImeVisible) { delay(250); requester.bringIntoView() }` +
   `.bringIntoViewRequester(requester)` wymagają
   `@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)` na composable.
   Sam `ExperimentalLayoutApi` nie wystarcza — to osobna adnotacja
   (`androidx.compose.foundation.ExperimentalFoundationApi`, a nie `...layout`).
   `delay(250)` jest celowy: klawiatura wjeżdża ~250 ms po fokusie, wcześniej
   `bringIntoView()` policzy zły offset.
9. **`BuildConfig.VERSION_NAME` / `VERSION_CODE` wymagają `buildFeatures { buildConfig = true }`.**
   AGP 8+ ma to domyślnie wyłączone; bez flagi każdy ekran odwołujący się do `BuildConfig`
   (karta „O aplikacji" w Profilu) sypie `Unresolved reference 'BuildConfig'`.
10. **`enabled` w `helpClickable` wyłącza tylko AKCJĘ, nigdy pomoc.**
    `combinedClickable(enabled = false)` wyłącza też `onLongClick` — więc na
    nieaktywnej kontrolce (pusty tekst → „Tłumacz", darmowe konto → silniki,
    brak zdjęcia → przyciski OCR) pomoc znikała bez śladu. Dlatego
    `helpClickable` zawsze przekazuje `enabled = true` i guarduje dopiero
    `onClick = { if (enabled) onClick() }`. Nie „naprawiaj" tego z powrotem.

## ➕ Jak dodać nowy silnik (checklista, sprawdzona na Pro 7B)

1. `ModelTier` — nowa pozycja z `fileName`, `approxBytes`, `minRamBytes`.
   **`requiresGpu = true`**, jeśli model bez GPU nie ma sensu (jak `PRO_7B`) —
   wtedy na urządzeniach bez backendu `blockReason()` zwraca `NO_GPU` i silnik znika
   z listy, zamiast kusić 2.9 GB pobierania, po którym użytkownik dostanie 1.6 tok/s.
2. `ModelDownloader.URL_*` + wpis w `urlFor()`.
3. `EngineChoice` — nowa pozycja: `id`, ikona (emoji), `captionResId`,
   `helpTitleResId`, `helpTextResId`, `isProOnly`, **`modelTier`**.
   Brak `modelTier` = silnik bez własnego GGUF (BOTH, ONLINE).
4. **5 stringów × 6 języków**: `engine_X_label`, `engine_X_desc`,
   `engine_caption_X`, `help_engine_X_title`, `help_engine_X`.
   Zweryfikuj skryptem porównującym zbiór `<string name=...>` w `values/` z każdym locale.
5. `TranslatorViewModel.translate()` — `when` jest wyczerpujący, kompilator wymusi
   obsługę nowej pozycji. Silniki jedno-modelowe idą jedną gałęzią przez `engine.modelTier`.
6. Gating: `computeAvailableEngines()` filtruje po `ModelDownloader.blockReason()` —
   nowy silnik z dużym `minRamBytes` **sam zniknie** na słabszych urządzeniach.
   Kolejność w `blockReason()` ma znaczenie: `NO_GPU` jest sprawdzany **przed**
   pamięcią i miejscem na dysku, żeby komunikat był właściwy („brak GPU", nie „brak RAM").

⚠️ **Pułapka overloadów:** `downloadModel(tier: ModelTier)` i
`downloadModel(isAccurate: Boolean = false)` — **tylko jeden może mieć wartość
domyślną**, inaczej wywołanie bez argumentów jest niejednoznaczne i nie kompiluje się.
To samo dotyczy `translate` / `translateSegmented` / `startModelDownload`.

## Konwencja podpisów

Podpisy ikon są zawsze **11.sp** — ta sama wielkość co w menu dolnym. Tam, gdzie
Milosz nie podał treści okna, treść jest wygenerowana i trzyma się schematu:
*czym jest → co robi → jak używać → co się dzieje z danymi*.

## 📱 Dolny pasek — zawsze MAX 5 ikon

`BottomNav` renderuje pasek współdzielony przez `AppNavigation` (`showBottomNav`).
**Twarda reguła: pasek ma dokładnie 5 pozycji** — Tłumacz, Rozmowa, Czat, Kontakty,
Profil. Nie dodawaj szóstej ikony „bo ekran X nie ma nawigacji".

- Ekran, który potrzebuje nawigacji, ale nie pasuje do paska, i tak pokazuje `BottomNav`
  (wystarczy, że jego `Screen.route` jest w `AppNavigation.showBottomNav`) — nawigacja
  wraca przez ikonę Tłumacza/Profilu, a wejście do ekranu jest jednym tapem z innego
  ekranu (wzorzec: OCR z Tłumacza przez `HelpFramedIconButton`).
- v41 dodało OCR jako szóstą pozycję. Cofnięte — pasek wizualnie się rozjeżdżał,
  a wejście do OCR nie było krótsze niż przez Tłumacza.

---
