# Czat szyfrowany end-to-end — projekt

Dokument decyzyjny. Powstał, zanim powstał kod, bo ta zmiana dotyka jednocześnie
formatu wiadomości, Cloud Functions, reguł Firestore, bazy lokalnej i **dwóch
klientów** (Android + webapp). Pomyłka w kryptografii nie objawia się błędem —
objawia się tym, że użytkownik wierzy, że jest bezpieczny, a nie jest.

Ostatnia aktualizacja: 2026-09-11 — projekt, kod jeszcze nie napisany.

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

| Miejsce | Co robi z treścią |
|---|---|
| `functions/src/searchIndex.ts` → `onMessageSearchIndex` | czyta pole `text` z `chats/{id}/messages/{msgId}` i zapisuje znormalizowaną kopię w `searchText` — czyli **buduje pełnotekstowy indeks treści** |
| `functions/src/messaging.ts` → `onMessageCreated` | czyta treść, żeby wstawić ją do treści powiadomienia push |

Dodatkowo `ChatRepository.sendMessage` zapisuje `text` jawnym tekstem, a reguły
Firestore (`firestore.rules`, blok `messages`) **zabraniają klientowi pisania
`searchText`** — bo indeks należy do funkcji. Wyszukiwanie w aplikacji działa
wyłącznie po tym serwerowym indeksie (`ChatRepository.searchMessages`).

Wniosek: dopóki `searchText` istnieje, E2E jest fikcją — treść i tak leży
odczytana na serwerze w drugim dokumencie.

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
| losowość | `SecureRandom` | JCA | `crypto.getRandomValues` |

**Klucz na urządzenie, nie na konto.** Każde urządzenie generuje raz parę
tożsamościową (P-256) i publikuje tylko klucz publiczny. Prywatny nigdy nie
opuszcza urządzenia — na Androidzie jest zapisany w Keystore, w webappie jako
nieeksportowalny `CryptoKey` w IndexedDB.

Uzasadnienie: klucz wspólny dla konta musiałby leżeć na serwerze (bo nie mamy
hasła — logowanie idzie przez Google), a wtedy nie ma czego chronić.

### Koperta wiadomości

Per wiadomość, nie per para urządzeń — dzięki temu mamy **forward secrecy**
(ujawnienie później klucza tożsamości nie odsłania starych wiadomości):

```
1. Nadawca losuje klucz wiadomości MK (32 B) i parę efemeryczną (epk, esk).
2. Dla KAŻDEGO urządzenia odbiorcy i KAŻDEGO swojego innego urządzenia:
     S_i      = ECDH(esk, pub_i)
     wrapKey_i= HKDF-SHA256(S_i, salt=iv_i, info="verbigem-chat-v1-wrap")
     wrapped_i= AES-256-GCM(wrapKey_i, MK, iv_i)
3. Treść: ciphertext = AES-256-GCM(MK, plaintext, iv_body)
4. esk jest wyrzucany z pamięci natychmiast po kroku 2.
```

Koszt: jedno generowanie pary kluczy na wiadomość (~1 ms) i jedno ECDH na
urządzenie odbiorcy. Przy czacie 1:1 i 2–3 urządzeniach to nieistotne.

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
| Wyszukiwanie w czacie | serwerowy indeks `searchText`, prefiksowy, po wszystkich rozmowach | **tylko lokalnie**, po wiadomościach już wczytanych do wątku (ostatnie 50 na rozmowę, doładowywane przy przewijaniu) |
| Treść powiadomienia push | fragment wiadomości | „Nowa wiadomość" (bez treści) — inaczej FCM = wyciek |
| Podgląd ostatniej wiadomości w skrzynce | `chats.lastMessage` jawny | jawny **placeholder** (`„Wiadomość"`) albo zaszyfrowany i odszyfrowywany lokalnie |
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
| 2 | Rejestr kluczy urządzeń + reguły | klucze publikowane, nikt ich jeszcze nie używa |
| 3 | Koperta w wysyłce/odbiorze (Android) | nowe wiadomości szyfrowane, stare czytane dalej |
| 4 | Koperta w webappie | parytet |
| 5 | Usunięcie `onMessageSearchIndex` + push bez treści | serwer przestaje widzieć treść |
| 6 | Wyszukiwanie lokalne + TOFU + ostrzeżenie o zmianie klucza | domknięcie funkcji |
| 7 | Teksty: pomoc w czacie, polityka prywatności, layout | **dopiero teraz** — wcześniej byłyby nieprawdą |

Punkt 7 jest celowo na końcu. Napisanie w UI „wiadomości są szyfrowane
end-to-end", zanim szyfrowane są, to nie kosmetyka — to wprowadzanie
użytkownika w błąd.

---

## 8. Pułapki

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
* ⚠️ `chats.lastMessage` jest dziś jawnym tekstem. Jeśli zostawimy go jawnego,
  podgląd w skrzynce nadal zdradza treść. Do decyzji w fazie 5.

---

## 9. Czego ten dokument jeszcze nie rozstrzyga

1. **Podgląd ostatniej wiadomości w skrzynce** — jawny placeholder czy
   szyfrogram odszyfrowywany lokalnie (kosztuje wpis w kluczach koperty dla
   każdego odbiorcy, ale zachowuje wygodę).
2. **Kopie zapasowe Androida** — czy blokować przenoszenie klucza przez
   `allowBackup`, czy zostawić domyślne zachowanie.
3. **Weryfikacja tożsamości później** — czy zostawiamy sobie furtkę na ekran
   „numer bezpieczeństwa" w przyszłości (wpływa na to, czy klucz tożsamości
   jest osobny od kluczy efemerycznych — w projekcie wyżej jest).
