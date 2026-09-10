# Play Console — co wypełnić (gotowe do skopiowania)

Przewodnik krok po kroku do wypełnienia w Google Play Console. AAB jest już zbudowany
(`app/build/outputs/bundle/playRelease/app-play-release.aab`, pakiet `com.verbigem.app`).

---

## 0. Play App Signing (przy pierwszym wgraniu AAB)

Play Console → **Setup → App integrity → App signing**.

Masz dwa wyjścia:
- **A (zalecane, najprostsze):** po prostu wgraj AAB podpisany kluczem uploadu
  (`app/release-keystore.jks`, alias `verbigem`). Google sam wygeneruje klucz
  podpisujący Sklepu i poprosi o certyfikat Twojego klucza uploadu.
- **B („Upload a key"):** wklej plik `play-upload-cert.pem` (leży obok tego dokumentu)
  albo poniższe odciski.

Odciski Twojego klucza uploadu (do weryfikacji):
- **SHA-1:** `1A:9B:77:0A:68:1D:77:F5:91:F4:5C:01:AA:FD:5C:74:FF:9C:8A:1F`
- **SHA-256:** `A8:5C:E9:F7:A4:B8:36:7B:4D:88:3E:3C:10:A3:4C:E4:59:34:F4:3A:7F:C0:1F:56:56:85:FC:08:D9:F2:B3:4C`

`play-upload-cert.pem` to certyfikat **publiczny** — bezpieczny do wgrania.

---

## 1. Data Safety form (Polityka danych)

Play Console → **Policy → App content → Data safety**.

### Dane, które zbieramy

| Kategoria | Przykłady | Przesyłane |
|---|---|---|
| **Email** | adres e-mail (logowanie przez Firebase Auth) | Tak — tylko do nas |
| **Informacje o użytkowniku** | historia tłumaczeń, historia OCR, kontakty/znajomi (Firestore `users/{uid}/…`) | Tak — tylko do nas |
| **Zdjęcia i pliki** | zdjęcia z aparatu/galerii do OCR | Przetwarzanie na urządzeniu (ML Kit); przetłumaczony tekst trafia do nas |
| **Audio** | nagrania głosowe (STT `SpeechRecognizer`) | Przetwarzanie na urządzeniu; wynik tekstowy do nas |
| **Aktywność w aplikacji** | ustawienia, historia, zanonimizowana analityka (jeśli jest) | Tak — tylko do nas |

### Udostępniane stronom trzecim
- Tekst wysyłany do **OpenRouter** — **tylko gdy użytkownik użyje funkcji Pro/TTS przez
  chmurę** → zaznacz **„Shared with third parties"**.

### Praktyki bezpieczeństwa
- Dane szyfrowane w transmisji (TLS) — zaznacz.
- Użytkownik może usunąć dane (konto + historia) — zaznacz opcje usuwania.

⚠️ Wypełnij zgodnie z prawdą (szczegóły w `PLAY_PUBLISHING_PLAN.md` §3.1).
Niezgodność z aplikacją = odrzucenie.

---

## 2. Content rating (Ocena wiekowa)

Play Console → **Policy → App content → Content rating** (kwestionariusz IARC).

Dla Verbigem (tłumacz offline + chmura):
- **Kategoria:** Utilities / Productivity (Narzędzia / Produktywność).
- Przemoc: **Brak**.
- Nagość: **Brak**.
- Treści niedozwolone / uzależniające: **Brak**.
- Mowa nienawiści: **Brak** (czaty moderowane przez użytkownika).
- Lokalizacja urządzenia: **Tak** (tłumaczenia, OCR).
- Udostępnianie danych innym użytkownikom: **Tak** (czaty, kontakty).
- Reklamy: jeśli włączone jest AdMob → zaznacz **„Contains ads"** i podaj link do
  polityki reklamowej (`mini.verbigem.com/privacy`).

Typowy wynik: **Everyone / 3+**, a przy zaznaczonych czatach i reklamach → **12+**.

---

## 3. Zamknięte testy (12 testerów × 14 dni)

Play Console → **Testing → Closed testing**:
1. Utwórz listę e-mail (`Create email list`), np. `weryfikacja-play`.
2. Wpisz **12 adresów** (rodzina/znajomi). **NIE używaj własnych kont** — Google wykrywa
   ten sam urządź/IP/dane odzyskiwania i może nie zaliczyć testu.
3. Wgraj AAB do tracku i wyślij do recenzji (recenzja tracku testowego jest szybka).
4. Po zatwierdzeniu wyślij testerom link opt-in. Muszą dołączyć i zostać 14 dni ciągłych.
5. Licznik 14 dni startuje, gdy dołączy 12 testerów.

---

## 4. Produkcja (po testach)

- Gdy minie 14 dni z 12 testerami → promuj track testowy do **produkcji**.
- Managed Publishing: możesz puścić ręcznie.
- Po publikacji produkcji (podpisanej przez Play) → włącz **App Check enforcement**
  (Faza 5 w planie: provider Play Integrity + SHA-256 certyfikatu podpisującego Play).

---

## 5. Firebase — osobna akcja (kanał sideload)

Nowa appka Firebase `com.verbigem.app.sideload` („Verbigem Sideload") **nie ma jeszcze
odcisków SHA** → phone-auth na sideload nie przejdzie, dopóki ich nie dodasz.

Firebase Console → Project Settings → Your apps → **Verbigem Sideload** → dodaj:
- SHA-1 debug: `ec9deb58cdf2483a7efe2b73c2c7901b9d6d3ccc`
- SHA-1 release: `1A:9B:77:0A:68:1D:77:F5:91:F4:5C:01:AA:FD:5C:74:FF:9C:8A:1F`
- SHA-256 release: `A8:5C:E9:F7:A4:B8:36:7B:4D:88:3E:3C:10:A3:4C:E4:59:34:F4:3A:7F:C0:1F:56:56:85:FC:08:D9:F2:B3:4C`

CLI `firebase` (v15.19.1) nie ma podkomendy do dodawania SHA — robisz to w przeglądarce.
