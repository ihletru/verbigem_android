# Czat i Kontakty — plan budowy

Dokument roboczy. Praca jest rozbita na fazy, z których każda kończy się stanem,
który da się zbudować i przetestować na telefonie. **Każdą sesję zaczynamy od
sekcji „Postęp", a kończymy jej aktualizacją** — dzięki temu kolejna sesja wie,
gdzie skończyliśmy, bez czytania całego pliku.

Ostatnia aktualizacja: 2026-09-05 — **fix 2.6: crash po kliknięciu „wyślij SMS"
WYDANY w v38** (poprzednio „no activity" WYDANY w v37). Przyczyna crashu:
`requireSmsValidation(true)` w `PhoneAuthOptions` — flaga działa TYLKO w MFA i
rzuca `IllegalArgumentException` bez `setMultiFactorSession`. Szczegóły w bloku
sesji niżej i w README przy „Weryfikacja numeru (2.6)".

Wcześniej: **Faza 3 (Kontakty 2.0) ZAMKNIĘTA** (3.0–3.10
zrobione). Ostatnie: **3.9 „Możesz znać" WYDANE w v33** (Cloud Function
`suggestFriends` + sekcja w zakładce Znajomi) i **3.10 audyt stringów ×6**
(przejściowy: 257 kluczy w 6 językach, zero braków, zero hardkodowań; v33 już
kompletna, brak nowego wydania). Wcześniej: **3.8 Zakładki WYDANE**,
**3.7 Import `.vcf` WYDANY**, **3.6 Wątek jednokierunkowy WYDANY**.

Wcześniej: **2.4 i 2.6** działają, aplikacja potrafi zweryfikować numer SMS-em.
Faza 2 (2.1–2.7) kompletna w kodzie. **3.5** (kanały wychodzące) zrobione.

**Stan wydań:** **v37 (1.0.36) WYDANY na produkcję** — zweryfikowane
`https://mini.verbigem.com/updates/version.json` → `versionCode: 37` oraz sha256
APK `app-debug-v37.apk?v=37` zgodny z lokalnym buildem. Zawiera v36 + fix „no
activity" z 2.6. APK: `mini/dist/android/app-debug-v37.apk`.
Poprzednio: **v36 (1.0.35) WYDANY** (Faza 5 media + fixy tr strings.xml),
przed nim **v33 (1.0.32)** z 3.9 („Możesz znać").

> **v27, v28, v29 nigdy nie trafiły na hosting** — wersjonowanie przeskoczyło z 26
> na 30, a potem 30 → 31 (3.7), 31 → 32 (3.8), 32 → 33 (3.9). Build v33 to najnowsza wersja po v26.

**Testy na telefonie (0.9, 1.14, 1.16, 1.17 push, 1.18 numer, 1.19 wyszukiwanie)
nadal są u Milosza** — bez nich nie ma podstaw, żeby uznać fazę 1 i 2 za domknięte.
- **1.18** wymaga wpisania odcisków SHA w konsoli Firebase (blok w sekcji 1).
- **1.19** wymaga **najpierw testu 0.9, potem backfillu** (`node
  backfill_searchtext.js --apply`) — `chats` jest w produkcji puste, nie ma czego
  indeksować, dopóki nie powstanie pierwsza rozmowa.

> **B6 rozstrzygnięte 2026-09-03 — Milosz miał rację tylko połowicznie.**
> Zweryfikowane na żywym kodzie (`ui/components/BottomNav.kt`):
> - ❌ „nie ma hardkodowanych etykiet" — **są**, i to mieszane EN/PL:
>   `"Translator"`, `"Rozmowa"`, `"Czat"`, `"Kontakty"`, `"Profil"`.
>   Co gorsza, `nav_*` istniały w `strings.xml` we wszystkich 6 językach i
>   **nie były nigdzie używane** (grep `R.string.nav_` = zero trafień).
> - ✅ „nie ma OCR" — faktycznie, `items` ma 5 pozycji i OCR nie ma wśród nich.
>   Natomiast `Screen.Ocr.route` **był** w `showBottomNav`, co dawało pasek
>   nawigacji na ekranie OCR bez możliwości powrotu/usunięcia zaznaczenia.
> **Rozstrzygnięcie:** etykiety → `stringResource`, OCR **wyjęty** z
> `showBottomNav` (5 ikon jest kompletnych, OCR zostaje na ekranie
> Translatora + systemowy back). Punkt B6 → zamknięty.

---

## 1. Postęp

| Faza | Nazwa | Status | versionCode | Commit |
|---|---|---|---|---|
| 0 | Ratunek fundamentów | 🟡 **kod gotowy, v26 na produkcji** (0.9 — test na telefonie został) | 26 | `bae1df0` |
| 1 | Skrzynka odbiorcza + wątek | 🟡 **kod gotowy, v26 na produkcji** (1.14 — test na telefonie został) | 26 | `bae1df0` |
| 1.12 | Wyszukiwanie w wiadomościach | 🟡 **wdrożone na produkcję** (1.19 — test na telefonie został; **backfill po 0.9**) | 28 | `1f30e72` |
| 1.13 | Karta kontaktu | 🟡 **kod gotowy** (1.16 — test na telefonie został) | 27 | `4f39292` |
| 2.1 | Szkielet `functions/` (Node 20 + TS) | ✅ **zrobione** (kod + dokumentacja w README) | — | `a4e65d5` |
| 2.2 | Secret Manager + App Check | ✅ **zrobione** (pepper ustawiony; App Check per wariant, egzekucja odroczona) | — | *w toku* |
| 2.3 | `matchContacts` (HMAC, whereIn po 30, rate limit) | 🟡 **wdrożone** (działa od 2.6 — katalog pusty) | 27 | *w toku* |
| 2.4 | `inviteByPhone` + `verifyPhone` + `onPhoneVerified` | 🟡 **wdrożone na produkcję** (test u Milosza został) | 27 | `3a50734` |
| 2.5 | FCM: tokeny + `onMessageCreated` → push | 🟡 **wdrożone na produkcję** (test push u Milosza został) | 27 | `a4e65d5` |
| 2.6 | Weryfikacja numeru w aplikacji (D3) | 🟡 **fix „no activity" WYDANY w v37** (SHA w konsoli + test u Milosza) | 37 | `3a50734` |
| 2.7 | Reguły `phoneDirectory` / `invites` | ✅ **częściowo zrobione** (reguły wdrożone; sama kolekcja czeka na 2.6) | — | *w toku* |
| 2 | Backend: Cloud Functions | 🟡 **kod kompletny** (2.1–2.7 zrobione; **testy na telefonie u Milosza**: 1.17 push, 1.18 numer) | 27 | `3a50734` |
| 3 | Kontakty 2.0 (import, kanały) | 🟢 **zrobione** (3.0–3.10) | 33 | `6afcdde` |
| 4 | Kody QR | 🟢 **zrobione** (4.1–4.3) | 34 | — |
| 5 | Media: zdjęcia + OCR, głosówki | 🟡 **w toku** (5.1–5.4 zrobione w kodzie, build OK; reguły Storage wdrożone 2026-09-04 — Firebase Storage włączone; release v35 czeka na testy telefonu Milosza) | — | — |
| 6 | Czaty grupowe | ⏸ odłożone (nie wybrane) | — | — |
| PP | Polityka prywatności (§12) | ✅ **zrobione** | 25 | `93c6fe1` |

## Karta testów na telefonie (u Milosza, v37)

Jedna lista zamiast sześciu rozrzuconych bloków. **Kolejność jest ważna** — 1.19
bez 0.9 i backfillu zwróci zero, bo w produkcji nie ma jeszcze żadnej wiadomości.

| # | Co | Jak sprawdzić | Blokuje |
|---|---|---|---|
| **1.18** | Weryfikacja numeru (2.6) | Profil → potwierdź numer → „wyślij SMS" → SMS przychodzi (lub weryfikacja w locie), ekran przechodzi do DONE. **Crash po kliknięciu naprawiony w v38** (usunięte `requireSmsValidation(true)`). Wcześniej „no activity" naprawione w v37. | — |
| **0.9** | Dodanie znajomego + pierwsza wiadomość | Znajomi → dodaj → napisz cokolwiek. Bez tego `chats` i `friendships` są w produkcji puste. | 1.19 |
| — | **Backfill** (po 0.9) | `cd functions && npm run build && cd .. && node backfill_searchtext.js` (dry run), potem `--apply` | 1.19 |
| **1.19** | Wyszukiwanie w wiadomościach | Napisz „kot ma Alego", szukaj `kot` (znajdzie), `kota` (**nie** znajdzie — to poprawne), `KOT` (znajdzie), `jęść`/`jesc` (znajdzie) | — |
| **1.14** | Czat — faza 1 | Skrzynka, wątek, tłumaczenie u odbiorcy, „pokaż oryginał", menu po długim naciśnięciu (kopiuj/czytaj/cytuj/usuń) | — |
| **1.16** | Karta kontaktu | Kliknięcie awatara/nicku w wątku → karta z aliasem, językiem, blokadą | — |
| **1.17** | Push FCM | Wiadomość od drugiej osoby przy zamkniętej aplikacji → powiadomienie, kliknięcie otwiera właściwy wątek | — |
| **5.x** | Media (Faza 5, w v36) | Wyślij zdjęcie w czacie → miniatura, kliknięcie = pełny ekran; nagraj głosówkę → transkrypcja STT na żywo | — |

**Co zrobione w sesji 2026-09-05 (fix 2.6 — „no activity", WYDANE w v37):**

- **Zgłoszenie:** w Profilu → potwierdzenie numeru → „wyślij SMS" kończy się
  komunikatem **„Nie udało się tego zrobić. no activity"**. SHA były już wpisane
  w konsoli Firebase, więc to nie była przyczyna.
- **Diagnoza:** komunikat pochodzi z jednego miejsca —
  `PhoneVerificationViewModel.sendCode`, `context.findActivity() == null`.
  `PhoneAuthOptions.setActivity()` wymaga prawdziwej Activity, a ViewModel dostaje
  tylko `LocalContext.current`. Ten kontekst okazywał się **nie** Activity, bo
  `MainActivity.LocalizationWrapper` podmieniał go na
  `context.createConfigurationContext(config)` — a to zwraca goły `ContextImpl`,
  **nie** `ContextWrapper`, więc łańcuch `baseContext` urywał się w tym miejscu.
  Ten sam mechanizm wcześniej wywalił `rememberLauncherForActivityResult`
  w `OcrScreen` (naprawione obejściem `LocalActivityResultRegistryOwner`,
  nie przyczyną).
- **Naprawa (3 pliki):**
  - `MainActivity.kt` — nowa prywatna klasa `LocalizedContext(base, locale)
    : ContextWrapper(base)`, nadpisująca tylko `getResources()` / `getAssets()`.
    Język działa identycznie, a Activity zostaje w łańcuchu.
  - `VerbigemApplication.kt` — `foregroundActivity()` (słaba referencja
    odświeżana w `MainActivity.onResume`) jako fallback, gdyby kiedyś znów nie
    dało się wyciągnąć Activity z kontekstu.
  - `PhoneVerificationViewModel.kt` — `context.findActivity()
    ?: VerbigemApplication.foregroundActivity()`.
- **Reguła na przyszłość (zapisana też w README):** każdy kontekst podmieniany
  w `LocalContext` **musi** dziedziczyć po `ContextWrapper` i mieć Activity
  u podstawy. Inaczej cicho giną wszystkie `findOwner<T>()`.
- **Wydanie:** `versionCode 37` / `1.0.36`, `assembleDebug`, APK →
  `mini/dist/android/app-debug-v37.apk`, `version.json` (updates + android) +
  `vite.config.ts` podbite na 37, `firebase deploy --only hosting`.
- **Test u Milosza (1.18):** Profil → potwierdź numer → „wyślij SMS" → SMS
  powinien przyjść, ekran przejść do pola z kodem, po wpisaniu → „gotowe".

**Co zrobione w sesji 2026-09-05 (fix 2.6 — crash po kliknięciu „wyślij SMS",
WYDANE w v38):**

- **Zgłoszenie (kontynuacja po v37):** po naprawie „no activity" (v37) aplikacja
  przestała wyświetlać błąd, ale **crashowała przy kliknięciu „wyślij SMS"**.
- **Diagnoza** (adb `dumpsys dropbox --print data_app_crash` z podpiętego
  telefonu): `java.lang.IllegalArgumentException: You cannot require sms
  validation without setting a multi-factor session` w `PhoneAuthOptions$Builder
  .build()` — wywołane z `PhoneVerificationRepository.sendCode`.
- **Przyczyna:** `.requireSmsValidation(true)` w `PhoneAuthOptions`. Flaga
  **istnieje TYLKO dla uwierzytelniania wieloskładnikowego (MFA)** i rzuca wyjątek,
  gdy nie ustawiono `setMultiFactorSession`. W v36 nigdy nie wybuchła, bo
  `sendCode` wcześniej przerywał na `findActivity() == null` (błąd „no activity");
  fix v37 odblokował wykonanie aż do `.build()`, więc wyjątek wyszedł na wierzch.
  ⚠️ W README (sekcja 2.6) i w skillu `verbigem-android-build-deploy` stała
  **błędna** adnotacja „requireSmsValidation(true) jest obowiązkowe" — to ona
  wprowadziła flagę. Obie poprawione.
- **Naprawa (2 pliki):**
  - `PhoneVerificationRepository.kt` — usunięto `.requireSmsValidation(true)` z
    obu builderów (`sendCode` + `resendCode`). Dodano `PhoneCodeRequest
    .AutoVerified` (niesie `PhoneAuthCredential` z `onVerificationCompleted`) oraz
    `confirmWithCredential()` / prywatne `link()` (sprawdza `auth.currentUser` i
    `startedUid`, by nie podmienić konta; potem `linkWithCredential` →
    `getIdToken(true)` → `verifyPhone`). `onVerificationCompleted` zamiast martwego
    logu zwraca teraz `AutoVerified(credential)`.
  - `PhoneVerificationViewModel.kt` — obsługa `PhoneCodeRequest.AutoVerified`:
    pobiera credential i woła `repository.confirmWithCredential(...)`, po sukcesie
    przechodzi do `PhoneVerificationStep.DONE`.
- **Wydanie:** `versionCode 38` / `1.0.37`, `assembleDebug` (BUILD SUCCESSFUL,
  4m33s, NDK OK), APK → `mini/dist/android/app-debug-v38.apk`, `version.json`
  (updates + android) + `vite.config.ts` podbite na 38, `firebase deploy --only
  hosting` (release complete). Lokalny sha256 APK: `6dea72f7…c9e7ad`.
- **Test u Milosza (1.18):** Profil → potwierdź numer → „wyślij SMS" → SMS
  przychodzi (lub weryfikacja w locie) → ekran przechodzi do DONE, bez crashu.

**Co zrobione w sesji 2026-09-04 (faza 1):**

- **1.1** ✅ `ChatMessage` v2: `senderTranslation` (`{lang, text}`), `type`,
  `clientMsgId`, zachowane legacy `translatedText` (stare wątki nadal się
  renderują przez `hintText()`). `sendMessage` przepisany na `set()` po
  `clientMsgId`.
- **1.2** ✅ Dokument `chats/{chatId}`: `members`, `lastMessage`,
  `lastMessageAuthorId`, `lastMessageAt` (upsert przed wiadomością — bez tego
  reguły odrzucają insert).
- **1.3** ✅ Trasy: `chat` = skrzynka (`ChatListScreen`), `chat/{uid}` = wątek
  (`ChatThreadScreen`). `ChatScreen` i `ChatViewModel` usunięte.
- **1.4** ✅ `ChatListViewModel` + skrzynka: avatar, nick (z `usersPublic`,
  fallback na nick ze znajomości), podgląd, godzina, kropka nieprzeczytanych.
- **1.5** ✅ **Tłumaczenie u odbiorcy (D1):** `chat_translations` w Room,
  `translateSegmented`, spinner, zapis do cache, `senderTranslation` jako
  natychmiastowy fallback. Jeden `Mutex` — model ładuje się raz.
- **1.6** ✅ Przełącznik „pokaż oryginał" (menu + podgląd oryginału pod bańką)
  i „Przetłumacz" ponownie (czyści wpis cache, wraca do auto-tłumaczenia).
- **1.7** ✅ Menu po długim naciśnięciu: kopiuj, czytaj (TTS offline),
  czytaj Pro 💎, pokaż oryginał / tłumaczenie, cytuj, usuń u mnie.
- **1.8** ✅ Kolejka offline `chat_outbox` + `ConnectivityObserver` wyzwala flush.
- **1.9** ✅ Paginacja: live listener na 50 najnowszych, `startAfter` przy
  dojechaniu na górę listy.
- **1.10** ✅ Potwierdzenia odczytu: **subkolekcja** `readReceipts/{uid}` zamiast
  `readBy` na wiadomości — patrz „Decyzja: readReceipts zamiast `update`".
- **1.11** ✅ Wskaźnik „pisze…": subkolekcja `typing/{uid}` z `expiresAt`,
  zapis throttlowany (4 s), heartbeat 1,5 s w VM odświeża wygaśnięcie.
- **1.12** 🟡 wyszukiwanie w wiadomościach — **wykonane** (szczegóły w bloku niżej).
- **1.13** ✅ karta kontaktu — **wykonana** (szczegóły w bloku niżej).
- **1.14** ⬜ test na telefonie (faza 1) — **zostaje dla Milosza**.
- **1.15** ✅ README + `chat_kontakty.md`; commit + push na koniec sesji.
- **1.16** ⬜ test na telefonie (karta kontaktu) — **zostaje dla Milosza**.
- **1.17** ⬜ test na telefonie (push FCM) — **zostaje dla Milosza** (patrz faza 2 niżej).
- **1.18** ⬜ test na telefonie (weryfikacja numeru 2.6) — **zostaje dla Milosza**,
  ale **najpierw trzeba wpisać SHA w konsoli Firebase** (blok niżej).
- **1.19** ⬜ test na telefonie (wyszukiwanie 1.12) — **zostaje dla Milosza**,
  ale dopiero **po backfillu** (blok niżej).

**Co zrobione w sesji 2026-09-04, noc (1.12 — wyszukiwanie w wiadomościach):**

- **1.12** 🟡 **Wyszukiwanie w wiadomościach — zrobione, czeka na backfill i test.**
  Z fazy 1 zostało odłożone z jednego powodu: `searchText` musiał zapisywać backend.
  Backend istnieje od 2.x, więc przeszkoda zniknęła.

  - **Trigger `onMessageSearchIndex`** (`functions/src/searchIndex.ts`), osobna
    funkcja od `onMessageCreated` choć reaguje na ten sam dokument: push i
    indeksowanie nie mają ze sobą nic wspólnego, a wspólny handler oznaczałby, że
    padnięty FCM zatrzymuje też wyszukiwanie (albo odwrotnie).
    `onDocumentCreated` + zapis `merge` — **nie ma pętli**, update nie wyzwala
    create.
  - **Reguła:** klient **nie może** napisać `searchText`
    (`!('searchText' in request.resource.data)`). Gdyby mógł, indeksowałby coś
    innego niż wysłał i wypychał własne wiadomości na każde cudze zapytanie.
  - **Normalizacja musi być identyczna po obu stronach** — `searchIndex.ts` ↔
    `data/MessageSearch.kt`: NFD → `\p{M}` → lowercase → trim → 2000 znaków.
    Bez NFD „jestes" nie znajdzie „jesteś". **Rozjazd objawia się ciszą:**
    wszystkie zapytania zwracają zero i nic w logach o tym nie mówi.
  - **Zapytanie per czat, nie collection group.** Group query przemiatałoby każdą
    rozmowę w bazie, a reguły nie potrafią go ograniczyć — dostęp decyduje `get()`
    na nadrzędnym czacie, czego group query nie wyrazi. Przejście po **własnej**
    liście rozmów trzyma odczyt wewnątrz dokumentów, których jestem członkiem.
    Brak `orderBy` (sortowanie po dacie wymagałoby indeksu złożonego — sortujemy
    kilkanaście trafień na urządzeniu).
  - **Prefiks, nie pełny tekst.** Firestore nie ma full-text; jedyna tania
    sztuczka to zakres `>= q` i `< q + "\uF8FF"`. „kot" znajdzie „kot ma Alego",
    ale **nie** „Ala ma kota" — **napisane wprost w UI**, bo inaczej użytkownik
    zalicza to jako błąd, a nie ograniczenie.
  - **Szukanie odpala się akcją „szukaj" na klawiaturze**, nie przy każdej
    literze: koszt to jedno zapytanie na rozmowę. Edycja pola cofa skrzynkę do
    listy rozmów (stare wyniki nie pasują do nowego tekstu). Minimum 3 znaki.
  - **Szukamy tylko w rozmowach widocznych w skrzynce** — zablokowane i usunięte
    są wykluczone, bo i tak bierzemy `chatId` z listy, którą widzi użytkownik.
    Szukanie w czymś, czego celowo nie pokazujemy, byłoby niespójne.
  - **Backfill `backfill_searchtext.js`** (dry run domyślnie, `--apply` zapisuje).
    Trigger indeksuje tylko nowe wiadomości. Skrypt **importuje** normalizację ze
    zbudowanego `functions/lib/searchIndex` — celowo nie niesie własnej kopii.
    Wymaga `cd functions && npm run build`.

**⚠️ KOLEJNOŚĆ: najpierw test 0.9, potem backfill, potem test 1.19.**
`chats` i `friendships` są w produkcji **puste** (sprawdzone REST-em) — nie ma
czego indeksować, dopóki Milosz nie doda znajomego i nie napisze pierwszej
wiadomości. Po teście 0.9:

```bash
cd functions && npm run build && cd ..
node backfill_searchtext.js            # dry run — sprawdzić liczniki
node backfill_searchtext.js --apply
```

- **1.19** — scenariusz testu: napisz „kot ma Alego", potem szukaj `kot`
  (znajdzie), `kota` (**nie** znajdzie — to jest poprawne zachowanie), `KOT`
  (znajdzie — lowercase po obu stronach), `jęść` vs `jesc` (polskie znaki
  obcinają akcenty po obu stronach). Wynik po kliknięciu otwiera właściwy wątek.

**Co zrobione w sesji 2026-09-04, w nocy (faza 3 — 3.3, 3.4, 3.5):**

- **3.3** ✅ **E.164 — zrobione wcześniej, przy 2.6.** `PhoneNumbers.kt` używa
  `android.telephony.PhoneNumberUtils.formatNumberToE164`, czyli libphonenumber
  wbudowanego w system — **zero nowych zależności**. Zgadywanie kraju (SIM →
  locale) daje listę kandydatów i haszujemy każdy. **Zostaje luka 3.3b:** ekspat z
  zagraniczną kartą SIM. Najlepszy możliwy sygnał to kraj **własnego** zweryfikowanego
  numeru — wymagałoby dopisania `phoneCountry` w `verifyPhone` (numeru nie trzymamy,
  tylko skrót, więc musi to policzyć funkcja).
- **3.4** ✅ **Trzy stany kontaktu — zrobione.** Ma Verbigem → „Napisz" (otwiera
  wątek). Nie ma → „Zaproś" (zapisuje zaproszenie pod skrótem numeru + otwiera
  kanał). Trzeciego stanu z §4.1 („nie ma, ale jest na WhatsApp") **nie da się
  odróżnić** bez zewnętrznego API — dlatego WhatsApp jest **kanałem**, nie stanem.
  Uczciwiej mieć przycisk, który otworzy WhatsApp, niż udawać, że wiemy.
- **3.5** ✅ **`OutboundChannel` — zrobione.** Pięć kanałów: WhatsApp, SMS, e-mail,
  Telegram, systemowy arkusz. Dostępność liczy się **per odbiorca** („SMS" znika,
  gdy wpis nie ma numeru). `OutboundTarget` to lekki nośnik na czas lotu, nie
  encja z 3.6. W 3.6 dostał dwa źródła: `OutboundTarget.from(PhoneContact)`
  (zaproszenie) i `ExternalThreadRepository.targetFor(ExternalContactEntity)`
  (wątek jednokierunkowy) — patrz sekcja 1.

  **⚠️ Plan §5.4 mylił się co do Telegrama.** Zakładał `t.me/<username>` i pójście
  na schowek, bo „URL Telegrama nie potrafi wkleić tekstu". Zwykły nie potrafi,
  ale **`t.me/share/url?url=…&text=…` potrafi**, i to z jednoczesnym podaniem
  linku — dokładnie tym, czego potrzebuje zaproszenie. Odpada więc snackbar
  „skopiowaliśmy do schowka". §5.4 poprawiony niżej.

  **Celowo nie używamy `SmsManager`.** `SEND_SMS` w polityce Google Play to
  wniosek, uzasadnienie i ryzyko odrzucenia — dla funkcji, która i tak musiałaby
  spytać użytkownika, czy na pewno. `ACTION_SENDTO` daje to samo za jednym
  kliknięciem.

  **Zaproszenie zapisujemy niezależnie od wybranego kanału.** Ten sam numer, a
  link i zaproszenie to dwa różne sposoby na ten sam cel: zaproszenie działa, gdy
  osoba kiedyś potwierdzi numer, link — gdy kliknie go od razu. Pominięcie
  któregoś zostawia połowę szansy.

**Co zrobione w sesji 2026-09-04, wieczorem (faza 3 — 3.7, WYDANE w v31):**

- **3.7** ✅ **Import `.vcf`** — `data/VcfImporter.kt` (własny parser, **zero
  zależności**): `FN`/`N`/`TEL`/`EMAIL`, wiele kart, `VERSION:2.1`+`3.0`, zwijanie
  długich linii (kontynuacja spacją/tabem). Celowo ignoruje `PHOTO`/grupy/adresy/
  wiele numerów na kartę — import to gest „dodaj tę osobę", nie migracja książki.
- Czytanie przez `ActivityResultContracts.OpenDocument` (mime `text/vcard`) →
  **nie wymaga `READ_CONTACTS`**. `VcfImporter.parseUri` rzuca przy błędnym URI,
  ViewModel łapie to w `runCatching` i pokazuje `contacts_import_vcf_failed`.
- Wynik w `ContactsViewModel.importedContacts` (StateFlow), scalony z książką w
  jedną listę w `ContactsScreen`; numery już obecne pomijane (dedup po `phone`).
  Importowany wpis otwiera ten sam wątek jednokierunkowy (3.6) co wpis z książki.
- 5 nowych stringów × 6 języków (`contacts_import_vcf`, `contacts_imported_title`,
  `contacts_import_vcf_done`, `contacts_import_vcf_none`, `contacts_import_vcf_failed`).

**Co zrobione w sesji 2026-09-04, noc (faza 3 — 3.8, WYDANE w v32):**

- **3.8** ✅ **Zakładki w Kontaktach.** `ContactsScreen` przepisany na `TabRow` +
  4 zakładki: `tab_friends` (Znajomi), `tab_invites` (Zaproszenia), `tab_phone`
  (Z telefonu), `tab_external` (Zewnętrzne). Każda zakładka to osobny composable
  (`FriendsTab` / `InvitesTab` / `PhoneTab` / `ExternalTab`); wspólny wiersz
  `ContactListRow(title, subtitle, onClick, trailing)`.
- Wybór zakładki to lokalny `selectedTab` (remember). Gdy pole wyszukiwania na górze
  jest niepuste, układ zakładek znika i pokazuje się `CombinedSearch` — jedna lista
  trafień z trzech źródeł (znajomi po nicku, książka+import po imieniu/numerze,
  zewnętrzni po imieniu/numerze), z nagłówkami sekcji. Puste pole wraca do zakładek.
- `ExternalTab` czyta `externalContacts` z VM (`ExternalThreadRepository.watchContacts()`
  w `init`) — zewnętrzni kontakty z Room (3.6). Kliknięcie otwiera wątek
  jednokierunkowy (3.6).
- 7 nowych stringów × 6 języków (`tab_friends`, `tab_invites`, `tab_phone`,
  `tab_external`, `no_invites`, `contacts_external_empty`, `no_results`).
- Pułapki przy budowie: przepisanie pliku zgubiło importy Compose (`Modifier`, `dp`,
  `sp`, `FontWeight`, `clip`, `Dialog`) — dosypane; `CombinedSearch` wymagał dwóch
  callbacków (PhoneContact dla trafień z książki przez `openExternal`, String dla
  zewnętrznych przez `onOpenExternalThread`).

**Co zrobione w sesji 2026-09-04, przed południem (faza 3 — 3.10, audyt stringów):**

- **3.10** ✅ **Audyt stringów × 6 (przejściowy, bez nowego wydania).** Skrypt
  przeszedł cały `app/src/main`: 257 użytych kluczy `R.string.*`, **każdy obecny
  w wszystkich 6 `values*/strings.xml`** (values / -pl / -de / -es / -zh / -tr, po
  267 kluczy każdy). Jedyny „brakujący" to `default_web_client_id` — wygenerowany
  przez Firebase/Google zasób wartości, celowo poza `strings.xml`.
- Zweryfikowano też **dynamiczne** `stringResource(channel.labelRes)` (klucze
  `channel_email`, `channel_email_subject`, `channel_other`, `channel_sms`,
  `channel_telegram`, `channel_whatsapp`) — kompletne w 6 językach.
- **Zero hardkodowanych literałów** w `Text(...)` w całym UI (skan po wszystkich
  `.kt` — 0 trafień). Emoji-przedrostki `💬`/`👤` w `ContactListRow` to dekoracja,
  nie tekst do tłumaczenia (i tak są spójne z `find_people`).
- Build v33 (`assembleDebug`) przeszedł czysto — `versionCode 33` potwierdzony.
- **Wniosek:** v33 już niesie pełny, przetłumaczony zestaw; 3.10 nie wymaga ani
  nowych stringów, ani wydania. **Test na telefonie (0.9, 1.14, 1.16, 1.17, 1.18,
  1.19) został u Milosza** — bez nich faza 1/2/3 nie jest formalnie domknięta.
- Nieodkryte luki: Node 20 runtime decommission **2026-10-30** (bump `runtime` w
  `firebase.json` + `engines.node` w `functions/package.json` przed tą datą).

**Co zrobione w sesji 2026-09-04, rano (faza 3 — 3.9, WYDANE w v33):**

- **3.9** ✅ **„Możesz znać".** Nowa Cloud Function `suggestFriends`
  (`functions/src/peopleMayKnow.ts`, `onCall`, `enforceAppCheck: false` jak
  `matchContacts`, rate-limit 30/h, `maxInstances: 10`). Serwer przegląda graf:
  czyta moje `friendships` (`whereArrayContains("members", me)`), dzieli na
  zaakceptowanych i oczekujących; dla każdego zaakceptowanego znajomego czyta JEgo
  zaakceptowane `friendships` i liczy `mutualCount` kandydata; wyklucza mnie /
  obecnych znajomych / oczekujących (oba kierunki); sortuje malejąco po
  `mutualCount`, cap 20, `lookupProfiles` → zwraca `{uid, nickname, photoURL,
  mutualCount}`. Żadnego indeksu złożonego. Wyeksportowana z `index.ts`.
- Klient: `PeopleMayKnowRepository.suggest()` (callable `suggestFriends` →
  `List<FriendSuggestion>`; każdy błąd = pusta lista, bo sekcja jest niekrytyczna).
  `ContactsViewModel` dostało `_suggestedFriends` StateFlow + `loadSuggestions()` w
  `init` (odsiewa osoby z `sentRequests`) + `dismissSuggestion(uid)` (tylko klient,
  bez zapisu); `sendRequest` usuwa kandydata z listy po wysłaniu.
- UI: sekcja „Możesz znać" na szczycie `FriendsTab` (przed polem szukania ludzi) —
  reuse `onSendRequest` + `onDismissSuggestion`, pokazuje nickname, „N wspólnych
  znajomych" (`people_you_may_know_mutual`) i przyciski +Dodaj / X. 3 nowe stringi
  × 6 (`people_you_may_know`, `people_you_may_know_mutual`, `dismiss`).
- Build v33 (`versionCode = 33`, `versionName = "1.0.32"`). Funkcja `suggestFriends`
  wdrożona **PO NAZWIE** (`FUNCTIONS_DISCOVERY_TIMEOUT=60 firebase deploy --only
  functions:suggestFriends --project mini-verbigem`) — 5 pozostałych funkcji
  produkcyjnych nietkniętych. APK `app-debug-v33.apk` skopiowany do
  `mini/dist/android/`, `version.json` (updates + android + `vite.config.ts`)
  podbicie na 33, `firebase deploy --only hosting`. Zweryfikowano `version.json` →
  33 i zgodność sha256 APK z lokalnym buildem.

**Co zrobione w sesji 2026-09-04, popołudniu (faza 3 — 3.6, WYDANE w v30):**

- **3.6** ✅ **Wątek jednokierunkowy dla osób bez Verbigema.** Całość lokalna w
  Room (`version = 8`, migracja `7_8`):
  - `external_contacts` (PK = `phone`, luźna forma z książki): nazwa, e164,
    e-mail, `lang` (puste = nie wybrano; nie da się wykryć), `lastUsedAt`.
  - `external_outbox` (auto-id): `phone`, `channel`, `originalText`,
    `translatedText`, `lang`, `createdAt`. `status` zawsze `handed_off` na stałe.
  - `ExternalContactDao`: `IGNORE` przy insercie (żeby wybrany język nie zginął
    przy ponownym odczycie książki), wąska `UPDATE` tylko dla pól z książki.
  - `ExternalThreadRepository`: remember / setLang / recordHandOff / forget /
    pruneHistory (90 dni) / targetFor.
  - `ExternalThreadViewModel`: tłumacz → przekaż; `HyMt2NativeEngine` zwalniany w
    `onCleared`; `load` anuluje poprzedni ładowanie, żeby stary historia-collector
    nie pisał w stan należący do kogoś innego.
  - `ExternalThreadScreen`: transparent „brak strony przychodzącej", wybór języka
    (blokada tłumaczenia, póki puste), historia, kompozytor z przyciskami kanałów.
  - **Wejście z książki:** `ContactsViewModel.rememberExternal` to `suspend`
    wywoływane **przed** nawigacją (wiersz klikiem otwiera wątek). Wątek czyta
    kontakt po kluczu telefonu — otwarty przed zapisem zastałby pusty ekran.
  - **Nawigacja:** `Screen.ExternalThread` (`external/{phone}`, telefon kodowany
    `Uri.encode`), wpis w `AppNavigation`, `onOpenExternalThread` z `ContactsScreen`.
  - **Historia trzyma `id` kanału**, nie tekst — `OutboundChannels.labelResFor(id)`
    rozwiązuje na dzisiejszy język przy wyświetlaniu.

**Co zrobione w sesji 2026-09-04, wieczorem (faza 2 — 2.4 i 2.6):**

- **2.4** 🟡 **Trzy nowe funkcje w `functions/src/invites.ts`:**
  - `verifyPhone` (callable, **bez argumentów**) — numer bierze z
    `request.auth.token.phone_number`, czyli z tokena który Firebase wystawia po
    Phone Auth. **Nie ma parametru do sfałszowania.** Zapisuje na `users/{uid}`
    `phoneVerified` + `phoneHash` + `phoneVerifiedAt` — i ani jednego numeru.
  - `inviteByPhone(hashes)` — zapisuje `invites/{hmac}_{fromUid}`.
    **Identyfikator zawiera `fromUid`**, inaczej druga osoba zapraszająca ten sam
    numer nadpisałaby pierwszą (plan mówił `invites/{hmac}` — to była usterka
    projektowa, poprawiona). Jeśli numer ma już konto, zamiast zaproszenia od razu
    powstaje zaproszenie do znajomych — wiszące zaproszenie nie zostałoby nigdy
    rozwiązane, bo trigger reaguje tylko na **zmianę** numeru.
  - `onPhoneVerified` (trigger `onDocumentWritten` na `users/{uid}`) — uzgadnia
    `phoneDirectory` (dopisuje nowy HMAC, usuwa stary przy zmianie numeru) i
    rozwiązuje czekające zaproszenia na znajomości. **Trigger, nie część
    `verifyPhone`:** `phoneDirectory` to dane pochodne, a dane pochodne powinny
    podążać za źródłem prawdy, nie za tym, który wywołujący akurat pamiętał.
    Pierwsza instrukcja to wczesne wyjście — dokument `users/{uid}` zmienia się
    przy każdej edycji profilu.
  - Znajomość tworzona jest **dokładnie** tak jak w `ChatRepository`
    (`{uidA}__{uidB}`, `uidA` = mniejszy leksykograficznie, `members`, `status:
    pending`, `requestedBy`, `nicknameA/B`), w transakcji — żeby wyścig dwóch
    zaproszeń nie wskrzesił odrzuconego.
  - Wspólne moduły: `phoneHash.ts` (SHA-256 / HMAC / E.164), `directory.ts`
    (`lookupDirectory`, `lookupProfiles`), `rateLimit.ts` (wspólne okno dla
    `matchContacts` i `inviteByPhone`), `secrets.ts`.

- **2.6** 🟡 **Weryfikacja numeru w aplikacji:**
  - `PhoneVerificationRepository` — Phone Auth → `linkWithCredential` na istniejącym
    koncie → `getIdToken(true)` → `verifyPhone`.
    **☠️ KLUCZOWE: `requireSmsValidation(true)`** w `PhoneAuthOptions`. Bez tego
    Firebase potrafi zweryfikować numer w locie albo przechwycić SMS-a i
    **samodzielnie zalogować użytkownika** — czyli wylogować go z dotychczasowego
    konta i wstawić nowe, oparte tylko na numerze. Opcja istnieje w
    `firebase-auth` 23.0.0 (sprawdzone `javap`iem w AAR).
    **`getIdToken(true)` też jest obowiązkowy** — dotychczasowy token powstał
    przed dodaniem numeru i nadal twierdzi, że go nie ma.
  - `PhoneVerificationScreen` + ViewModel: numer → SMS → kod, „Nie teraz".
    Bramka w `AppNavigation`: przy pierwszym wejściu w **Czat albo Kontakty**,
    nigdzie indziej (tłumacz to główna robota aplikacji — nagabywanie na starcie to
    najszybsza droga do kliknięcia „Pomiń" bez czytania).
  - **„Pomiń" jest trwałe**, więc droga powrotna musiała powstać: Profil →
    „Numer telefonu" → „Potwierdź numer". Bez tego wpisu pominięcie byłoby
    nieodwracalne.
  - Stringi × 6 (22 nowe klucze).

**☠️ WYMAGANE PRZED TESTEM 2.6 — odciski SHA w konsoli Firebase.**
Phone Auth nie zadziała, dopóki certyfikat podpisu nie będzie zarejestrowany:
Firebase Console → Project settings (koło zębate) → Your apps → aplikacja Android →
**Add fingerprint**. Dla debugowego keystore'a (`C:\Users\milo\.android\debug.keystore`):

```
SHA1:   EC:9D:EB:58:CD:F2:48:3A:7E:FE:2B:73:C2:C7:90:1B:9D:6D:3C:CC
SHA256: A4:2A:45:FF:D5:25:B9:02:8C:12:2B:BE:8A:92:FE:D9:F0:3D:A7:5C:9F:D1:CA:4D:90:98:8D:CA:AD:EC:CA:94
```

Trzeba wpisać **oba**, potem pobrać nowy `google-services.json` i podmienić go
w `app/`. Przy pierwszym wydaniu na Play to samo trzeba powtórzyć dla certyfikatu
wydania.

**⚠️ Normalizacja E.164 — zmieniona, i był to ostatni moment.**
Poprzednia uwaga („2.6 musi użyć tej samej lekkiej normalizacji") była błędnym
kierunkiem: lekka normalizacja nie potrafi rozwinąć `0981 123 456` do
`+595981123456`, więc w Paragwaju matching nie znalazłby **nikogo**. Nowy
`PhoneNumbers.kt` używa `android.telephony.PhoneNumberUtils.formatNumberToE164`
(biblioteka libphonenumber jest w systemie, bez nowej zależności) z domyślnym
krajem z karty SIM, a w razie braku — z locale urządzenia. Kraj zgadujemy, więc
`e164Candidates` zwraca **listę** możliwych postaci (zwykle jedną, czasem dwie) i
aplikacja haszuje każdą. Zmiana była możliwa tylko dlatego, że `phoneDirectory`
jest nadal pusty — po weryfikacji pierwszych numerów każda zmiana normalizacji
oznaczałaby przeliczenie całego katalogu.
Pozostała luka: ekspat z zagraniczną kartą SIM. Zakryje ją libphonenumber (3.x).

**Co zrobione w sesji 2026-09-04, po południu (faza 2 — 2.2, 2.3, 2.7):**

- **2.2** ✅ **Sekret ustawiony:** `PHONE_HASH_PEPPER` (32 bajty hex) w Secret Manager,
  wersja 1, ENABLED. **Rotacja unieważnia cały `phoneDirectory`** — ustawiony raz,
  zanim katalog powstał.
  ✅ **App Check:** `firebase-appcheck` + provider per wariant (`src/debug` → debug,
  `src/release` → Play Integrity, `debugImplementation` żeby release nie potrafił
  sam siebie poświadczyć).
  ⚠️ **`enforceAppCheck` ZOSTAJE `false`.** Dystrybuujemy **debugowy** APK, a Play
  Integrity nie poświadcza aplikacji nie zainstalowanych przez Play. Egzekucję
  włączyć razem z pierwszym wydaniem na Play — TODO w `contacts.ts`.
- **2.3** ✅ **`matchContacts` wdrożony.** Rate limiting: 20 wywołań/godzinę per uid,
  transakcja w `users/{uid}/rateLimits/matchContacts` (zwykły `increment` nie wystarcza
  — dwa równoległe wywołania przeczytałyby ten sam licznik). Odrzucone wywołanie nie
  przesuwa okna. Po stronie aplikacji: `ContactMatchRepository` (SHA-256 per numer,
  callable, mapowanie błędów na `resource-exhausted` itd.), dopasowane numery dostają
  przycisk „Napisz" zamiast „Zaproś". Stringi × 6.
- **2.7** ✅ **Reguły wdrożone (częściowo):** `phoneDirectory` i `invites` →
  `allow read, write: if false` (klient nie ma żadnego dostępu: odczyt = atak
  enumeracyjny, zapis = przypisanie cudzego numeru do siebie). `phoneVerified` /
  `phoneHash` na profilu **tylko do odczytu z klienta** (dopisane do blacklisty
  `create` i `update`). Sama kolekcja zapełni się w 2.6.

**☠️ NAJWAŻNIEJSZE ODKRYCIE DNIA — współdzielony projekt Firebase.**
`mini-verbigem` hostuje też **pięć funkcji webappu `verbigem/mini`**
(`deepseekProxy`, `paddleWebhook`, `portalSession`, `visionProxy`, `walletTopUp`
— wszystkie `europe-west1`) i oba projekty mają `codebase: default`.
`firebase deploy --only functions` stąd chce je **usunąć**. Wywaliło się tylko
dlatego, że tryb nieinteraktywny nie usuwa bez potwierdzenia; z `--force` skasowałoby
płatności, OCR i portfel.
**Zawsze:** `firebase deploy --only functions:onMessageCreated,functions:matchContacts`.
Zapisane w README. Trwałym rozwiązaniem byłyby osobne `codebase`, ale to wymaga
przewalczenia już wdrożonych funkcji.

**⚠️ Matching zadziała dopiero po 2.6.** `phoneDirectory` jest pusty — nikt jeszcze
nie zweryfikował numeru. Do tego czasu `matchContacts` zwraca `[]` i ekran Kontaktów
zachowuje się tak jak dotychczas (wszędzie „Zaproś"). To nie jest błąd.

**⚠️ Normalizacja numerów jest umownie lekka.** `PhoneContactsImporter.normalize()`
nie ma libphonenumber (TODO faza 3.3). Skrót liczymy z tego, co daje ta funkcja, więc
2.6 **musi** użyć dokładnie tej samej normalizacji, inaczej skróty się nie zgodzą i
matching będzie cicho zwracał zero.

**Co zrobione w sesji 2026-09-04 (faza 2 — 2.1 i 2.5):**

- **2.1** ✅ **`functions/` od zera:** `package.json` (Node 20, `firebase-admin` 12,
  `firebase-functions` 6), `tsconfig.json` (commonjs, strict, `noUnusedLocals`),
  `.gitignore` (`node_modules/`, `lib/`), `src/index.ts`. `npm install` + `npm run
  build` przechodzą czysto. `firebase.json` dostał blok `functions` z `runtime:
  nodejs20` i `predeploy: npm run build`. Sekcja „☁️ Cloud Functions" w README:
  tabela funkcji, komendy deployu, Secret Manager, 6 decyzji pushy.
- **2.5** ✅ **FCM end-to-end w kodzie:**
  - `functions/src/messaging.ts` — `onDocumentCreated(chats/{chatId}/messages/{msgId})`
    → `sendEachForMulticast` do pozostałych członków. `members` czytane z dokumentu
    czatu (wiadomość jest append-only i nie ma rosteru). Martwe tokeny kasowane po ID.
    `Promise.allSettled` — nieudany push **nigdy** nie failuje triggera (wiadomość już
    jest w Firestore, retry = podwójne powiadomienia).
  - **Treść pusha = `senderTranslation`, nie `text`** (D1: podpowiedź nadawcy powstała
    w języku odbiorcy). **Podgląd DOMYŚLNIE WYŁĄCZONY** — `app_config/notifications`
    musi mieć `showMessagePreview == true`, inaczej „Nowa wiadomość". Push wychodzi z
    urządzenia i idzie przez serwery Google, więc failuje zamknięty.
  - **Wyciszenie honorowane w chmurze** (`users/{odbiorca}/contacts/{nadawca}` →
    `muted === true`), nie na urządzeniu: wyciszony czat nie budzi telefonu.
  - **Android:** `VerbigemMessagingService`, `FcmTokenManager`
    (`users/{uid}/fcmTokens/{token}` — **ID dokumentu = token**, żeby funkcja mogła
    kasować bez odczytu), `VerbigemNotifications` (kanał `verbigem_messages`, grupa
    per czat + podsumowanie, MessagingStyle z historią, akcje),
    `NotificationActionReceiver` (odpowiedź + „oznacz jako przeczytane" pod
    `goAsync()`, bo robią zapis do Firestore).
  - **Kanał powiadomień** tworzony w `VerbigemApplication.onCreate`, nie przy
    pierwszym pushu — użytkownik szukający „jak to wyłączyć" znajdzie go w ustawieniach.
  - **Tap → wątek:** `MainActivity.intentForChat()` + `onNewIntent` + flow
    `openChatUid` w Compose. `AppNavigation` czeka na odtworzenie sesji Auth
    (do 3 s), bo na zimnym starcie `currentUser` jest jeszcze nullem.
  - **`POST_NOTIFICATIONS` proszony raz, przy pierwszym otwarciu skrzynki**
    (flaga `asked_notif_perm` w DataStore) — nie na starcie aplikacji: Android
    przestaje pytać po dwóch odmowach, więc szkoda je przepalać.
  - **Reguły Firestore:** `users/{uid}/fcmTokens/{token}` (właściciel + whitelist
    pól + `data.token == token`). **Nowe stringi × 6** (6 kluczy `notif_*`).
  - **Wylogowanie kasuje token przed `signOut()`** — po wylogowaniu `currentUser`
    jest null i dokument zostałby osierocony.

**⚠️ Pułapka: `MessagingStyle` ma własną metodę `apply()`.**
`NotificationCompat.MessagingStyle.apply { ... }` **nie wywołuje** kotlinowej
funkcji `apply` — `MessagingStyle` deklaruje własny element
`apply(NotificationBuilderWithBuilderAccessor)`, który ją przysłania. Kompilator
zgłasza „Unresolved reference" dla każdego wywołania wewnątrz lambdy. Na tej
klasie piszemy zwykłe instrukcje, nie `.apply {}`.

**⚠️ Decyzja: FCM jest `data-only` (bez pola `notification`).**
Z polem `notification` Android sam renderuje powiadomienie w tle i **nie wywołuje
`onMessageReceived`** — akcje „Odpowiedz" / „Oznacz jako przeczytane" działałyby
tylko dla wiadomości przychodzących przy otwartej aplikacji. Data-only daje pełną
kontrolę zawsze; ceną jest Doze, stąd `priority: "high"` i TTL 4 tygodnie.

**Wdrożone 2026-09-04:** reguły `fcmTokens` **i** funkcja `onMessageCreated`
(`firebase deploy`, projekt `mini-verbigem`, region `us-central1`).
**Push wymaga APK z tym kodem** (v27, jeszcze nie wydany) — tokeny rejestrują się
dopiero po uruchomieniu aplikacji z `FcmTokenManager`.

**⚠️ Pierwszy deploy funkcji 2. gen zawsze rzuca błąd Eventarc.** Firebase mówi
wprost: „retry the deployment in a few minutes" — uprawnienia Service Agent
propagują się z opóźnieniem. Drugi deploy przeszedł. Po udanym deployu CLI żąda
jeszcze `functions:artifacts:setpolicy` (obrazy kontenerów w Artifact Registry
rosną i kosztują); ustawione na 7 dni.

**⚠️ `matchContacts` jest napisany, ale celowo NIE wyeksportowany**
(zakomentowany w `functions/src/index.ts`). Deklaruje sekret `PHONE_HASH_PEPPER`,
a Firebase weryfikuje istnienie sekretów przy deployu — wyeksportowanie go
przed `functions:secrets:set` blokuje **każdy** deploy, także pushowy. Włączyć
przy 2.3.

**1.17** ⬜ **test push na telefonie — zostaje dla Milosza:** wysłać wiadomość
z drugiego konta (aplikacja w tle / ekran zgaszony), sprawdzić powiadomienie,
akcję „Odpowiedz" (czy wiadomość doszła) i „Oznacz jako przeczytane", oraz
tap → czy otwiera właściwy wątek. Podgląd treści jest domyślnie WYŁĄCZONY,
więc na ekranie blokady ma być „Nowa wiadomość", nie treść.

**1.18** ⬜ **test weryfikacji numeru (2.6) — zostaje dla Milosza:**
0. **Warunek wstępny:** SHA1 + SHA256 debugowego keystore'a wpisane w Firebase
   Console → Project settings → Your apps → Android → **Add fingerprint**,
   pobrany nowy `google-services.json` i podmieniony w `app/`. Bez tego SMS
   w ogóle nie przyjdzie.
1. Wejść w Czat (albo Kontakty) → bramka ma się pokazać **raz**.
2. Wpisać swój numer, dostać SMS, wpisać kod → ekran „potwierdzony".
3. **Sprawdzić, czy nie zostałeś wylogowany** — to najważniejszy punkt tego testu.
   Jeśli po weryfikacji profil jest pusty albo każe się logować, to znaczy, że
   `requireSmsValidation(true)` nie zadziałało i Firebase podmieniło konto.
4. Profil → „Numer telefonu" ma pokazywać „Potwierdzony".
5. Zaprosić z drugiego konta numer, który **nie ma** konta → w Konsoli powinien
   pojawić się dokument `invites/{hmac}_{uid}`. Potem zweryfikować ten numer na
   drugim koncie → w `friendships` ma powstać znajomość `pending`, a dokument
   z `invites` ma zniknąć.
6. „Nie teraz" → bramka nie wraca; droga powrotna to Profil → „Potwierdź numer".

**Co zrobione po fazie 1 — 1.13 Karta kontaktu (2026-09-04):**

- **Model + repozytorium.** `ContactSettings` w `CommonModels.kt` (`alias`,
  `langOverride`, `muted`, `pinned`, `blocked`, `note`, `updatedAt`).
  `ChatRepository`: jeden listener `watchContactSettings(uid)` na całą
  subkolekcję (jeden subskrypcja zamiast N) + `saveContactSettings` /
  `clearContactSettings`. Błąd zapisu idzie tylko do logcatu — utrata aliasu
  jest irytująca, utrata wątku nie do przyjęcia, więc nigdy nie wywala UI.
- **Ekran `ContactCardScreen` + `ContactCardViewModel`.** Alias, język
  tłumaczenia (Auto + 6 języków jako chipy), przypnij / wycisz / zablokuj,
  notatka, „Napisz wiadomość", „Usuń rozmowę" z dialogiem potwierdzenia.
  Wchodzi się z nagłówka wątku i z ikony ⓘ przy znajomym w Kontaktach.
- **Zapis z debounce 500 ms** dla pól tekstowych (alias, notatka), natychmiastowy
  dla przełączników. Flaga `pendingWrite` wycisza listener Firestore w trakcie
  własnego zapisu — bez niej snapshot przychodzący w środku pisania cofałby tekst.
- **Integracja ze skrzynką:** alias w nazwie, przypięte na górę
  (`compareByDescending<ChatRow> { it.pinned }.thenByDescending { it.lastMessageAt }`),
  zablokowane i usunięte odfiltrowane, wyciszone bez kropki nieprzeczytanych.
- **Integracja z wątkiem:** nagłówek klikalny → karta; `translationLang`
  (override ❔ profil) steruje celem tłumaczenia; zmiana języka czyści mapę
  w pamięci, ale nie rusza cache w Room (kluczowany językiem).
- **Room v6→v7:** tabela `chat_hidden` dla „usuń rozmowę". Wiadomości w Firestore
  są append-only i nie ma funkcji, która by je sprzątała, więc usunięcie może być
  tylko lokalnym ukryciem — dialog mówi to wprost.
- **Stringi × 6 + reguły Firestore wdrożone** (`users/{uid}/contacts/{otherUid}`:
  właściciel + whitelist 7 pól).
- **Build:** `assembleDebug` przeszedł (1m20s, NDK z cache).

**Uczciwość funkcji — zapisane w stringach, nie tylko w komentarzach:**
„zablokuj" i „wycisz" są **lokalne** (brak Cloud Functions do egzekwowania
blokady i brak pushy do wyciszania), a „usuń rozmowę" **nie kasuje** wiadomości
u rozmówcy. Teksty w UI o tym mówią — nie wolno ich „poprawić" na brzmiące lepiej,
dopóki faza 2 nie dowiezie prawdziwej blokady.

> **Decyzja: `readReceipts` zamiast łagodzenia `update` na `messages`.**
> Plan zakładał dopuszczenie `update` na wiadomościach, żeby odbiorca mógł
> zmienić własny klucz w `readBy`. Reguła musiałaby analizować diff zagnieżdżonej
> mapy (`resource.data.get('readBy', {}).diff(...)`), co jest kruche (stare
> dokumenty bez `readBy`), i otwiera furtkę na modyfikację cudzych wiadomości.
> Zamiast tego każdy uczestnik ma **jeden własny dokument** w subkolekcji —
> reguła to po prostu `request.auth.uid == uid`. Wiadomości w Firestore
> zostają **append-only** (`update, delete: if false`), a „usuń u mnie"
> realizują lokalne tombstone'y w Room. B5 uznajemy za rozwiązane inaczej.

**Odłożone z fazy 1 (świadomie, nie zapomniane):**

- ~~**1.12 Wyszukiwanie w wiadomościach**~~ — **WYKONANE** (2026-09-04), patrz
  blok niżej. Czekało dokładnie na to, po co była mowa w fazie 1: trigger
  zapisujący `searchText`. Backend istnieje od 2.x, więc przeszkoda zniknęła.
- ~~**1.13 Karta kontaktu**~~ — **WYKONANE** (2026-09-04), patrz blok wyżej.
  Zrobione bez czekania na test telefonu, bo to własny tor: nie dotyka
  wysyłania ani tłumaczenia, tylko nakłada się na już działającą skrzynkę.

**Co zrobione w sesji 2026-09-03 → 04, noc (faza 0):**

- **Faza 0 — wykonana w całości poza testem na telefonie:**
  - **0.1** ✅ `AuthRepository.ensureProfile()` / `updateProfile()` utrzymują
    `usersPublic/{uid}` (nick, e-mail, avatar, `uiLang`, `speakLangSource/Target`,
    zlowercasowane `searchNick`/`searchEmail`). `ensureProfile` jest też
    samoleczącym backfillem — każde logowanie odnawia wizytówkę.
    Nowe `PublicProfile` w `CommonModels.kt` + `getPublicProfile(uid)`.
  - **0.2** ✅ `backfill_faza0.js` (domyślnie dry-run, `--apply` zapisuje).
    **Uruchomiony: 4/4 konta uzupełnione, zweryfikowane odczytem z Firestore.**
  - **0.3** ✅ `ContactsViewModel.searchUsers()` pyta `usersPublic` po
    `searchNick` **i** `searchEmail` (dwa zapytania — Firestore nie ma OR),
    wyniki scalone i zdeduplikowane po uid. Błąd przestał się chować: idzie do
    logcatu zamiast udawać „zero wyników".
  - **0.4** ✅ `Friendship` zyskało `members: List<String>` (posortowane) plus
    helpery `otherUid()` / `otherNickname()` / `isIncoming()` / `isOutgoing()`.
  - **0.5** ✅ `ChatRepository`: **jeden** listener
    `whereArrayContains("members", uid)` + pochodne `watchAccepted` /
    `watchIncoming` / `watchOutgoing`. `requestFriendship` zapisuje `members`.
    Asymetria uidA usunięta.
  - **0.6** ✅ `ChatViewModel`: język z profilu (mój = `speakLangSource`,
    rozmówcy = jego `speakLangSource` z `usersPublic`). `speak()` czyta w języku
    aktualnie wyświetlanego tekstu. `translateSegmented` zamiast `translate`.
  - **0.7** ✅ Reguły wdrożone (`firebase deploy --only firestore:rules`):
    `usersPublic` — zapis tylko właściciela; `friendships` — odczyt/zapis po
    `members`, zablokowana zmiana składu i pól funkcji.
  - **0.8** ✅ Etykiety BottomNav → `stringResource(R.string.nav_*)`.
    OCR wyjęte z `showBottomNav` (patrz werdykt B6 na górze pliku).
  - **0.9** ⬜ **test na telefonie — zostaje dla Milosza.**
- **Znaleziska naprawione przy okazji (nie było na liście fazy 0):**
  - **Czat w ogóle nie mógł wysłać wiadomości.** Reguły `messages` robią
    `get(/chats/$(chatId)).data.members`, a `sendMessage()` nigdy nie tworzyło
    dokumentu czatu. Teraz `chats/{chatId}` jest upsertowany z `members`
    **przed** dodaniem wiadomości.
  - **5 hardcodedowanych polskich stringów** (`"Odsłuchaj"`, `"Wyślij"` ×2,
    `"Mów"`, `"OK"`) → nowe klucze `chat_read_aloud`, `action_search`,
    `action_speak`, `ok` × 6 języków.

**Gdzie skończyliśmy:** faza 0 w kodzie gotowa, reguły na serwerze, dane
zbackfillowane. **APK nie wydany** — brak commita, pusha i podbicia wersji.
**Następny krok:**
1. **0.9** — zainstalować, zalogować się na dwa konta, dodać znajomego,
   sprawdzić że obie strony go widzą + wyszukać po nicku i e-mailu.
2. **Wydanie v26** (nowy `versionCode`, APK `app-debug-v26.apk`,
   `vite.config.ts` + `dist/updates/version.json`, `firebase deploy`).
3. **Faza 1** — skrzynka odbiorcza i porządny wątek.

**Ważne pułapki do pamiętania:**
- **Skrypty Node z Firestore (`write_firestore.js`, `update_firestore_update.js`)
  mają MARTWĄ ścieżkę odświeżania tokena.** Siedzą w nich na sztywno dwa różne
  `client_id` (oba błędne) i jeden `client_secret` — `oauth2.googleapis.com`
  odpowiada `invalid_client`, więc po wygaśnięciu tokena skrypt umiera.
  **Działające obejście:** odśwież przez CLI (`firebase projects:list` — odczyt,
  zero skutków ubocznych), który ma poprawne poświadczenia wbudowane i zapisuje
  świeży `access_token` do `~/.config/configstore/firebase-tools.json`; potem
  skrypt czyta go z dysku. Tak robi `backfill_faza0.js`.
- Token z `firebase login` wygasa po ~1 h (`expires_at` w configstore). Skrypt
  musi sprawdzać `expires_at`, nie zakładać że `access_token` jest żywy.
- **REST API Firestore z tokenem właściciela projektu omija reguły bezpieczeństwa**
  — backfill mógł zapisać cudze `usersPublic/{uid}` mimo reguły „tylko
  właściciel". Wygooglać się nie da: to cecha, nie bug. Pamiętać, że skrypt
  backfillu może więcej niż aplikacja.
- `/privacy/**` w `firebase.json` ma cache 1h (**nie** `immutable`), styl
  inlinowany (bo `**/*.css` ma `immutable`).
- Pliki polityki są w `mini/dist/privacy/` **i** `mini/public/privacy/`
  (deploy bierze z `dist/`; `public/` przeżyje `npm run build`).
- Przed deployem polityki **nie odpalać `npm run build`** (czyści `dist/`).

---

## 2. Ustalone decyzje

Potwierdzone przez Milosza 2026-09-03. Nie zmieniać bez nowej dyskusji.

| # | Decyzja | Ustalenie |
|---|---|---|
| D1 | **Gdzie tłumaczymy wiadomości** | **U odbiorcy.** Nadawca wysyła oryginał + `sourceLang` + własne tłumaczenie jako podpowiedź (fallback dla odbiorców bez pobranego modelu). Odbiorca tłumaczy lokalnie Hy-MT2 na swój język. |
| D2 | **Backend (Cloud Functions)** | **Dopuszczony.** Sekwencjonowanie: fazy 0–1 czysto klientowe, `functions/` wchodzi w fazie 2 (matching numerów + FCM). |
| D3 | **Weryfikacja numeru telefonu** | **Leniwa, nie przy rejestracji.** Gate przy pierwszym wejściu w Czat lub Kontakty (albo przy kliknięciu „Znajdź znajomych"). Opcja „Pomiń" — czat działa, ale użytkownik nie jest odnajdywalny po numerze i nie używa matchingu. |
| D4 | **Dodatki w zakresie** | Kod QR + skaner, zdjęcia z OCR w czacie, głosówki (nagraj → tekst). |
| D5 | **Czaty grupowe** | Odłożone / nie wybrane. Faza 6 zostaje w planie jako opcjonalna. |
| D6 | **Plan Blaze** | **Potwierdzony — Milosz ma Blaze** (2026-09-03). Cloud Functions i FCM nie są zablokowane kosztowo. Nadal pilnować limitów i App Check. |
| D7 | **Polityka prywatności** | **OPUBLIKOWANA (2026-09-03).** 6 wersji językowych (PL/EN/DE/ES/ZH/TR) na `mini.verbigem.com/privacy/`, link z aplikacji (Profil) + do zgłoszenia w Google Play. Kontakt: **`privacy@verbigem.com`**. Szczegóły w §12. Faza 3 odblokowana. |

---

## 3. Diagnoza stanu obecnego

Sześć realnych błędów. **Faza 0 musiała pójść pierwsza** — nadbudowa na tym
fundamencie to budowanie na piasku.

**Stan po fazie 0 (2026-09-04):** B1 ✅, B2 ✅, B3 ✅ (częściowo — pełne
tłumaczenie u odbiorcy to 1.5), B6 ✅. **B4 i B5 zostają na fazę 1.**

| # | Błąd | Gdzie | Skutek | Stan |
|---|---|---|---|---|
| B1 | **Wyszukiwanie ludzi nie działa wcale.** `searchUsers()` odpytuje `users/`, reguły pozwalają czytać tylko własny dokument. Reguły przewidują `usersPublic/{uid}`, ale **żaden kod tego dokumentu nie tworzy** (grep: zero wystąpień). | `ContactsViewModel.kt:76` | Każde wyszukiwanie → `PERMISSION_DENIED`. | ✅ **NAPRAWIONE.** `AuthRepository` utrzymuje `usersPublic`, wyszukiwanie pyta `searchNick`/`searchEmail`, backfill wykonany (4/4 konta). |
| B2 | **Znajomi tylko w jedną stronę.** `watchFriendships()` filtruje `whereEqualTo("uidA", uid)`. Osoba zaproszona (jako `uidB`) nigdy nie widzi znajomego. | `ChatRepository.kt:60-68` | Połowa zaproszeń znika po akceptacji. | ✅ **NAPRAWIONE.** `members: [uidA, uidB]` + jeden listener `whereArrayContains`. Trzy strumienie pochodne (`accepted`/`incoming`/`outgoing`). |
| B3 | **Tłumaczenie hardkodowane PL→EN.** `hyMt2Engine.translate(text, PL, EN)` ignoruje profile obu stron. `speak()` też ma na sztywno `"en"` dla przychodzących. | `ChatViewModel.kt:68`, `ChatScreen.kt:141` | Czat tłumaczy w losowy język. | 🟡 **NAPRAWIONE CZĘŚCIOWO.** Język bierze się z profilu obu stron, `speak()` czyta w języku wyświetlanego tekstu. Nadal brak tłumaczenia u odbiorcy (1.5) — odbiorca widzi podpowiedź nadawcy. |
| B4 | **Brak listy konwersacji.** Ekran Czatu to napis „wybierz znajomego w Kontaktach". Nie ma jak wrócić do toczącej się rozmowy. | `ChatScreen.kt:64-87` | Czat nieużywalny jako komunikator. | ⬜ faza 1 (1.3, 1.4) |
| B5 | **Reguły blokują `update` na wiadomościach** (`allow update, delete: if false`). | `firestore.rules` → `chats/{chatId}/messages` | Potwierdzenia odczytu, edycja i usuwanie są dziś niemożliwe. | ⬜ faza 1 (1.10) |
| B6 | **BottomNav.** Etykiety hardkodowane (mieszane EN/PL), a `Screen.Ocr.route` był w `showBottomNav` mimo braku pozycji OCR w `items`. | `BottomNav.kt:41-47`, `AppNavigation.kt:55-62` | Nav nie do przetłumaczenia; pasek na ekranie OCR bez możliwości powrotu. | ✅ **NAPRAWIONE.** Etykiety → `stringResource(R.string.nav_*)`, OCR wyjęte z `showBottomNav`. |

> **B7 — znaleziony przy okazji, już naprawiony:** czat **w ogóle nie mógł
> wysłać wiadomości.** Reguły `chats/{chatId}/messages` robią
> `get(/chats/$(chatId)).data.members`, a `sendMessage()` nigdy nie tworzyło
> dokumentu czatu — każda próba leciała na `PERMISSION_DENIED`. Teraz
> `chats/{chatId}` jest upsertowany z `members` **zanim** poleci wiadomość.

---

## 4. Co jest technicznie możliwe

Sprawdzone, nie zgadywane. Tu leży najważniejsza korekta do pierwotnego pomysłu.

| Źródło | Import listy | Wysyłka do konkretnej osoby | Decyzja |
|---|---|---|---|
| **Książka telefoniczna** (`ContactsContract`) | ✅ pełny (imiona, numery, e-maile, zdjęcia) | ✅ SMS z prefillem | **Główne źródło listy** |
| **WhatsApp** | ❌ brak ContentProvidera od lat | ⚠️ `https://wa.me/<E164>?text=…` — działa tylko dla numerów zarejestrowanych w WA, odpowiedzi nie wracają | **Kanał wysyłki** |
| **Telegram** | ❌ tylko TDLib/MTProto (wymaga api_id, logowania jako użytkownik, ryzykowne ToS) | ⚠️ `https://t.me/<username>` otwiera czat, ale **URL nie potrafi wkleić tekstu** | **Tylko zaproszenie** |
| **Plik .vcf** (eksport z WhatsApp / Google / Telegram) | ✅ | zależnie od kanału | **Import ręczny, ~120 linii, zero zależności** |
| Signal, Messenger, Instagram | ❌ | ❌ | Pomijamy — zostaje systemowy share sheet |

### 4.1 Model docelowy: jedna lista, wiele kanałów

WhatsApp nie da listy kontaktów — **ale to bez znaczenia**, bo WhatsApp opiera się
na numerach telefonu. Lista z książki telefonicznej *jest* w ~90% listą Twoich
kontaktów WhatsApp. Telegram jest wyjątkiem (username'y zamiast numerów) i dlatego
obsługujemy go wyłącznie jako kanał zaproszenia.

```
Książka telefoniczna (+ opcjonalnie .vcf)
        │
        ├─ normalizacja do E.164 (libphonenumber)
        ├─ SHA-256 każdego numeru
        └─ Cloud Function matchContacts() → kto ma Verbigem?
                │
        ┌───────┴────────┬──────────────────┐
        ▼                ▼                  ▼
   MA VERBIGEM      NIE MA, JEST NA WA     NIE MA
   zielony badge    "Zaproś" / "Napisz"    "Zaproś SMS"
   "Dodaj" /        przez WhatsApp         "Napisz SMS"
   "Napisz"         (tłumacz → przekaż)
   (pełny czat)
```

Kontakty bez Verbigema trzymamy **lokalnie w Room** (tabela `external_contacts`),
razem z historią tego, co im wysłaliśmy (`outbox_messages`).

### 4.2 Jak działają zaproszenia

- **Firebase Dynamic Links zostało wyłączone przez Google (sierpień 2025).**
  Zostaje zwykły link `https://mini.verbigem.com/app?inv=<uid>` + systemowy
  share sheet (`ACTION_SEND`) — działa z WhatsApp, Telegramem, e-mailem, SMS-em,
  z dowolną aplikacją na telefonie, **bez żadnej integracji**.
- `invites/{phoneHash}` = `{fromUid, fromName, createdAt}` — zapisywane przez
  Cloud Function. Gdy zaproszona osoba zweryfikuje numer, druga funkcja znajduje
  oczekujące zaproszenia i tworzy gotowe do akceptacji znajomości.

### 4.3 Prywatność numerów telefonów

- Do chmury wysyłamy **wyłącznie hashe**, nigdy numeru wprost.
- Klient: `SHA-256(E.164)`. Funkcja: `HMAC-SHA256(hash, pepper)` z pepperem
  w Secret Managerze. Kolekcja `phoneDirectory` trzyma HMAC, **nie jest czytelna
  dla klienta** — dostęp tylko przez funkcję.
- Dlaczego tak, a nie odwrotnie: gdyby klient wysyłał hashe swojej całej książki
  adresowej do zbioru do odczytu, serwer magazynowałby numery osób, które
  **nie są** użytkownikami. Model „każdy publikuje tylko swój numer" tego unika.
- Ochrona: App Check (Play Integrity) na wszystkich funkcjach + rate limiting.
- **Znane ryzyko MVP:** nawet sam HMAC można zaatakować rainbow table, jeśli
  pepper wycieknie. Mitygacja w fazie 2, akceptowalne na start.

---

## 5. Architektura docelowa

### 5.1 Firestore — nowe i zmieniane kolekcje

```
users/{uid}
  + phoneVerified: Boolean            (tylko funkcja może ustawić)
  + phoneHash: String                 (HMAC, tylko funkcja)

usersPublic/{uid}                     ← NAPRAWA B1, dziś nie istnieje
  uid, nickname, photoURL, uiLang, speakLangTarget
  searchNick (lowercase), searchEmail (lowercase)
  discoverableByPhone: Boolean

friendships/{id}                      ← NAPRAWA B2
  members: [uidA, uidB]               (zapytanie: whereArrayContains)
  uidA, uidB                          (zachowane dla kompatybilności)
  status: pending | accepted | blocked
  requestedBy, nicknameA, nicknameB, createdAt

users/{uid}/contacts/{otherUid}       ← ustawienia per kontakt (prywatne)
  alias, langOverride, muted, pinned, blocked, note

chats/{chatId}                        (chatId = posortowane uidA__uidB)
  members: [uidA, uidB]
  lastMessage, lastMessageAuthorId, lastMessageAt
  readState: { uid: lastReadAt }      (inkrementowane przez odbiorcę)
  typing: { uid: expiresAt }

chats/{chatId}/messages/{msgId}
  authorId, sourceLang, text          ← ORYGINAŁ, zawsze
  senderTranslation: { lang, text }   ← podpowiedź nadawcy (fallback)
  type: text | image | audio
  attachment: { storagePath, width, height, ocrText, ocrLang, durationMs }
  createdAt, clientMsgId              (idempotencja kolejki offline)
  readBy: { uid: timestamp }

phoneDirectory/{hmac}                 ← tylko funkcja, brak dostępu klienta
  uid

invites/{hmac}                        ← tylko funkcja
  fromUid, fromName, createdAt

users/{uid}/fcmTokens/{token}         ← rejestracja tokenów do pushy
  token, platform, updatedAt
```

### 5.2 Room — migracja v5 → v7

Cztery nowe tabele w v6 + jedna w v7. Wzorowane na istniejącym `pending_deletes`
(małe, wyspecjalizowane).

- **`chat_hidden`** (v6→v7, zadanie 1.13) — rozmowy usunięte ze skrzynki.
  `chatId TEXT PK, hiddenAt INTEGER`. Wiadomości w Firestore są append-only, więc
  „usuń rozmowę" nie ma jak ich skasować — zostaje ukrycie lokalne.
  **Ustawienia per kontakt NIE trafiły do Room** — idą do Firestore
  (`users/{uid}/contacts/{otherUid}`), bo Firestore i tak trzyma cache offline,
  a ustawienia mają iść za kontem na drugie urządzenie bez nowego mechanizmu syncu.

- **`chat_translations`** — cache tłumaczeń, żeby nie mielić modelu przy każdej
  rekompozycji i żeby działało offline.
  `msgId TEXT PK, chatId TEXT, targetLang TEXT, translatedText TEXT, updatedAt INTEGER`
- **`chat_outbox`** — kolejka offline (spójna z filozofią syncu w projekcie).
  `clientMsgId TEXT PK, chatId TEXT, text TEXT, sourceLang TEXT, senderTranslation TEXT, createdAt INTEGER, status TEXT, attempts INTEGER`
- **`external_contacts`** — kontakty spoza Verbigema.
  `id INTEGER PK, displayName TEXT, phoneE164 TEXT, email TEXT, channel TEXT, verbigemUid TEXT NULL, lastInvitedAt INTEGER, lastMessagedAt INTEGER, note TEXT, createdAt INTEGER`
- **`outbox_messages`** — historia wysyłek ręcznych (SMS / WhatsApp / Telegram).
  `id INTEGER PK, externalContactId INTEGER, channel TEXT, originalText TEXT, translatedText TEXT, targetLang TEXT, sentAt INTEGER, status TEXT`

### 5.3 Cloud Functions (`functions/`, Node 20 + TypeScript)

- `matchContacts(hashes: string[])` — callable, HMAC + `whereIn` (chunkowanie po 30,
  limit Firestore dla `in`), zwraca `{index, uid, nickname, photoURL}[]`.
- `inviteByPhone(hashes: string[])` — callable, zapisuje `invites/{hmac}`.
- `onPhoneVerified` (trigger na zmianę `users/{uid}.phoneVerified`) — dopisuje
  `phoneDirectory/{hmac}` i rozwiązuje oczekujące zaproszenia.
- `onMessageCreated` — trigger na `chats/{chatId}/messages`, wysyła FCM do odbiorców.
- **Wymaga planu Blaze** (Cloud Functions nie działają na Sparku — brak ruchu
  wychodzącego). Darmowe limity pokrywają wczesny ruch; do sprawdzenia przed fazą 2.

### 5.4 Warstwa kanałów dostarczenia

Jeden interfejs, cztery implementacje. Wspólne: tłumaczymy w Verbigem, potem
przekazujemy do aplikacji zewnętrznej przez `Intent`.

```kotlin
interface OutboundChannel {
    fun isAvailable(context: Context): Boolean
    fun handOff(context: Context, target: ExternalContact, text: String)
}
```

| Kanał | Mechanizm | Ograniczenie |
|---|---|---|
| WhatsApp | `ACTION_VIEW` → `https://wa.me/<E164>?text=<urlencoded>`, `setPackage("com.whatsapp")` + fallback na chooser | Tekst jest wklejony, użytkownik musi kliknąć wysłanie. Numer musi być na WA. |
| SMS | `ACTION_SENDTO` → `smsto:<E164>` + `EXTRA_SMS_BODY` | Prewypełnione, wymaga jednego kliknięcia. **Nie używamy `SmsManager`** — uprawnienie `SEND_SMS` to problem z polityką Google Play. |
| Telegram | `ACTION_VIEW` → `https://t.me/share/url?url=<link>&text=<tekst>` | Otwiera **wybór rozmowy**, nie konkretną osobę — nie da się trafić do kogoś po numerze. |
| E-mail | `ACTION_SENDTO` → `mailto:` + `EXTRA_SUBJECT` + `EXTRA_TEXT` | Wymaga adresu; importer czyta je osobnym zapytaniem po `CONTACT_ID`. |

> **Poprawka do §5.4 (2026-09-04, przy 3.5):** pierwotna tabela zakładała
> `t.me/<username>` i pójście na schowek, bo „URL Telegrama nie potrafi wkleić
> tekstu". Zwykły nie potrafi, ale `t.me/share/url` **tak**. Interfejs dostał też
> `link` osobno, bo `share/url` wymaga go jako parametru `url` — wyciąganie go z
> treści byłoby zgadywaniem. Z tego samego powodu wiadomość (`contacts_invite_text`)
> **nie zawiera linku**: każdy kanał składa ją sam.

> **Poprawka do §5.3 (2026-09-05, przy 5.3):** plan zakładał „nagranie → upload
> `m4a` → transkrypcja STT na nadawcy". To NIE działa na urządzeniu: systemowy
> `SpeechRecognizer` rozpoznaje mowę **na żywo** i nie przyjmuje nagranego pliku,
> a mikrofonu nie da się dzielić między `SpeechRecognizer` a `MediaRecorder`.
> Dlatego głosówka to **transkrypcja na żywo** (`transcript` w dokumencie,
> tłumaczona u odbiorcy) — bez uploadu `m4a`. Odtwarzanie oryginalnego audio to
> osobny temat: wymaga serwerowego STT (funkcja Cloud) lub innej biblioteki
> on-device, i ląduje w 5.4. `SpeechManager` już miał `startListening` — plan
> miał rację, że istnieje, ale źle założył, że bierze plik.

**Uczciwość UX:** wątek zewnętrzny jest wyraźnie oznaczony jako jednokierunkowy —
„Wysyłka ręczna. Odpowiedzi nie wracają do Verbigem". Po handoff nie wiemy, czy
użytkownik faktycznie wysłał, więc rekord zapisujemy jako `handed_off`.

---

## 6. Fazy

### Faza 0 — Ratunek fundamentów

Bez tego żadna dalsza praca nie ma sensu. Mała, możliwa do zrobienia w jednej sesji.

- [x] **0.1** `AuthRepository.ensureProfile()` + `updateProfile()` dopisują
      `usersPublic/{uid}` (uid, nickname, photoURL, uiLang, speakLangSource,
      speakLangTarget, searchNick, searchEmail). Nowy model `PublicProfile`.
      `ensureProfile` działa też jako samoleczący backfill przy każdym logowaniu.
- [x] **0.2** `backfill_faza0.js` (Node) — uzupełnia `usersPublic` i dorabia
      `members` na starych znajomościach. **Domyślnie dry-run**, zapis dopiero
      z `--apply`. **Uruchomiony: 4/4 konta.**
- [x] **0.3** `ContactsViewModel.searchUsers()` pyta `usersPublic`, nie `users`.
      Wyszukiwanie po `searchNick` i `searchEmail` (dwa zapytania, scalone).
- [x] **0.4** Model `Friendship`: `members: List<String>` (posortowane), `uidA/uidB`
      zachowane. Skrypt migracyjny w `backfill_faza0.js` (faza 2 skryptu).
- [x] **0.5** `ChatRepository`: jeden listener `whereArrayContains("members", uid)`;
      pochodne strumienie `watchAccepted` / `watchIncoming` / `watchOutgoing`.
- [x] **0.6** `ChatViewModel`: `myLang` z własnego profilu, `otherLang` z
      `usersPublic/{otherUid}`, `speak()` czyta w języku wyświetlanego tekstu.
- [x] **0.7** Reguły Firestore wdrożone: `usersPublic` (read dla zalogowanych,
      create/update tylko właściciel), `friendships` po `members`.
- [x] **0.8** BottomNav: etykiety → `strings.xml` (klucze `nav_*` już istniały
      w 6 językach, tylko ich nie używano). OCR **usunięte** z `showBottomNav`
      — 5 ikon jest kompletnych, zostaje link z Translatora + systemowy back.
- [ ] **0.9** **Test na telefonie (Milosz):** dodać znajomego z dwóch kont,
      sprawdzić że obie strony go widzą; wyszukać po nicku i po e-mailu.
- [x] **0.10** README + commit + push.

### Faza 1 — Skrzynka odbiorcza i porządny wątek

Czat 1:1 w pełni użyteczny, **bez backendu**.

- [ ] **1.1** `ChatMessage` v2 (`sourceLang`, `text`, `senderTranslation`, `type`,
      `clientMsgId`, `readBy`). `ChatRepository.sendMessage` przepisany.
- [ ] **1.2** Dokument `chats/{chatId}`: `members`, `lastMessage*`, `readState`, `typing`.
- [ ] **1.3** Nowa trasa `chat/{uid}` w `Screen.kt` + `AppNavigation`. Ekran `chat`
      = lista konwersacji, `chat/{uid}` = wątek.
- [ ] **1.4** `ChatListViewModel.watchChats(uid)` + ekran skrzynki: avatar, nick,
      podgląd ostatniej wiadomości, godzina, licznik nieprzeczytanych.
- [ ] **1.5** **Tłumaczenie u odbiorcy (D1):** `ChatTranslationCache` w Room
      (`chat_translations`). Przy pierwszym wyświetleniu wiadomości → Hy-MT2
      `translateSegmented`, spinner, zapis do cache. Brak modelu → `senderTranslation`.
- [ ] **1.6** Przełącznik „pokaż oryginał" na każdej bańce + możliwość
      przetłumaczenia na inny język po fakcie (czyści wpis z cache).
- [ ] **1.7** Menu po długim naciśnięciu: kopiuj, czytaj (TTS offline),
      czytaj Pro 💎 (`ProFeatureButton` już istnieje), pokaż oryginał, cytuj,
      usuń u mnie (lokalnie w Room + flaga `deletedFor`).
- [ ] **1.8** **Kolejka offline:** `chat_outbox` w Room. `ConnectivityObserver`
      (już istnieje) wyzwala wysyłkę. `clientMsgId` jako id dokumentu → idempotencja.
- [ ] **1.9** Paginacja: najpierw ostatnie 50, starsze dokładane przy scrollu
      (`endBefore`). Realtime listener tylko na najnowszą stronę.
- [ ] **1.10** Potwierdzenia odczytu: reguły pozwalają odbiorcy zmienić **tylko**
      własny klucz w `readBy` i `readState`. Wskaźniki ✓ / ✓✓.
- [ ] **1.11** Wskaźnik „pisze…" + obecność (Firestore z throttlem 5 s; RTDB jest
      tańszy do presence — do rozważenia, jeśli koszty urosną).
- [x] **1.12** Wyszukiwanie w wiadomościach — pole `searchText` zapisywane przez
      Cloud Function, zapytanie prefiksowe per czat. **Zostaje:** test 1.19.
- [x] **1.13** Karta kontaktu (`users/{uid}/contacts/{otherUid}`): alias,
      język tłumaczenia (ręczne nadpisanie profilu), wycisz, zablokuj, przypnij,
      notatka, usuń rozmowę (ukrycie lokalne). Ekran `contact/{uid}`,
      wejście z nagłówka wątku i z Kontaktów. **Zostaje:** auto-wykrywanie
      języka z rozmowy (oryginalny pomysł) — wymagałoby licznika języków
      per wątek i progu pewności; ręczny wybór daje użytkownikowi kontrolę
      i jest przewidywalny. Do przemyślenia w fazie 2.
- [ ] **1.16** Test na telefonie (karta kontaktu): alias widoczny w skrzynce
      i w nagłówku, przypięcie sortuje, blokada ukrywa, zmiana języka
      przetłumaczenia w locie.
- [ ] **1.14** Wszystkie stringi × 6 języków. Build + test na telefonie.
- [ ] **1.15** README + commit + push.

### Faza 2 — Backend: Cloud Functions

Warunek: plan Blaze.

- [ ] **2.1** `firebase init functions` (Node 20, TS). Dokumentacja deployu w README.
- [ ] **2.2** Secret Manager: pepper do HMAC. App Check (Play Integrity) na callable.
- [ ] **2.3** `matchContacts` — HMAC + `whereIn` po 30, rate limiting.
- [ ] **2.4** `inviteByPhone` + `onPhoneVerified` (rozwiązywanie zaproszeń).
- [ ] **2.5** **FCM:** rejestracja tokenów, `onMessageCreated` → push do odbiorcy.
      Kanał powiadomień, grupy, akcje (odpowiedz, oznacz jako przeczytane).
- [ ] **2.6** **Weryfikacja numeru (D3):** gate przy pierwszym wejściu w Czat lub
      Kontakty. Firebase Phone Auth → `linkWithCredential` na istniejącym koncie.
      „Pomiń" zostawia czat działający, ale użytkownik nie jest odnajdywalny.
- [ ] **2.7** Reguły: `phoneDirectory` i `invites` bez dostępu klienta,
      `phoneVerified`/`phoneHash` tylko do odczytu z klienta.

### Faza 3 — Kontakty 2.0

- [x] **3.0** **Warunek wstępny: polityka prywatności opublikowana** (§12) —
      zrobione 2026-09-03.
- [x] **3.1** `READ_CONTACTS` + ekran wyjaśnienia — **zrobione**.
      `ContactsPermissionScreen.kt` (prominent disclosure + link do polityki +
      e-mail `privacy@verbigem.com`) pokazywany **przed** systemowym dialogiem,
      uprawnienie dopisane do manifestu, `PhoneContactsImporter.hasPermission()`
      pilnuje, żeby nie prosić drugi raz.
- [x] **3.2** `PhoneContactsImporter` (ContactsContract) — **wersja podstawowa**:
      imię + numer, deduplikacja po numerze, odczyt na `Dispatchers.IO`.
      **Zostaje do dorobienia:** e-maile, miniatura, `starred`, zapis do tabeli
      Room `external_contacts` i normalizacja E.164 (libphonenumber, task 3.3).
- [x] **3.3** libphonenumber → normalizacja E.164 — **zrobione przy okazji 2.6**
      (`PhoneNumbers.kt`, systemowy libphonenumber przez
      `PhoneNumberUtils.formatNumberToE164`, bez nowej zależności).
      **Zostaje luka 3.3b:** ekspat z zagraniczną kartą SIM (zgadujemy kraj z SIM,
      potem z locale). Najlepszy sygnał — kraj własnego zweryfikowanego numeru —
      wymaga dopisania go w `verifyPhone`.
- [x] **3.4** Matching przez `matchContacts` → trzy stany kontaktu z §4.1
      — **zrobione**: ma Verbigem → „Napisz", nie ma → zaproszenie + kanał.
      Stan „nie ma, ale jest na WhatsApp" jest nierozróżnialny bez API
      WhatsAppa, więc WhatsApp jest kanałem, nie stanem.
- [x] **3.5** **Warstwa kanałów wychodzących** — `data/OutboundChannel.kt`:
      WhatsApp (`wa.me`), SMS (`smsto:`), e-mail (`mailto:`), Telegram, systemowy
      arkusz. Wybór kanału w dialogu po kliknięciu „Zaproś". Importer czyta
      e-maile (osobne zapytanie po `CONTACT_ID`). **Patrz blok w sekcji 1.**
- [x] **3.6** **Wątek jednokierunkowy (3.6)** — `external_contacts` + `external_outbox`
      w Room (migracja `7_8`, `version = 8`); `ExternalThreadRepository` (remember /
      setLang / recordHandOff / forget / prune / targetFor); `ExternalThreadViewModel`
      (tłumacz → przekaż, po polsku ręczny wybór języka); `ExternalThreadScreen`
      (transparent o braku strony przychodzącej, historia, kompozytor z przyciskami
      kanałów). Wejście z książki: `ContactsViewModel.rememberExternal` (suspend,
      przed nawigacją) + wiersz klikiem otwiera wątek. **WYDANE w v30.**
- [x] **3.7** **Import `.vcf` (3.7)** — `data/VcfImporter.kt`: własny parser, zero
      zależności (brak `ez-vcard`/`vcard`). Wyciąga `FN`/`N`/`TEL`/`EMAIL`, wiele
      kart, `VERSION:2.1`+`3.0`, zwijanie długich linii. Plik przez `OpenDocument`
      (mime `text/vcard`) — nie wymaga `READ_CONTACTS`. Wynik scalony z książką w
      jedną listę; numery już obecne pomijane. **WYDANE w v31.**
- [ ] **3.7** Import `.vcf` — własny parser, zero zależności.
- [x] **3.8** **Zakładki w Kontaktach (3.8)** — `ContactsScreen` przepisany na
      `TabRow` + 4 zakładki: Znajomi / Zaproszenia / Z telefonu / Zewnętrzne
      (`FriendsTab` / `InvitesTab` / `PhoneTab` / `ExternalTab`, wspólny
      `ContactListRow`). Wyszukiwanie po wszystkich naraz: niepuste pole na górze
      ukrywa zakładki i pokazuje `CombinedSearch` (znajomi po nicku, książka+import
      po imieniu/numerze, zewnętrzni po imieniu/numerze). 7 nowych stringów × 6.
      **WYDANE w v32.**
- [x] **3.9** **„Możesz znać" (3.9)** — nowa Cloud Function `suggestFriends`
      (`functions/src/peopleMayKnow.ts`, callable, rate-limit, przejście grafu
      po stronie serwera przez Admin SDK: znajomi moich znajomych, wyklucza mnie
      / obecnych znajomych / oczekujące w obu kierunkach, rankuje po `mutualCount`,
      cap 20, brak indeksu złożonego). Klient: `PeopleMayKnowRepository` +
      `ContactsViewModel` (`suggestedFriends` StateFlow, `loadSuggestions` w init,
      `dismissSuggestion(uid)`). UI: sekcja „Możesz znać" na szczycie `FriendsTab`
      (reuse `onSendRequest` + `onDismissSuggestion`). 3 nowe stringi × 6
      (`people_you_may_know`, `people_you_may_know_mutual`, `dismiss`).
      Funkcja wdrożona PO NAZWIE. **WYDANE w v33.**
- [x] **3.10** **Stringi × 6 (audyt, weryfikacja)** — przelotowy audyt: 257
      kluczy `R.string.*` użytych w kodzie, **wszystkie obecne w 6 językach**;
      zweryfikowano też dynamiczne `stringResource(channel.labelRes)` (klucze
      `channel_*`) — kompletne. **Zero hardkodowanych literałów** w `Text(...)`
      (emoji-przedrostki `💬`/`👤` to dekoracja, nie tekst do tłumaczenia).
      Nie znaleziono brakujących ani nieprzetłumaczonych stringów, więc **brak
      nowego wydania** — v33 już niesie pełny, przetłumaczony zestaw. Build v33
      (`assembleDebug`) przeszedł czysto. **Test na telefonie został u Milosza.**
      Plan + sesja zaktualizowane, commit `d501145` (v33) już na `origin/master`.

### Faza 4 — Kody QR

- [x] **4.1** **Mój kod QR** — `ProfileLinks.forUser(uid)` w `AppLinks.kt`
      (`https://mini.verbigem.com/u/<uid>`; `usersPublic` jest publiczne, więc
      surowy uid wystarcza — bez podpisanego tokenu). Generowanie bitmapy przez
      ZXing `core` 3.5.3 (`data/QRBitmap.kt`), ekran `MyQrScreen` + `MyQrViewModel`,
      trasa `Screen.MyQr`, przycisk w `ProfileScreen`. 10 nowych stringów × 6
      (`qr_my_code`, `qr_my_code_hint`, `qr_scan`, `qr_scan_hint`, `qr_scan_failed`,
      `qr_scan_no_camera`, `qr_not_verbigem`, `qr_copy_link`, `qr_generate_failed`,
      `qr_scan_retry`).
- [x] **4.2** **Skaner** — `ScanScreen` na GMS Code Scanner
      (`play-services-code-scanner` 18.3.0): sam prosi o kamerę, zwraca `Barcode`;
      `ProfileLinks.uidFromUrl` wyciąga uid i otwiera `ContactCard`. Obcy link →
      komunikat „to nie kod Verbigem" (nie otwieramy obcych stron). Trasa
      `Screen.Scan`, ikona skanera w nagłówku `ContactsScreen`.
- [x] **4.3** **App Links** — `intent-filter` VIEW z `autoVerify=true` na
      `mini.verbigem.com/u/<uid>` w `AndroidManifest.xml`; `assetlinks.json`
      (SHA256 debug) w `mini/dist/.well-known/`; obsługa deep linku w `MainActivity`
      (`handleDeepLink` → `deepLinkUid` flow) + `AppNavigation` (`openProfileUid`
      → `ContactCard`, pomija własny uid i niezalogowanego). Hosting `mini`
      wdrożony (`firebase deploy --only hosting`) — plik statyczny wygrywa z
      SPA-rewrite. **WYDANE w v34.**

**Sesja: Faza 4 (2026-09-04)** — pełna Faza 4 (4.1+4.2+4.3) z surowym linkiem
`https://mini.verbigem.com/u/<uid>` (wybór użytkownika: „nie wiem jaka jest różnica"
→ wyjaśnione raw-uid vs signed-token, wybrano raw-uid zgodnie z planem).
- `libs.versions.toml`: + `zxing = "3.5.3"`, `codeScanner = "18.3.0"`; biblioteki
  `zxing-core`, `play-services-code-scanner`. `app/build.gradle.kts`: + 2 impl.
- `AppLinks.kt`: obiekt `ProfileLinks` (`forUser`, `uidFromUrl` — jedyny parser
  linków Verbigem, używany przez skaner i App Links).
- `QRBitmap.kt` (nowy): ZXing → `Bitmap`.
- `MyQrViewModel` + `MyQrScreen` (nowe): kod + nickname + „Kopiuj link".
- `ScanScreen` (nowy): GMS Code Scanner + stany (skanowanie / brak kamery /
  błąd / nie-Verbigem) z przyciskiem „Skanuj ponownie".
- `Screen.kt`: + `MyQr`, `Scan`. `AppNavigation`: composable'e + wejścia
  (`onOpenMyQr` w `ProfileScreen`, `onOpenScan` w `ContactsScreen`) + efekt
  deep-linku `openProfileUid`.
- `MainActivity`: `handleDeepLink` (ACTION_VIEW → `ProfileLinks.uidFromUrl`).
- `AndroidManifest.xml`: VIEW `intent-filter` `autoVerify=true` na `/u/`.
- `mini/dist/.well-known/assetlinks.json`: SHA256 debug (autoVerify).
- Stringi ×6 (10 kluczy `qr_*`). Build v34 (`assembleDebug`, z native llama.cpp)
  przeszedł czysto. Hosting `mini` wdrożony, `assetlinks.json` na żywo.
  **Test na telefonie został u Milosza** (skanowanie GMS, App Links od 1.18).

### Faza 5 — Media

- [x] **5.1** Firebase Storage + reguły (`chat_attachments/{chatId}/{msgId}`).
- [x] **5.2** Zdjęcia: wyślij → opcjonalnie OCR na nadawcy (`OcrManager` już jest)
      → `ocrText` w dokumencie → odbiorca tłumaczy tekst swoim językiem.
- [x] **5.3** Głosówki: transkrypcja STT na żywo (`SpeechManager.startListening`)
      → `transcript` w dokumencie → odbiorca tłumaczy tekst swoim językiem.
      ⚠️ Odchylenie: brak uploadu `m4a` (patrz Poprawka do §5.3).
- [x] **5.4** Podgląd zdjęcia na pełnym ekranie + postęp ładowania (`SubcomposeAsyncImage`);
      cache offline przez Coil (domyślny dyskowy). Celowo pominięte: odtwarzanie `m4a`
      (transkrypcja na żywo z 5.3 wystarcza; serwerowe STT NIE jest potrzebne — patrz
      Poprawka do §5.3) oraz retry błędów uploadu (TODO w `sendImage`/`sendVoice`).

**Sesja: Faza 5 (2026-09-04)** — start. Zrobiono 5.1 (fundament): dodano
`firebase-storage` (BOM 33.2.0, bez `version.ref`) do `libs.versions.toml` +
`app/build.gradle.kts`, utworzono `storage.rules` (tylko członkowie czatu,
create-only, `image/*`+`audio/*` <25 MB, członkostwo przez `firestore.get` z
`chats/{chatId}.members`) i sekcję `"storage"` w `firebase.json`. Build v34
przechodzi (40 tasków, czysto). **Bloker wdrożenia reguł:** Firebase Storage
nie jest włączone w projekcie `mini-verbigem` — `firebase deploy --only storage`
zwraca „Storage has not been set up". Wymaga kliknięcia „Get Started" w konsoli
(mus Milosz) albo `gcloud services enable firebasestorage.googleapis.com` +
utworzenia domyślnego bucketu. Po włączeniu: wdrażam reguły i przechodzę do 5.2
(zdjęcia + OCR przez istniejący `OcrManager`).

**Sesja: Faza 5.2 (2026-09-04, popołudnie/wieczór)** — 5.2 zrobione w kodzie i buduje
się czysto (`assembleDebug` OK). Zakres: `coil-compose` 2.7.0 (miniatury `AsyncImage`),
`StorageRepository.uploadAttachment` (`putFile` strumieniowo), rozszerzony `ChatMessage`
(`attachmentUrl`/`ocrText`/`transcript`) + `ChatSummary.lastMessageType`, `ChatRepository`
`.sendMessage` o `type`/`attachmentUrl`/`ocrText`/`transcript` (preview + `lastMessageType`),
`ChatThreadViewModel.sendImage` (upload → OCR nadawcy → hint translacji → send), miniatura
w `MessageBubble`, zlokalizowany placeholder w inboxie (`R.string.photo`), 4 nowe klucze
×6 języków. **Pułapka budowy:** komentarz KDoc w `StorageRepository.kt` zawierał
`image/*`/`audio/*` — `/*` otwiera zagnieżdżony komentarz blokowy, który nigdy się nie
zamyka, połykając całe ciało klasy (błąd „Unclosed comment" / „Missing '}"). Poprawione
przez przeredagowanie komentarza bez `/*`. **Decyzja:** build only, bez bumpu
`versionCode` i bez wdrożenia hostingu — release czeka na testy na telefonie Milosza.

### Faza 6 — Czaty grupowe (odłożone)

Nie wybrane przez Milosza. Zostaje jako opcjonalna: `chats` z `members` o długości
> 2, role (admin/członek), opuszczanie grupy, nazwa i avatar grupy, koszty
tłumaczenia rosną liniowo (każdy odbiorca tłumaczy na swój język —
akurat tu model „u odbiorcy" błyszczy).

---

## 7. Wpływ na reguły Firestore

Każda zmiana wymaga `firebase deploy --only firestore:rules --project mini-verbigem`.

| Kolekcja | Zmiana | Faza |
|---|---|---|
| `usersPublic/{uid}` | read dla zalogowanych; **create/update tylko właściciel**, blokada zapisu `discoverableByPhone` (pole funkcji) | 0 ✅ wdrożone |
| `friendships/{id}` | zapytania po `members`; blokada zmiany `members`/`uidA`/`uidB` przy update | 0 ✅ wdrożone |
| `chats/{chatId}/messages` | **złagodzić `update: if false`** — odbiorca może zmienić tylko własny klucz w `readBy` | 1 |
| `chats/{chatId}` | update tylko dla członków, tylko `lastMessage*`/`readState.<uid>`/`typing.<uid>` | 1 |
| `users/{uid}/contacts/{otherUid}` | read/write tylko właściciel + whitelist 7 pól (`alias`, `langOverride`, `muted`, `pinned`, `blocked`, `note`, `updatedAt`) | 1 ✅ wdrożone |
| `phoneDirectory`, `invites` | **brak dostępu klienta** (tylko funkcje) | 2 |
| `users/{uid}` | `phoneVerified`/`phoneHash` nie do zapisu z klienta (chronione przez `hasAny`) | 2 |
| `chat_attachments/**` | Storage: tylko członkowie czatu | 5 |

---

## 8. Zależności do dodania

**Projekt buduje się offline z lokalnego cache Gradle** (patrz README — dlatego
kiedyś wyleciało Paging 3). Przed dodaniem każdej zależności: **sprawdzić, czy
się pobiera**, ewentualnie zbudować raz online, żeby zapełnić cache.

| Zależność | Po co | Faza | Ryzyko |
|---|---|---|---|
| `com.googlecode.libphonenumber:libphonenumber` | normalizacja E.164 | 3 | niskie — popularna, stabilna |
| `com.google.firebase:firebase-functions-ktx` | wywołania callable | 2 | niskie — w BOM 33.2.0 |
| `com.google.firebase:firebase-messaging` | tokeny FCM | 2 | niskie |
| `com.google.firebase:firebase-storage` | zdjęcia, głosówki | 5 | niskie |
| `com.google.firebase:firebase-auth` (Phone) | weryfikacja numeru | 2 | **już jest** w BOM, telefonówka wymaga Blaze |
| `com.google.zxing:core` | generowanie QR | 4 | średnie — nowe źródło artefaktów |
| `com.google.android.gms:play-services-code-scanner` | skaner QR | 4 | średnie — wymaga GMS |

---

## 9. Ryzyka i pułapki

| Ryzyko | Szczegół | Mitygacja |
|---|---|---|
| ~~**Plan Blaze**~~ | ~~Cloud Functions nie działają na Sparku~~ | **ROZWIĄZANE — Milosz ma Blaze** (2026-09-03). Pozostaje pilnować limitów i App Check. |
| **Polityka Google Play — READ_CONTACTS** | Wymaga prominent disclosure w aplikacji + opublikowanej polityki prywatności. Brak = odrzucenie wniosku | **§12 — politykę generujemy sami**, 6 języków, hosting na `mini.verbigem.com/privacy/`. Ekran wyjaśnienia przed dialogiem uprawnień |
| **Rainbow table na hashe numerów** | Jeśli pepper wycieknie, można odtworzyć mapowanie numer→uid | App Check, rate limiting, pepper w Secret Managerze, brak odczytu klienta |
| **Koszt tłumaczenia u odbiorcy** | Model 1.8B, ~3–4 tok/s. Długa wiadomość = kilka sekund | Cache w Room, spinner, `senderTranslation` jako natychmiastowy fallback |
| **Rozmiar modelu** | 440 MB (Fast) / 1.1 GB (Accurate). Odbiorca bez modelu nie przetłumaczy | Właśnie dlatego wysyłamy `senderTranslation` — wątek działa od razu |
| **Firestore `in` ograniczone do 30** | `matchContacts` z 500 kontaktami | Chunkowanie po 30 w funkcji |
| **Koszty odczytu w czacie** | Realtime listener na każdym otwartym wątku | Listener tylko na najnowszą stronę, paginacja starszych |
| **Migracja `usersPublic`** | Istniejący użytkownicy nie mają dokumentu → wyszukiwanie ich nie znajdzie | Skrypt jednorazowy w zadaniu 0.2 |
| **Nadpisywanie nazwy APK** (projektowa pułapka) | CDN trzyma `/android/**` jako immutable przez rok | Każde wydanie = nowy `versionCode` = nowa nazwa pliku. Patrz README |

---

## 10. Konwencje projektowe — przypomnienie

Obowiązują przy każdej fazie (z `README.md` i notatek projektu):

1. **README.md aktualizujemy w tym samym turnie, co zmianę kodu.** Szczególnie
   sekcje: „Kluczowe funkcje" (3. Czat zdalny, 4. Kontakty), „Architektura
   techniczna", „Firebase — konfiguracja projektu", „Synchronizacja danych".
2. **Zakaz hardkodowania tekstów UI.** Każdy string → `values/strings.xml`
   + `values-pl`, `-de`, `-es`, `-zh`, `-tr`. Nawet tymczasowy angielski fallback.
3. **Po każdej zmianie większej niż kosmetyczna: commit + `git push`** na
   `origin/master`. Projekt `mini` (webapp) nie ma repozytorium.
4. **Wydanie = nowy `versionCode`** (+1) i `versionName` = `1.0.(code-1)`, nowa
   nazwa APK, wpis w `vite.config.ts` i `dist/updates/version.json`,
   `firebase deploy --only hosting`.
5. **Bezpieczeństwo plików osobistych:** żadnego `rm -rf`, żadnych operacji na
   katalogach domowych bez wyraźnego potwierdzenia.
6. **Zachowanie gestów i UX weryfikuje się tylko na telefonie.** Build to nie to
   samo co działający interfejs (dotyczy zwłaszcza klawiatury, scrollowania i
   długiego naciśnięcia).

---

## 11. Pytania otwarte

Do rozstrzygnięcia w trakcie — nie blokują startu fazy 0.

1. ~~**URL polityki prywatności**~~ — **ROZSTRZYGNIĘTE (2026-09-03):** nie istniał,
   wygenerujemy go sami. Zob. §12.
2. **Czy import kontaktów ma być jednorazowy, czy ciągły?** (opcjonalny okresowy
   re-match w tle, np. raz na tydzień, żeby wykryć znajomych którzy dołączyli).
3. **Czy kontakty zewnętrzne synchronizować z chmurą?** Na razie zakładam, że
   zostają lokalne (prywatność) — ale wtedy znikają po zmianie telefonu.
4. **Kto płaci za tłumaczenie w chmurze** w fallbacku dla użytkowników bez modelu?
   Czy `senderTranslation` jest darmowe (nadawca używa swojego modelu), czy Pro?
5. **Retencja wiadomości** — czy kasować stare wiadomości z Firestore po X dniach
   (koszty vs. historia)?

---

## 12. Polityka prywatności — OPUBLIKOWANA (2026-09-03)

**URL:** `https://mini.verbigem.com/privacy/` (+ `/pl/`, `/en/`, `/de/`, `/es/`,
`/zh/`, `/tr/`). **Kontakt: `privacy@verbigem.com`.**

Wymagana przez Google Play (zwłaszcza przy `READ_CONTACTS` — bez niej wniosek o
dostęp do kontaktów zostanie odrzucony). **Zrobiliśmy ją sami** — treść jest
prosta, bo aplikacja jest zaprojektowana prywatnie: tłumaczenie dzieje się na
urządzeniu. Faza 3 jest odblokowana.

**Struktura treści (13 sekcji, identyczna w każdym języku):** kto odpowiada za
dane → dane tylko na urządzeniu → dane w chmurze → wiadomości czatu → numery
telefonów i kontakty → uprawnienia → podmioty trzecie (tabela: Firebase /
OpenRouter / DeepSeek / Hugging Face) → retencja → prawa użytkownika →
bezpieczeństwo → dzieci (13+/16+ w EOG) → zmiany polityki → kontakt. Na górze
jest ramka „Najważniejsze w skrócie" z 5 punktami, w tym adresem e-mail.

### 12.1 Zakres treści (do napisania)

- **Dane na urządzeniu, nie u nas:** tłumaczenia (model Hy-MT2), historia
  tłumaczeń i OCR, wyniki rozpoznawania mowy — przetwarzane lokalnie.
- **Dane w chmurze (Firebase/Google):** konto (nick, e-mail), treść wiadomości
  czatu (musi być przechowana, żeby dostarczyć ją odbiorcy), historia
  synchronizowana między urządzeniami, tokeny powiadomień.
- **Numery telefonów:** do chmury trafia **wyłącznie skrót kryptograficzny**
  (SHA-256 + HMAC z kluczem serwerowym), nigdy numer w postaci jawnej. Numery
  kontaktów nie są przechowywane na serwerze.
- **Podmioty trzecie:** Google Firebase (Auth, Firestore, Storage, FCM),
  OpenRouter (TTS Pro — tylko dla płacących, tylko tekst do syntezy),
  DeepSeek (silnik online — tylko gdy użytkownik go wybierze).
- **Prawa użytkownika:** usunięcie konta i danych, kontakt (adres e-mail
  do ustalenia — patrz pytanie niżej).
- **Dzieci / wiek:** standardowa klauzula 13+/16+.

### 12.2 Jak to jest wdrożone

- **Generator:** `verbigem/mini/scripts/build_privacy.py` — jeden plik z treścią
  w 6 językach i arkuszem stylów, zero zależności, zero bundlera. Wypluwa
  statyczne HTML do `public/privacy/` **i** `dist/privacy/`:
  ```bash
  cd verbigem/mini && python scripts/build_privacy.py
  cd verbigem/mini && firebase deploy --only hosting --project mini-verbigem
  ```
  Zmiana treści = edycja skryptu, wygenerowanie, deploy. **Bez wydania APK.**
- **Format:** 6 katalogów `/privacy/<lang>/index.html` + `/privacy/index.html`
  (pełna treść EN + przełącznik języków + skrypt przekierowujący wg
  `navigator.languages`, raz na sesję). Bez JS strona i tak pokazuje politykę —
  ważne dla crawlera Google Play.
- **Dlaczego katalogi, nie pliki `pl.html`:** `firebase.json` ma catch-all
  rewrite `** → /index.html` (SPA webappy). Pliki statyczne mają pierwszeństwo,
  ale tylko przy dokładnym trafieniu — `/privacy/pl/` działa zawsze,
  `/privacy/pl` dostaje 301 na wersję ze slashem (sprawdzone).
- **⚠️ Pułapka hostingu (znana z README):** `firebase deploy --only hosting`
  **zastępuje cały hosting zawartością `dist/`**. Dlatego pliki są w obu
  miejscach: `dist/` (leci na serwer teraz) i `public/` (przetrwa przyszły
  `npm run build`, bo Vite kopiuje `public/` → `dist/`).
- **⚠️ Pułapka cache:** w `firebase.json` dodano `/privacy/**` →
  `public, max-age=3600, must-revalidate`. **Nie wolno** dać tam `immutable` —
  Cloudflare zamroziłby politykę na rok (ta sama pułapka co `/android/**`).
  Z tego powodu **styl jest inlinowany** do każdego pliku, zamiast leżeć w
  osobnym `.css` (reguła `**/*.css` ma `immutable`).
- **Link w aplikacji:** karta „Polityka prywatności" w `ProfileScreen`
  (otwiera `/privacy/<uiLang>/`) oraz ekran `ContactsPermissionScreen`
  przed dialogiem `READ_CONTACTS`. URL-e buduje `data/AppLinks.kt` — jedno
  źródło prawdy. Stringi × 6 (`privacy_*`, `contacts_perm_*`).
- **Kontakt:** `privacy@verbigem.com` — ten sam adres w polityce, w ekranie
  disclosure i w zgłoszeniu do Play.

### 12.3 Zadania

- [x] **12.1** Adres kontaktowy: **`privacy@verbigem.com`** (ustalił Milosz,
      2026-09-03).
- [x] **12.2** Treść polityki — 13 sekcji + ramka „w skrócie", PL i EN jako
      wzorce.
- [x] **12.3** Tłumaczenia DE / ES / ZH / TR.
- [x] **12.4** 6 plików HTML + `index.html`, wspólny styl inlinowany,
      jasny/ciemny motyw pod `prefers-color-scheme`.
- [x] **12.5** Wrzucone do `mini/dist/privacy/` **i** `mini/public/privacy/`,
      wdrożone, wszystkie 7 URLi sprawdzone (HTTP 200, poprawne tytuły).
      Deploy zgłosił `uploading new files [0/7]` — webapp nietknięta.
- [x] **12.6** Pozycja „Polityka prywatności" w `ProfileScreen` + stringi × 6.
- [x] **12.7** README: sekcja „🔒 Polityka prywatności (opublikowana)" +
      pułapki `dist/` vs `public/` i cache.

**Kiedy:** zrobione 2026-09-03 jako osobny, lekki tor. Faza 3 odblokowana.
