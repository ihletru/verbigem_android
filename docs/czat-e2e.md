# Czat szyfrowany end-to-end — projekt

Dokument decyzyjny. Powstał, zanim powstał kod, bo ta zmiana dotyka jednocześnie
formatu wiadomości, Cloud Functions, reguł Firestore, bazy lokalnej i **dwóch
klientów** (Android + webapp). Pomyłka w kryptografii nie objawia się błędem —
objawia się tym, że użytkownik wierzy, że jest bezpieczny, a nie jest.

Ostatnia aktualizacja: 2026-09-11 — fazy 1–7 zrobione w webappce (wdrożone), fazy 1–7 zrobione w Androidzie (skompilowane, czeka na wersję + upload do Play).

---

## 1. Cel

Wiadomości w czacie mają być zaszyfrowane end-to-end (E2E) tak jak w Telegramie:
treść ma być nieczytelna dla naszego backendu. Serwer przechowuje i przekazuje
szyfrogram, ale nie ma klucza, który pozwoliłby go odczytać.

Decyzja Milosza (2026-09-11): **bez weryfikacji tożsamości** (bez ekranu
porównywania „numerów bezpieczeństwa" w stylu Signala). Uzasadnienie: większość
kont powstaje przez Google, gdzie weryfikacja i tak już się odbyła; WhatsApp nie
ma takiej weryfikacji, a E2E ma.

---

## 2. Stan obecny — dlaczego dziś to NIE jest E2E

To nie jest luka do „dokręcenia". Dziś serwer czyta każdą wiadomość w całości,
i to w dwóch miejscach:

| Miejsce | Co robiło z treścią | Stan po fazie 5 |
|---|---|---|
| `functions/src/searchIndex.ts` → `onMessageSearchIndex` | czytało pole `text` z `chats/{id}/messages/{msgId}` i zapisywało znormalizowaną kopię w `searchText` — czyli **budowało pełnotekstowy indeks treści** | **usunięte**: kod skasowany, funkcja skasowana w projekcie |
| `functions/src/messaging.ts` → `onMessageCreated` | czytało treść, żeby wstawić ją do powiadomienia push | **treść usunięta**: push niesie tylko „Nowa wiadomość” |

Dodatkowo `ChatRepository.sendMessage` zapisywał `text` jawnym tekstem — teraz
zapisuje szyfrogram, a jawny zostaje tylko wtedy, gdy którejś stronie brakuje klucza.

Wniosek: dopóki `searchText` istniał, E2E było fikcją — treść leżała odczytana
na serwerze w drugim dokumencie. Po fazie 5 **żadna funkcja nie czyta już treści**.

⚠️ **Konsekwencja dla wyszukiwania, która jest stanem przejściowym:** indeks
serwerowy zniknął, a wyszukiwanie lokalne dochodzi dopiero w fazie 6. Do tego
czasu wyszukiwanie znajduje wyłącznie wiadomości **już zaindeksowane** (sprzed
fazy 5). Nowe są dla wyszukiwania niewidoczne — dotyczy to także starego APK
v1.0.71, który szuka po serwerowym `searchText`.

---

## 3. Model zagrożeń

**Chronimy przed:**
* odczytaniem treści przez nasz backend (administrator, wyciek bazy, nakaz sądowy
  obejmujący tylko bazę),
* odczytaniem treści przez kogoś, kto przechwyci bazę lub ruch sieciowy,
* podsłuchaniem treści przez operatora sieci (i tak chroni to TLS, ale to druga warstwa).

**Świadomie NIE chronimy przed:**
* **podmianą klucza przez serwer** — to jest cena decyzji o braku weryfikacji
  tożsamości. Serwer, który poda nadawcy własny klucz publiczny „odbiorcy", może
  odszyfrować wiadomość. Patrz §4 — łagodzimy to, ale nie usuwamy.
* przejęciem samego urządzenia (złośliwa aplikacja z uprawnieniami roota,
  forensic image telefonu odblokowanego),
* metadanymi: kto, z kim, kiedy i ile pisał. One zostają jawne — tak samo jak
  w Telegramie i WhatsAppie.
* zrzutem ekranu i kopiowaniem tekstu przez odbiorcę.

---

## 4. Brak weryfikacji tożsamości — co to realnie znaczy

Bez porównania odcisków kluczy poza kanałem (osobiście, innym komunikatorem)
**nie da się odróżnić „klucza odbiorcy" od „klucza podstawionego przez serwer"**.
To nie jest wada implementacji, tylko matematyczna konsekwencja braku drugiego kanału.

Mitigacja, którą stosuje WhatsApp i którą przyjmujemy — **TOFU (trust on first use)**:

* przy pierwszym kontakcie zapisujemy lokalnie (Room) zestaw kluczy urządzeń rozmówcy,
* jeśli przy kolejnej wiadomości zestaw się **zmieni**, pokazujemy w wątku wyraźne
  ostrzeżenie: „Klucz bezpieczeństwa rozmówcy się zmienił" z krótkim odciskiem,
* ostrzeżenie NIE blokuje czatu — informuje.

To wykrywa podstawienie klucza po pierwszej rozmowie, ale nie przy pierwszej.
Uczciwie opisujemy to w polityce prywatności i w pomocy — bez tego teksty w UI
byłyby obietnicą bez pokrycia.

---

## 5. Kryptografia

Wszystko na prymitywach dostępnych w obu klientach bez dodatkowych bibliotek:

| Krok | Prymityw | Android | Webapp |
|---|---|---|---|
| wymiana klucza | ECDH P-256 | `KeyAgreement` (JCA) | WebCrypto `ECDH` |
| wyprowadzenie klucza | HKDF-SHA256 | `Mac` (ręczny HKDF) | WebCrypto `HKDF` |
| szyfrowanie | AES-256-GCM | `Cipher` (JCA) | WebCrypto `AES-GCM` |
| klucz z hasła | PBKDF2-HMAC-SHA256, 310 000 iteracji | `SecretKeyFactory` | WebCrypto `PBKDF2` |
| losowość | `SecureRandom` | JCA | `crypto.getRandomValues` |

### Model klucza — decyzja Milosza (2026-09-11)

**Jeden klucz tożsamości na KONTO** (nie na urządzenie), z kopią zapasową klucza
prywatnego trzymaną na serwerze **zaszyfrowaną hasłem, którego serwer nie zna**.

⚠️ **To rozróżnienie jest całym sensem tego punktu i nie wolno go zgubić przy
implementacji.** „Klucz konta na serwerze" da się zrobić na dwa sposoby:

| Wariant | Czy to nadal E2E? |
|---|---|
| Serwer trzyma klucz prywatny **jawnym tekstem** | **NIE.** Ktokolwiek z dostępem do bazy czyta wszystko. Punkt 4 przestaje istnieć. |
| Serwer trzyma klucz **zaszyfrowany hasłem użytkownika** (`AES-GCM(PBKDF2(hasło, sól), klucz)`) | **TAK.** Bez hasła mamy losowy ciąg bajtów. |

Implementujemy **wariant drugi**. Wariant pierwszy nie zostałby nawet zapisany
w kodzie, bo dawałby użytkownikowi złudzenie bezpieczeństwa.

Konsekwencje, które trzeba powiedzieć użytkownikowi wprost:
* **Zapomniane hasło = utracona historia czatu.** Nie ma „przypomnij hasło" ani
  „zresetuj" — reset oznacza nowy klucz i nową tożsamość, a stare wiadomości
  zostają nieczytelne na zawsze. Serwer nie może pomóc, bo nie ma czym.
* Hasło jest **osobne od logowania** (logowanie idzie przez Google — nie mamy
  jego hasła i nie chcemy).
* Konto bez ustawionego hasła działa jak dziś (jawny tekst), dopóki użytkownik go
  nie ustawi. To jest cała migracja istniejących kont.

Ustawienie hasła: losowa **fraza odzyskiwania** (np. 6 słów) generowana na
urządzeniu i pokazana raz do zapisania — łatwiejsza do przepisania na nowy telefon
niż wymyślone hasło i odporna na zapomnienie w większym stopniu. Użytkownik może
też wpisać własne hasło, jeśli woli.

### Gdzie leżą klucze

| Co | Gdzie | Kto czyta | Kto pisze |
|---|---|---|---|
| klucz publiczny | `usersPublic/{uid}.chatKey = {v, pub, updatedAt}` | każdy zalogowany | tylko właściciel (reguły już to wymuszają) |
| klucz prywatny (roboczy) | lokalnie: PKCS#8 zaszyfrowany kluczem AES z Android Keystore, per konto | tylko to urządzenie | tylko to urządzenie |
| kopia klucza prywatnego | `users/{uid}/chatKeyBackup/main` — `AES-GCM(PBKDF2(hasło, sól), PKCS#8)` | tylko właściciel (jak `users/{uid}/history`) | tylko właściciel |

Dlaczego klucz publiczny ląduje na `usersPublic`, a nie w osobnej kolekcji: i tak
czytamy ten dokument przy każdej rozmowie (nazwa, avatar), więc nie dokładamy
ani jednego odczytu. `AuthRepository.syncPublicProfile` używa `SetOptions.merge()`
i wypisuje tylko swoje pola, więc **nie skasuje** `chatKey` — ale każda przyszła
zmiana tego rzutu musi o tym pamiętać.

⚠️ **Klucz prywatny NIE może być kluczem z Android Keystore.** Keystore nie pozwala
wyeksportować klucza, a my musimy umieć zapisać jego kopię zapasową — więc para
powstaje w oprogramowaniu (`KeyPairGenerator`), a Keystore służy tylko jako
sejf na klucz AES, którym szyfrujemy PKCS#8 w spoczynku. To jest konsekwencja
wybranego modelu (kopia zapasowa) i nie da się jej obejść.

⚠️ Klucz AES w Keystore jest **per urządzenie**, a nie per konto — dlatego
szyfrogram klucza prywatnego trzymamy w osobnym wpisie dla każdego `uid`.
Bez tego wylogowanie i zalogowanie na inne konto próbowałoby odszyfrować cudzy
klucz (dokładnie ten sam błąd, co wspólny znacznik synchronizacji przed v1.0.68).

### Koperta wiadomości

Per wiadomość, nie per para urządzeń — dzięki temu mamy **forward secrecy**
(ujawnienie później klucza tożsamości nie odsłania starych wiadomości):

```
1. Nadawca losuje klucz wiadomości MK (32 B) i parę efemeryczną (epk, esk).
2. Dla KAŻDEGO odbiorcy tej wiadomości (rozmówca + moje pozostałe urządzenia):
     S_i      = ECDH(esk, pub_i)
     wrapKey_i= HKDF-SHA256(S_i, salt=iv_i, info="verbigem-chat-v1-wrap")
     wrapped_i= AES-256-GCM(wrapKey_i, MK, iv_i)
3. Treść: ciphertext = AES-256-GCM(MK, plaintext, iv_body)
4. esk jest wyrzucany z pamięci natychmiast po kroku 2.
```

Koszt: jedno generowanie pary kluczy na wiadomość (~1 ms) i jedno ECDH na
urządzenie odbiorcy. Przy czacie 1:1 i 2–3 urządzeniach to nieistotne.

⚠️ Forward secrecy chroni **historię przed późniejszym wyciekiem klucza
tożsamości**. Nie chroni przed kimś, kto zna hasło odzyskiwania — ten odszyfruje
całą historię, bo po to jest kopia zapasowa. To jest cena wariantu wygodnego
i musi być napisana w pomocy, a nie przemilczana.

### Format dokumentu w Firestore

```jsonc
{
  "authorId": "…",
  "sourceLang": "pl",
  "clientMsgId": "…",
  "createdAt": …,          // metadana — zostaje jawna
  "enc": {
    "v": 1,
    "alg": "AES-256-GCM+HKDF-SHA256+ECDH-P256",
    "epk": "base64(65 B, punkt nieskompresowany)",
    "bodyIv": "base64(12 B)",
    "keys": {
      "<deviceId>": { "iv": "base64(12 B)", "wrap": "base64(MK)" }
    }
  },
  "text": "base64(ciphertext)",
  "senderTranslation": { "lang": "pl", "text": "base64(ciphertext)" }
}
```

**Stare wiadomości bez `enc` są czytane jak dziś** — brak pola oznacza jawny
tekst. To jest cała migracja: nic nie przepisujemy, nic nie tracimy.

---

## 6. Co przestaje działać (i co z tym robimy)

| Funkcja | Dziś | Po E2E |
|---|---|---|
| Wyszukiwanie w czacie | serwerowy indeks `searchText`, prefiksowy, po wszystkich rozmowach | **tylko lokalnie**, po wiadomościach już wczytanych do wątku (ostatnie 50 na rozmowę, doładowywane przy przewijaniu). **Zrobione w fazie 6** (Android i webapp), szuka podciągu w `text` + tłumaczenie + `hintText`, pomija `NO_KEY`/`FAILED`. ⚠️ Nowe wiadomości NIEWYSZUKIWALNE po stronie serwera (indeks zniknął w fazie 5) — to zamierzone |
| Treść powiadomienia push | fragment wiadomości | „Nowa wiadomość” (bez treści) — inaczej FCM = wyciek. **Zrobione w fazie 5** |
| Podgląd ostatniej wiadomości w skrzynce | `chats.lastMessage` jawny | `lastMessage` zostaje jako **placeholder** dla klientów bez klucza, a obok leci **`lastMessageEnc` + `lastMessageBody`** (druga, mała koperta) odszyfrowywana lokalnie. **Zrobione w fazie 5** |
| Odtworzenie historii na nowym urządzeniu | pełne | **tylko nowe wiadomości** — nowe urządzenie nie ma klucza do kopert sprzed dodania |

⚠️ Ostatni punkt jest najważniejszą konsekwencją i musi być napisany wprost
w pomocy: **nowy telefon nie odczyta starych wiadomości.** Alternatywą byłoby
kopiowanie klucza między urządzeniami (wtedy każde nowe urządzenie osłabia
bezpieczeństwo wszystkich poprzednich) albo trzymanie klucza na serwerze (wtedy
E2E nie istnieje). Nie idziemy w żadną z tych stron.

⚠️ Wyszukiwanie w `searchIndex.ts` trzeba **usunąć**, a nie „wyłączyć": dopóki
funkcja istnieje i jest wdrożona, każda nowa wiadomość (także ta zaszyfrowana —
`text` to base64, więc funkcja zapisze indeks z szyfrogramu) trafia do serwera.
Usunięcie funkcji z kodu bez `firebase deploy` nie wystarczy.

---

## 7. Plan faz

Każda faza kończy się stanem, który da się zbudować i **nie psuje czatu**.

| # | Faza | Efekt |
|---|---|---|
| 1 | `E2eCrypto` + test wektorowy | brak zmian w zachowaniu; dowód, że Android i webapp liczą to samo |
| 2 | Klucz konta: generowanie, publikacja klucza publicznego, kopia prywatnego zaszyfrowana hasłem + reguły Firestore | konto ma tożsamość, nikt jej jeszcze nie używa; konta bez hasła działają jak dziś |
| 3 | Koperta w wysyłce/odbiorze (Android) + ekran ustawienia hasła odzyskiwania | nowe wiadomości szyfrowane, stare czytane dalej |
| 4 | Koperta w webappie | parytet |
| 5 | Usunięcie `onMessageSearchIndex` + push bez treści + szyfrowany podgląd w skrzynce | serwer przestaje widzieć treść |
| 6 | Wyszukiwanie lokalne + TOFU + ostrzeżenie o zmianie klucza | domknięcie funkcji |
| 7 | Teksty: pomoc w czacie, polityka prywatności, layout | **dopiero teraz** — wcześniej byłyby nieprawdą |

Punkt 7 jest celowo na końcu. Napisanie w UI „wiadomości są szyfrowane
end-to-end", zanim szyfrowane są, to nie kosmetyka — to wprowadzanie
użytkownika w błąd.

### Stan realizacji

| Faza | Stan |
|---|---|
| 1 — format i wektory | **zrobione i zweryfikowane po obu stronach**: referencja `mini/scripts/e2e-vectors.mjs` → `mini/scripts/e2e_vectors.json` (kopia w `app/src/test/resources/`). Kotlin: `E2eCrypto` + `E2eCryptoVectorsTest` — **10 testów, 0 błędów**, odtwarza wektory bajt w bajt (ECDH, HKDF, epk, body, wrap). Uruchomienie: `node scripts/e2e-vectors.mjs` (referencja) i `:app:testStandaloneDebugUnitTest --tests "*E2eCryptoVectorsTest*"` (Kotlin) |
| 2 — klucz konta | **zrobione**: `E2eCrypto` (pary, koperta, kopia klucza), `E2eKeyStore` (lokalny sejf: PKCS#8 pod kluczem AES z Keystore, wpisy per `uid`), `ChatKeyRepository` (stan / utworzenie / odtworzenie / publikacja klucza i kopii), reguły na `users/{uid}/chatKeyBackup/{doc}`, ekran `E2eKeysScreen` + `E2eKeysViewModel` (wejście: kłódka w nagłówku skrzynki) |
| 3 — koperta w wysyłce/odbiorze (Android) | **zrobione**: `MessageCipher` (jedna koperta na cały wrażliwy payload), `ChatMessage.enc` + `EncEnvelope`, szyfrowanie w `ChatThreadViewModel.outgoing()` (tekst / zdjęcie / głosówka), odszyfrowanie w `recompute()` z cache po `msg.id`, `EncState` (PLAIN / ENCRYPTED / NO_KEY / FAILED) i kłódka w dymku. Stare wiadomości bez `enc` czytane jak dotąd. Podgląd w skrzynce dla wiadomości szyfrowanej to na razie **opis, nie treść** — deszyfrowalny podgląd dochodzi w fazie 5 |
| 4 — koperta w webappie | **zrobione**: `mini/src/chat/e2eCrypto.ts` (WebCrypto: ECDH P-256, HKDF, AES-GCM, PBKDF2, kopia klucza w tym samym układzie bajtów), `mini/src/chat/e2eKeys.ts` (tożsamość w IndexedDB + publikacja klucza i kopii), `chatService.sendMessage`/`watchMessages` (szyfrowanie i odszyfrowanie z cache po `msg.id`), `E2ePanel.tsx` + `ChatPage.tsx` (pasek stanu, panel hasła, kłódka i placeholdery w dymkach). Weryfikacja: `npm run e2e:parity` — **31 sprawdzeń, 0 błędów** |
| 5 — usunięcie `onMessageSearchIndex` + push bez treści + szyfrowany podgląd | **zrobione**: `functions/src/searchIndex.ts` i `backfill_searchtext.js` skasowane, funkcja `onMessageSearchIndex` skasowana w projekcie (`firebase functions:delete`), `messaging.ts` nie czyta już ani `text`, ani `senderTranslation` (zawsze „Nowa wiadomość”, `app_config/notifications.showMessagePreview` przestał być konsultowany), `MessageCipher.decryptPreview` + `ChatSummary.lastMessageEnc/lastMessageBody` + deszyfrowanie w `ChatListViewModel.refreshPreviews` (w tle, cache po `lastMessageAt`), webappka zapisuje te same pola. Reguły: whitelista `chats` rozszerzona o `lastMessageEnc`/`lastMessageBody`. Wdrożone: `functions:onMessageCreated` + `firestore:rules` |
| 6 — wyszukiwanie lokalne + TOFU | **zrobione (obie strony)**: webappka (patrz wyżej) + Android — `E2ePeerKeyStore` (SharedPreferences per urządzenie `verbigem_e2e_peer_keys`, `observePeerKey` → `NEW\|SAME\|CHANGED`, `fingerprint` z SHA-256 w 4 grupach zgodny z webappką), `ChatKeyRepository.watchPeerKey` (nasłuch `addSnapshotListener` na `usersPublic.{uid}.chatKey.pub`), `ChatThreadViewModel` (`searchQuery`/`visibleBubbles` — filtr podciągu po `text`+tłumaczenie+`hintText`, pomija `NO_KEY`/`FAILED`; `peerKeyStatus`/`PeerKeyInfo`), `ChatThreadScreen` (rozwijane pole szukania + baner TOFU `chat_e2e_key_changed` przy `status==CHANGED`, odcisk przez `E2eCrypto.fingerprint`). Kompiluje się (`:app:compileStandaloneDebugKotlin` BUILD SUCCESSFUL 2026-09-11) |
| 7 — teksty w UI | **zrobione (obie strony)**: webappka (wdrożona na produkcję) + Android — `E2eKeysScreen` (blok uczciwej pomocy E2E `e2e_help_*`: co chroni, co NIE chroni, nowe urządzenie, utracone hasło + `TextButton` do polityki `AppLinks.privacyPolicyFor`), 9 nowych kluczy i18n we wszystkich 6 językach (`chat_e2e_key_changed*`, `e2e_help_*`) dodanych do `values*/strings.xml`, sekcja E2E w polityce prywatności webappki (wszystkie 6 języków, 2026-09-12). Android kompiluje się; **sekcja E2E w polityce aplikacji** — polityka webappki (hostowana) jest linkowana z ekranu Androida przez `e2e_help_learn_more`, więc na Androidzie nie trzeba osobnej kopii (jak w webappce) |

**Czego faza 1 NIE dowodzi:** testy używają kluczy z wektorów, więc nie sprawdzają
jednej rzeczy — że `KeyPairGenerator` produkuje parę, której klucz publiczny da się
zakodować tą samą drogą. Pokrywa to test „świeżo wygenerowane klucze działają dla
dwóch odbiorców" (pełny obieg na prawdziwych parach), ale to jest jedyne miejsce,
gdzie wektory milczą. Warto o tym pamiętać, jeśli kiedyś zmieni się dostawca JCA.

⚠️ Wektory są **źródłem prawdy formatu**: stałe klucze, stałe IV-y, wynik
bajt w bajt. Implementacja w Kotlinie i w webappie musi je odtworzyć — inaczej
któraś z nich jest błędna i odbiorca zobaczy „nie można odszyfrować".
⚠️ Referencja liczy HKDF **ręcznie** (extract + expand), a nie przez `hkdfSync`,
bo JCA nie ma HKDF w API publicznym (jest dopiero od JDK 24) i Android zrobi to
tak samo. Użycie `hkdfSync` dałoby zgodny wynik, ale nie sprawdziłoby ścieżki,
która faktycznie pójdzie na produkcji.

---

## 8. Pułapki

* ⚠️ **Kasowanie funkcji: `firebase functions:delete`, NIE deploy.** `firebase deploy
  --only functions:<nazwa>` **nie usuwa** funkcji, której nie ma już w źródłach —
  zostaje w projekcie i dalej czyta treść. Właściwe polecenie:
  `firebase functions:delete onMessageSearchIndex --project mini-verbigem --force`.
  ⚠️ To polecenie **wypisze błąd** `The specified filters do not match any existing
  functions`, jeśli trafi na drugi przebieg — powłoka w tym projekcie wykonuje
  polecenia dwukrotnie, a pierwszy przebieg już skasował funkcję. **Ten błąd nie
  oznacza niepowodzenia** — sprawdź `firebase functions:list`.
  ⚠️ **Nigdy `firebase deploy --only functions`** (bez nazw): codebase `default`
  jest wspólna z webappką, więc CLI uzna jej funkcje za osierocone i skasuje je
  — razem z płatnościami, portfelem i OCR. Po każddym deployu funkcji zrób
  `firebase functions:list` i policz, czy nadal jest ich 14.
* ⚠️ **Whitelista pól w regułach `chats` to nie kosmetyka.** `allow update` ma
  `affectedKeys().hasOnly([...])`, więc **każde nowe pole dokumentu rozmowy wymaga
  dopisania go tam**. Bez tego zapis jest odrzucany jako `PERMISSION_DENIED`, a objaw
  jest mylący: outbox ponawia wysyłkę w nieskończoność (dokładnie jak przy
  `lastMessageType`). Faza 5 dopisała `lastMessageEnc` i `lastMessageBody`.
* ⚠️ **Nieaktualny podgląd w skrzynce to cichy błąd.** Gdy wiadomość jest jawna,
  pola `lastMessageEnc`/`lastMessageBody` muszą być **usunięte** (`FieldValue.delete()`
  / `deleteField()`), a nie pominięte — inaczej w skrzynce zostaje podgląd
  z poprzedniej wiadomości i pokazuje treść, której nadawca już nie wysłał.
* ⚠️ **Odszyfrowanie podglądów nie może biec w `recompute()`.** `recompute()` jest
  wołane przy każdym sygnale z kilku listenerów, a ECDH na kilkadziesiąt rozmów
  na wątku głównym to widoczne zacięcie. Dlatego `refreshPreviews()` liczy
  w `Dispatchers.Default`, tylko dla rozmów o zmienionym `lastMessageAt`,
  i trzyma wynik w cache.
* ⚠️ **Długość podglądu musi być ta sama w obu ścieżkach** — `PREVIEW_MAX_CHARS`
  w `ChatThreadViewModel` i `preview.take(80)` w `ChatRepository`. Rozjazd
  oznacza, że skrzynka zmienia długość tekstu tylko dlatego, że rozmowa
  przeszła na szyfrowanie.

* ⚠️ **`crypto.subtle` nie istnieje poza HTTPS/localhost.** Webapp działa na
  HTTPS, ale trzeba to sprawdzić i zwrócić błąd, a nie cicho wysłać jawny tekst.
* ⚠️ **Normalizacja i kodowanie muszą być identyczne w obu klientach** — ten sam
  błąd co przy `searchText` (`MessageSearch.normalize` ↔ `normalizeForSearch`).
  Tu dochodzi base64, punkt nieskompresowany P-256 (65 bajtów, prefiks `0x04`)
  i UTF-8. Faza 1 istnieje dokładnie po to, żeby to złapać na wektorach, a nie
  na produkcji.
* ⚠️ **Android Keystore nie pozwala wyeksportować klucza prywatnego** — i dobrze.
  Ale to znaczy, że kopia zapasowa telefonu NIE przeniesie tożsamości. Nowe
  urządzenie = nowy klucz = nowa „tożsamość" w oczach rozmówcy (TOFU pokaże
  ostrzeżenie). Trzeba to opisać w pomocy, bo inaczej wygląda jak błąd.
* ⚠️ **Reguły muszą zabronić nadpisania cudzego klucza publicznego.** Bez tego
  atakujący z kontem może podmienić klucz ofiary na własny i czytać jej
  wiadomości — a to jest dokładnie ten scenariusz, którego brak weryfikacji
  nie neutralizuje.
* ⚠️ **Klucze wczytują się asynchronicznie, a kolejka startuje od razu.**
  `openThread()` woła `flushOutbox()` w tej samej chwili, w której zaczyna
  pobierać klucze. Bez `ensureKeys()` na wejściu do `flushRow` wiadomość
  wysłana tuż po wejściu w wątek poleciałaby **jawnie**, mimo że obie strony
  mają klucze — czyli cicho, dokładnie w tym momencie, w którym użytkownik
  najbardziej liczy na szyfrowanie.
* ⚠️ **Brak klucza lokalnego nie znaczy „nie da się odszyfrować”.**
  `decryptCached()` celowo NIE zapamiętuje wyniku, gdy `myIdentity` jest
  jeszcze `null`. Inaczej pierwszy przebieg `recompute()` — a ten leci
  z `watchLatestMessages`, zanim tożsamość zdąży się wczytać — zamurowałby
  wszystkie dymki jako nieczytelne na stałe.
* ⚠️ **Szyfrowanie nie może po cichu zejść do jawności.** Gdy klucze są,
  a `MessageCipher.encrypt` rzuci, wyjątek leci do `flushOutbox` i wiersz
  dostaje „nie wysłano / ponów”. Cicha wysyłka jawna byłaby dokładnie tym,
  przed czym to szyfrowanie ma chronić. Jawność jest dozwolona TYLKO wtedy,
  gdy którejś ze stron brakuje klucza.
* ⚠️ **Szyfrowanie samego `text` to za mało.** Koperta musi objąć `hintText`,
  `ocrText` i `transcript`, a przy wysyłce te pola muszą zostać PUSTE —
  inaczej jawna treść leży obok koperty w tym samym dokumencie i cała
  robota jest teatrem. Pilnuje tego jedna funkcja: `outgoing()`.
* ⚠️ **JCA koduje klucz prywatny EC w 67 bajtach — BEZ klucza publicznego**
  (pole `[1]` w `ECPrivateKey` jest opcjonalne). Sprawdzone realnym kluczem
  z SunEC: WebCrypto taki PKCS#8 przyjmuje, liczy identyczne ECDH i odszyfrowuje
  szyfrogram. Gdyby tego nie potrafiło, odtworzenie historii w przeglądarce nie
  działałoby **wcale**, a testy webapp → webapp by tego nie wykryły. Fixture:
  `mini/scripts/jca_pkcs8_fixture.json`, sekcja 8 w `npm run e2e:parity`.
* ⚠️ **Przeglądarka nie ma Keystore.** Klucz prywatny w IndexedDB jest czytelny
  dla każdego, kto uruchomi JS w tej domenie (XSS, złośliwe rozszerzenie).
  To jest słabsze niż Android i jest zapisane tutaj jako znana różnica —
  nie udajemy, że jest inaczej.
* ⚠️ **`crypto.subtle` istnieje tylko w HTTPS.** Poza nim `encryptionAvailable()`
  zwraca `false` i wysyłka jest jawna — ale UI MUSI o tym powiedzieć
  (`chat.e2eNoHttps`). Cicha wysyłka jawna „bo się nie udało" jest niedopuszczalna.
* ⚠️ **Strażnik i18n (`npm run i18n:code`) flaguje KAŻDY tekst podany wprost do
  `new Error(...)`** — niezależnie od języka. Komunikaty techniczne trzymamy
  więc w stałej (`ERR`) i rzucamy przez `fail(ERR.x)`; zdania dla użytkownika
  są w słownikach i idą przez `t(uiLang, …)`.
* ⚠️ `chats.lastMessage` jest dziś jawnym tekstem. Jeśli zostawimy go jawnego,
  podgląd w skrzynce nadal zdradza treść. Do decyzji w fazie 5.

---

## 9. Czego ten dokument jeszcze nie rozstrzyga

1. **Fraza odzyskiwania czy własne hasło** — domyślnie proponujemy wygenerowaną
   frazę (6 słów), ale ostateczny kształt ekranu ustalamy przy implementacji fazy 2.
2. **Kopie zapasowe Androida** — czy blokować przenoszenie klucza przez
   `allowBackup`, czy zostawić domyślne zachowanie. Klucz i tak jest zaszyfrowany
   hasłem, więc kopia zapasowa nie oddaje historii bez hasła.
3. **Weryfikacja tożsamości później** — czy zostawiamy sobie furtkę na ekran
   „numer bezpieczeństwa" w przyszłości. Klucz tożsamości jest osobny od kluczy
   efemerycznych, więc da się to dołożyć bez zmiany formatu koperty.

### Rozstrzygnięte

* **Model klucza** — jeden klucz na konto, kopia na serwerze zaszyfrowana hasłem
  (§5). Odrzucone: klucz per urządzenie (nowy telefon tracił historię).
* **Podgląd w skrzynce** — szyfrowany, odszyfrowywany lokalnie (§6).
* **Weryfikacja tożsamości** — brak, łagodzona przez TOFU (§4).
