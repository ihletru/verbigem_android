> 📄 Fragment dokumentacji Verbigem Android. Indeks: [README.md](../README.md)

# ☁️ Cloud Functions (`functions/`)

Backend czatu. **Wymaga planu Blaze** (wykupiony). Kod w `functions/src/`, kompilacja do `functions/lib/` (gitignored). **Runtime: `nodejs22`** (`firebase.json` → `runtime`, `engines.node` w `functions/package.json`).

| Funkcja | Typ | Co robi |
|---|---|---|
| `onMessageCreated` | trigger `chats/{chatId}/messages/{msgId}` | push FCM do pozostałych członków czatu |
| `matchContacts` | callable | dopasowanie kontaktów po HMAC numeru telefonu |
| `inviteByPhone` | callable | zapraszanie numerów bez konta |
| `verifyPhone` | callable | zapis faktu „to konto ma zweryfikowany numer" |
| `onPhoneVerified` | trigger `users/{uid}` | uzgadnia `phoneDirectory`, rozwiązuje zaproszenia |
| `suggestFriends` | callable | „Możesz znać" — znajomi moich znajomych (3.9) |

## Jak deployować

```bash
cd functions
npm install         # raz, po klonie
npm run build       # tsc -> lib/  (deploy i tak to robi przez predeploy w firebase.json)

# z katalogu głównego projektu:
firebase deploy --only firestore:rules --project mini-verbigem   # po zmianie reguł
firebase functions:log --project mini-verbigem                   # logi
```

`firebase.json` ma `predeploy: ["npm --prefix \"$RESOURCE_DIR\" run build"]`, więc deploy sam kompiluje — `npm run build` ręcznie słuzy tylko złapaniu błędu typów bez czekania na upload.

⚠️ **Deploy katalogu `functions/`: lokalny serwer discovery ładuje `lib/index.js` i ma domyślnie 10 s.** Na tej maszynie zimny start Node + `firebase-admin` to za mało → `User code failed to load. Cannot determine backend specification. Timeout after 10000`.

```bash
export FUNCTIONS_DISCOVERY_TIMEOUT=60
```

## ☠️ NIGDY nie deployuj funkcji bez wylistowania nazw

Projekt `mini-verbigem` jest **współdzielony z webappem `verbigem/mini`**, który ma własny katalog `functions/` i sześć działających funkcji produkcyjnych.
⚠️ **Od 2026-09-08 wszystkie funkcje webminiego są w `us-central1`** (zgodnie z regionem projektu Firebase). Wcześniej były w `europe-west1`, co psuło APK: `FirebaseFunctions.getInstance()` bez regionu woła domyślnie `us-central1`, więc `createCheckout` (doładowanie portfela) w ogóle nie istniał z punktu widzenia aplikacji.

| Funkcja | Region | Po co |
|---|---|---|
| `deepseekProxy` | us-central1 | tłumaczenie online — OpenRouter, przekazuje wybrany model 1:1 + rozlicza z `usage.cost` |
| `paddleWebhook` | us-central1 | płatności — webhook Paddle |
| `portalSession` | us-central1 | portal klienta Paddle |
| `visionProxy` | us-central1 | OCR online |
| `walletTopUp` | us-central1 | doładowanie portfela |
| `createCheckout` | us-central1 | otwiera checkout Paddle (APK: doładowanie portfela); od v1.0.66 z `checkout.url = /checkout` |

Oba projekty mają `codebase: default`, więc Firebase widzi **jeden** zbiór funkcji. `firebase deploy --only functions` uruchomiony stąd uznaje tamte pięć za osierocone i chce je **usunąć**. W trybie nieinteraktywnym na szczęście się wykłada (`Aborting because deletion cannot proceed in non-interactive mode`) — z `--force` po prostu by je skasowało: **płatności, OCR i portfel przestałyby działać.**

Zawsze podawaj nazwy:

```bash
firebase deploy --only functions:onMessageCreated,functions:matchContacts,\
functions:inviteByPhone,functions:verifyPhone,functions:onPhoneVerified,\
functions:suggestFriends \
  --project mini-verbigem
```

## Kasowanie funkcji — `functions:delete`, nie deploy

`firebase deploy --only functions:<nazwa>` **nie usunie** funkcji, której nie ma już
w źródłach — zostanie w projekcie i dalej będzie działać. Do usuwania jest
osobne polecenie:

```bash
firebase functions:delete onMessageSearchIndex --project mini-verbigem --force
```

⚠️ To polecenie wypisze `Error: The specified filters do not match any existing
functions in project mini-verbigem`, jeśli trafi na **drugi przebieg** — powłoka
w tym projekcie wykonuje polecenia dwukrotnie, a pierwszy przebieg już skasował
funkcję. **Ten błąd nie znaczy, że się nie udało** — potwierdź przez
`firebase functions:list`.

⚠️ Po **każdym** deployu funkcji zrób `firebase functions:list` i policz funkcje
(2026-09-11: 14). To jedyny sposób, żeby zauważyć, że CLI uznało funkcje webappki
za osierocone.

⚠️ **Zweryfikowane 2026-09-11:** `firebase deploy --only functions:onMessageCreated`
**nie skasowało** żadnej funkcji webappki — po deployu `firebase functions:list`
pokazał wszystkie 14. Kasowanie pojedynczej funkcji robi wyłącznie
`functions:delete`. Etykiety wdrożonych funkcji **nie zawierają**
`firebase-functions-codebase` (sprawdzone REST-em), więc nie wiadomo dokładnie,
po czym CLI rozpoznaje przynależność — dlatego zasada „zawsze podawaj nazwy"
zostaje.

Dopiero nadanie obu projektom różnych `codebase` w `firebase.json` (np. `android` i `mini`) trwale rozwiązałoby problem — wymagałoby przewalczenia już wdrożonych funkcji, więc na razie zostawiamy jak jest i uważamy.

⚠️ **Pierwszy deploy funkcji 2. gen na projekcie ZAWSZE sypie błędem Eventarc:** `Permission denied while using the Eventarc Service Agent`. Uprawnienia Service Agent propagują się z opóźnieniem — **powtórzyć deploy po kilku minutach**, nie szukać błędu w kodzie.

⚠️ **Po udanym deployu CLI żąda polityki czyszczenia obrazów** (kontenery w Artifact Registry rosną i kosztują):

```bash
firebase functions:artifacts:setpolicy --location us-central1 --days 7 --force \
  --project mini-verbigem
```

## Sekrety (Secret Manager)

`matchContacts` potrzebuje pieprzu do HMAC numerów telefonów:

```bash
printf '%s' "$(openssl rand -hex 32)" | \
  firebase functions:secrets:set PHONE_HASH_PEPPER --project mini-verbigem
```

⚠️ **Rotacja pieprzu unieważnia wszystkie dopasowania** — stare hashe w `phoneDirectory` przestaną się zgadzać; zmiana wymaga przeliczenia katalogu, nie tylko podmiany sekretu. Funkcja nie wdroży się bez ustawionego sekretu (deploy pyta o to automatycznie).

## Weryfikacja numeru telefonu (E.164, Phone Auth)

**Aplikacja NIGDY nie wysyła numeru do naszego backendu.** Kolejność:

1. Firebase Phone Auth wysyła SMS i dowiązuje numer do konta (**`linkWithCredential`**, nie `signInWithCredential` — konto już istnieje).
2. Firebase wkłada zweryfikowany numer E.164 do tokena ID.
3. `getIdToken(true)` odświeża token — bez tego funkcja widziałaby token sprzed dodania numeru.
4. `verifyPhone` czyta `request.auth.token.phone_number` i zapisuje na `users/{uid}` tylko `phoneVerified` + `phoneHash` + `phoneVerifiedAt`. **Nie ma parametru do sfałszowania** — klient nie jest pytany o numer.
5. `onPhoneVerified` (trigger) dopisuje `phoneDirectory/{hmac}` i rozwiązuje zaproszenia oczekujące.

☠️ **`requireSmsValidation(true)` w `PhoneAuthOptions` NIE WOLNO używać** przy zwykłej weryfikacji numeru. Ta flaga istnieje **TYLKO** dla MFA i rzuca `IllegalArgumentException: You cannot require sms validation without setting a multi-factor session` (w v37 → crash przy każdym kliknięciu „wyślij SMS"; naprawione w v38). Instant verification: `onVerificationCompleted` → `PhoneCodeRequest.AutoVerified` → `linkWithCredential`.

☠️ **SMS nie wyjdzie, dopóki nie odblokujesz regionu (status 17006).** W nowym projekcie Firebase domyślna polityka to `allowlistOnly` z **pustą listą regionów** — SMS nie wychodzi **nigdzie**, choć dostawca „Phone" jest włączony, a odciski SHA się zgadzają. Objaw: `FirebaseAuthException` `ERROR_OPERATION_NOT_ALLOWED` (17006) z dopiskiem `[ SMS unable to be sent until this region enabled by the app developer. ]`. **Pułapka: ten sam kod dostajesz przy wyłączonym dostawcy logowania** — przez to wyglądał na problem z odciskiem SHA i kosztował cały wieczór. `PhoneVerificationViewModel.messageFor` rozróżnia te dwa przypadki po słowie „region".

```bash
node check_sms_region.js          # drukuje smsRegionConfig
node set_sms_region.js PY,PL      # allowlistOnly.allowedRegions = [PY, PL]
```

Ręcznie: Firebase Console → **Authentication → Settings → SMS region policy**. Każdy region to realny koszt SMS-a i ryzyko SMS-pumpingu — lista powinna zostać krótka.

☠️ **Przed pierwszym testem trzeba wpisać odciski SHA certyfikatu** (Firebase Console → Project settings → aplikacja Android → **Add fingerprint**), potem pobrać nowy `google-services.json`. **Dopisz oba: SHA-1 *i* SHA-256** — Phone Auth weryfikuje przez Play Integrity i potrafi odrzucić build, dla którego widzi tylko jeden. Debug keystore (`C:\Users\milo\.android\debug.keystore`, hasło `android`):

```
SHA1:   EC:9D:EB:58:CD:F2:48:3A:7E:FE:2B:73:C2:C7:90:1B:9D:6D:3C:CC
SHA256: A4:2A:45:FF:D5:25:B9:02:8C:12:2B:BE:8A:92:FE:D9:F0:3D:A7:5C:9F:D1:CA:4D:90:98:8D:CA:AD:EC:CA:94
```

**Normalizacja numerów:** matching porównuje **skróty**, nie łańcuchy — `0981 123 456` i `+595981123456` haszują się zupełnie inaczej, więc każdy numer musi być pełnym E.164 **przed** haszowaniem. `data/PhoneNumbers.kt` używa `PhoneNumberUtils.formatNumberToE164` (libphonenumber jest w systemie — nie dokładamy zależności). Bez numeru kierunkowego kraj się zgaduje (SIM, potem locale), więc `e164Candidates` zwraca **listę** i haszujemy każdą. ⚠️ **Zmiana normalizacji po weryfikacji pierwszych numerów oznacza przeliczenie całego `phoneDirectory`** — rób ją tylko, gdy katalog jest pusty.

## App Check

Inicjowany w `VerbigemApplication`; dostawca zależy od wariantu (`src/debug` → `DebugAppCheckProviderFactory`, `src/release` → Play Integrity). `firebase-appcheck-debug` jest wyłącznie w `debugImplementation`, więc build release'owy fizycznie nie potrafi sam siebie poświadczyć.

⚠️ **`matchContacts` ma `enforceAppCheck: false` i to NIE jest przeoczenie.** APK dystrybuowany przez auto-update to build **debugowy**, a Play Integrity nie poświadcza aplikacji, których nie zainstalował Play Store. Włączenie tego dziś odrzucałoby każdego użytkownika, nie tylko nadużywających.

Debugowy token do wpisania ręcznie (Console → App Check → **Debug tokens**) pojawia się w logcat po tagiem `FirebaseAppCheck`; jest per instalacja (ponownie po reinstalacji).

📌 **TODO: włączyć enforcement App Check PO dodaniu appki do Google Play.** Gdy opublikujesz pierwszy **Play-signed release** i przełączysz dystrybucję na sklep (`onPlayStore: true` w `version.json`), zrób:
1. Console → **App Check** → zarejestruj appkę Android, provider **Play Integrity**, dodaj **SHA-256 certyfikatu podpisującego Play** (Play Console → Setup → App integrity).
2. App Check → **APIs** → włącz `enforce` na **Firebase Auth**, **Firestore** i **Storage** (osobno, usługa po usłudze).
3. `functions/src/contacts.ts` → `matchContacts` na `enforceAppCheck: true` (TODO w kodzie mówi gdzie).
4. Wycofaj debug-tokeny.
⚠️ Bez kroku 1 (Play-signed build + SHA z Play) wymuszanie odrzuci wszystkich użytkowników — **nie włączaj `enforce` na debugowej dystrybucji auto-update**.

## Powiadomienia push — decyzje, których nie wolno zmienić niechcący

1. **Wiadomość FCM jest `data-only` (bez pola `notification`).** Z polem `notification` Android sam renderuje powiadomienie w tle i **nie wywołuje `onMessageReceived`** — akcje „Odpowiedz" / „Oznacz jako przeczytane" działałyby tylko przy otwartej aplikacji. Data-only daje pełną kontrolę; ceną jest podatność na Doze, dlatego `priority: "high"` + TTL 4 tyg.
2. **Kanał `verbigem_messages`.** Identyfikator jest po obu stronach: `functions/src/messaging.ts` i `VerbigemNotifications.ensureChannel()`. Zmiana w jednym miejscu bez drugiego = ciche zniknięcie powiadomień na Androidzie 8+.
3. **Podgląd treści DOMYŚLNIE WYŁĄCZONY.** `buildBody()` zwraca „Nowa wiadomość", chyba że `app_config/notifications` ma `showMessagePreview == true`. Push wychodzi z urządzenia i przechodzi przez serwery Google — to inna historia prywatności niż „tłumaczenie dzieje się na Twoim telefonie". Bezpieczeństwo niejawne: brak dokumentu = podgląd wyłączony. Przełącznik w Firestore, da się włączyć bez nowego APK.
4. **Treść podglądu z `senderTranslation`, nie z `text`.** Podpowiedź nadawcy powstała *w języku odbiorcy* (decyzja D1) — to jedyna wersja, którą ten człowiek przeczyta. Surowy `text` jest w języku nadawcy.
5. **Wyciszenie honorowane w chmurze**, nie na urządzeniu (`muted === true` na `users/{odbiorca}/contacts/{nadawca}`) — wyciszony czat nie budzi telefonu.
6. **Token = ID dokumentu.** FCM zwraca `messaging/registration-token-not-registered` dla martwych tokenów; funkcja kasuje je po ID, bez odczytu.

Po stronie aplikacji: `VerbigemMessagingService` (odbiera), `FcmTokenManager` (`users/{uid}/fcmTokens/{token}`, rejestracja po odtworzeniu sesji, usunięcie przy wylogowaniu), `VerbigemNotifications` (kanał, grupa per czat, MessagingStyle, akcje), `NotificationActionReceiver` (odpowiedź + przeczytane pod `goAsync()`, bo robią zapis do Firestore).

Uprawnienie `POST_NOTIFICATIONS` (Android 13+) jest proszone **raz, przy pierwszym otwarciu skrzynki czatu** — nie na starcie aplikacji (użytkownik nie ma wtedy powodu chcieć powiadomień, a Android przestaje pytać po dwóch odmowach). Flaga `asked_notif_perm` w DataStore.

---
