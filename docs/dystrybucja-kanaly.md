> 📄 Fragment dokumentacji Verbigem Android. Indeks: [README.md](../README.md)
>
> Ten plik opisuje **kanały dystrybucji** (własna strona / Google Play / App Store).
> Techniczne różnice między AAB a APK są w [`dystrybucja.md`](dystrybucja.md).

# Dystrybucja własnej aplikacji — Android i iPhone

*Instrukcja krok po kroku: własna strona oraz oficjalne sklepy (stan na wrzesień 2026)*

## Podsumowanie

Android od zawsze pozwala rozdawać aplikacje z własnej strony (plik `.apk`/`.aab`). iPhone jest znacznie bardziej zamknięty – standardowa droga to App Store, ale od 2024 roku, dzięki unijnym przepisom (Digital Markets Act), da się to obejść w Unii Europejskiej. Ten dokument opisuje obie ścieżki: dystrybucję z własnej strony oraz publikację w oficjalnych sklepach (Google Play i Apple App Store).

> **Ważne:** Bezpośrednia dystrybucja z własnej strony na iPhone (tzw. Web Distribution) wymaga spełnienia jednego z rygorystycznych kryteriów Apple (np. 1 mln instalacji rocznie i 2 lata w programie deweloperskim, albo finansowanie VC, albo list kredytowy na 1 mln USD). Dla pojedynczego dewelopera w praktyce jest to niedostępne. Realną alternatywą jest publikacja w istniejącym, zatwierdzonym przez Apple alternatywnym sklepie – opisana w Części 2, Opcja C.

---

## Część 1: Android — własna strona

### Wymagania

- Plik instalacyjny w formacie `.apk` (prosty, jeden plik) lub `.aab` (potrzebuje dodatkowego narzędzia do wygenerowania `.apk` do dystrybucji poza Google Play).
- Aplikacja podpisana Twoim własnym kluczem (keystore) — to wymóg Androida niezależnie od kanału dystrybucji.
- Miejsce na hosting pliku (Twoja strona, dowolny serwer, CDN).
- Od 2026/2027: rejestracja jako deweloper w Android Developer Console (patrz uwaga niżej).

### Krok po kroku

1. Zbuduj wersję „release” aplikacji (nie „debug”) w Android Studio: **Build → Generate Signed App Bundle / APK**.
2. Podpisz aplikację własnym kluczem (keystore) — Android Studio przeprowadzi Cię przez ten proces przy pierwszym budowaniu wersji podpisanej.
3. Przetestuj plik `.apk` na realnym urządzeniu przed publikacją.
4. Wgraj plik `.apk` na swój serwer/stronę (zwykły link do pobrania wystarczy, np. `https://twojastrona.pl/pobierz/aplikacja.apk`).
5. Na stronie umieść jasną instrukcję dla użytkownika, ponieważ Android domyślnie blokuje instalację z nieznanych źródeł (patrz niżej).
6. Rozważ udostępnienie sumy kontrolnej (SHA-256) pliku, aby użytkownicy mogli zweryfikować integralność pobranego pliku.

### Co musi zrobić użytkownik

- Pobiera plik `.apk` z Twojej strony w przeglądarce na telefonie.
- Przy próbie otwarcia pliku system poprosi o zgodę „Zezwól na instalację z tego źródła” (np. z Chrome lub menedżera plików) — to ustawienie per aplikacja, od Androida 8.
- Po zaakceptowaniu instalacja przebiega jak każda inna.

> **Ważne:** Google ogłosiło, że od 2026/2027 wprowadza obowiązkową weryfikację tożsamości dewelopera (Android Developer Console) dla **wszystkich** metod instalacji na certyfikowanych urządzeniach z Google Play — łącznie z sideloadingiem z własnej strony. Nie chodzi o recenzję aplikacji, tylko o potwierdzenie, kim jest deweloper (jak weryfikacja tożsamości na lotnisku, nie kontrola bagażu). Wdrożenie zaczyna się 30 września 2026 w Brazylii, Indonezji, Singapurze i Tajlandii, a globalnie ma objąć wszystkich w 2027 roku. Rejestracja jest bezpłatna i jednorazowa; dla hobbystów/studentów ma być uproszczona ścieżka. Zaawansowani użytkownicy nadal będą mogli zainstalować niezweryfikowaną aplikację przez ADB (z komputera), ale zwykli użytkownicy klikający plik `.apk` w telefonie — nie. **Zalecenie:** zarejestruj się w Android Developer Console z wyprzedzeniem, gdy tylko rejestracja będzie dostępna w Twoim regionie.

---

## Część 2: iPhone — poza App Store

### Opcja A — App Store (droga standardowa)

Opisana szczegółowo w Części 4 poniżej.

### Opcja B — TestFlight (testy beta)

Oficjalne narzędzie Apple do rozsyłania wersji testowych bez pełnej recenzji App Store.

- Wymaga konta Apple Developer (99 USD/rok).
- Możesz wysłać publiczny link testowy do max 10 000 testerów.
- Build jest ważny tylko 90 dni — potem trzeba wgrać nową wersję.
- To rozwiązanie tymczasowe/testowe, nie do stałej dystrybucji produkcyjnej.

### Opcja C — AltStore PAL (realna droga „własna strona” dla solo-dewelopera)

To obecnie najbardziej dostępna metoda dla pojedynczego dewelopera, żeby dotrzeć do użytkowników iPhone poza App Store, bez spełniania wygórowanych wymogów finansowych Apple. AltStore PAL to zatwierdzony przez Apple alternatywny sklep, w którym możesz samodzielnie publikować aplikacje, hostując pliki na własnym serwerze.

**Wymagania:**
- Aktywne, płatne konto Apple Developer Program (99 USD/rok) — bez tego się nie da.
- Zgoda na *Alternative EU Terms Addendum* w Apple Developer (formalność online).
- Własny serwer/hosting do przechowywania plików aplikacji (zwykły hosting stron wystarczy).

**Krok po kroku:**

1. Załóż/aktywuj konto Apple Developer Program (jeśli jeszcze go nie masz).
2. Zaakceptuj „Alternative EU Terms Addendum” — wniosek składa się online przez stronę Apple Developer.
3. Zarejestruj swój Developer ID w AltStore PAL przez ich REST API (instrukcje na faq.altstore.io).
4. Prześlij aplikację przez App Store Connect do notaryzacji (Notarization) — to uproszczona weryfikacja bezpieczeństwa, dużo mniej rygorystyczna niż pełne App Review; nie ocenia się tu np. pomysłu na aplikację, tylko czy nie zawiera złośliwego kodu.
5. Po zatwierdzeniu pobierz tzw. ADP (Alternative Distribution Package) przez REST API AltStore.
6. Wgraj cały pakiet ADP na swój serwer, zachowując dokładnie strukturę katalogów (nie modyfikuj pliku `manifest.json`).
7. Stwórz „source” — prosty plik JSON z metadanymi Twojej aplikacji — i umieść jego URL na swojej stronie.
8. Gotowe: użytkownik z iPhonem w UE, Japonii lub Brazylii instaluje aplikację AltStore PAL, dodaje URL Twojego „source” i może pobrać Twoją aplikację.

> **Uwaga:** Ta droga działa tylko dla użytkowników z Apple ID zarejestrowanym w UE, Japonii lub Brazylii (region konta, nie fizyczna lokalizacja telefonu). Ty jako deweloper możesz mieszkać i publikować skądkolwiek na świecie.

### Opcja D — Web Distribution na Twojej własnej domenie (bez pośrednika)

Technicznie istnieje, ale Apple obwarował ją wymogami eliminującymi większość małych deweloperów. Wystarczy spełnić **jeden** z poniższych punktów:

- Organizacja non-profit, placówka edukacyjna lub instytucja rządowa (zwolnienie z opłat).
- Ocena Global Business Ranking od Dun & Bradstreet na poziomie „Low Risk” lub „Below Average Risk”.
- Spółka notowana na giełdzie należącej do World Federation of Exchanges lub Euronext.
- Finansowanie od funduszu VC z listy Midas List, Midas List Europe, Invest Europe lub HEC-Dow Jones.
- Gwarancja bankowa (stand-by letter of credit) na 1 000 000 USD.
- Audyt finansowy z ostatnich 3 lat bez zastrzeżeń, wykonany przez akredytowaną firmę audytorską.
- **LUB:** minimum 2 lata nieprzerwanego członkostwa w Apple Developer Program w dobrym standingu ORAZ co najmniej jedna aplikacja z ponad 1 000 000 pierwszych instalacji rocznie na świecie w poprzednim roku kalendarzowym.

Dodatkowo Twoja organizacja musi być zarejestrowana/inkorporowana w UE (lub mieć podmiot zależny zarejestrowany w UE), a każda transakcja podlega 5% prowizji (Core Technology Commission) na rzecz Apple.

> **Ważne:** Jeśli dopiero zaczynasz jako niezależny deweloper, żaden z powyższych punktów prawdopodobnie nie jest dla Ciebie osiągalny na start. Dlatego w praktyce Opcja C (AltStore PAL) jest dla Ciebie realną drogą do celu „aplikacja pobierana z linku, bez App Store”.

### Co musi zrobić użytkownik iPhone (Opcje C i D)

- Musi mieć Apple ID zarejestrowane w kraju UE (Ustawienia → [Twoje imię] → Media i zakupy → Kraj/region).
- Instaluje AltStore PAL (lub inny zatwierdzony marketplace) ze strony altstore.io.
- W Ustawieniach zatwierdza dostawcę marketplace („Zezwól na marketplace od…”).
- Dodaje URL Twojego „source” w aplikacji AltStore i stamtąd instaluje Twoją aplikację.

---

## Część 3: Google Play Store (droga oficjalna)

### Wymagania i koszty

- Konto Google (osobiste lub organizacji).
- Jednorazowa opłata rejestracyjna: **25 USD** (bez odnawiania).
- Aplikacja zgodna z [zasadami programu dla deweloperów Google Play](https://play.google.com/console/about/guides/developer-policy-comprehensive/) (prywatność, treści, bezpieczeństwo).
- Dla **nowych kont osobistych** (założonych po 13 listopada 2023): obowiązkowy test zamknięty z **min. 12 testerami przez nieprzerwane 14 dni** przed uzyskaniem dostępu produkcyjnego. Konta organizacji (firmowe, zweryfikowane przez D-U-N-S) są z tego wymogu zwolnione.

### Krok po kroku

1. Załóż konto dewelopera w [Google Play Console](https://play.google.com/console) przy użyciu konta Google.
2. Zaakceptuj Umowę dystrybucyjną dla deweloperów Google Play.
3. Wpłać jednorazową opłatę rejestracyjną 25 USD.
4. Przejdź weryfikację tożsamości (dokument tożsamości dla konta osobistego lub numer D-U-N-S dla organizacji).
5. Utwórz nową aplikację w konsoli: podaj nazwę, domyślny język, typ (aplikacja/gra) i informację, czy jest bezpłatna czy płatna.
6. Wypełnij kartę sklepu (Store listing): opis krótki i długi, ikona, grafiki promocyjne, zrzuty ekranu, kategoria.
7. Uzupełnij wymagane sekcje zgodności: ankietę dotyczącą treści (content rating), sekcję bezpieczeństwa danych (Data safety — jakie dane zbiera aplikacja), grupę docelową i politykę prywatności (link do strony z polityką).
8. Wgraj podpisany plik `.aab` (Android App Bundle — obecnie wymagany format zamiast `.apk`) w sekcji **Wersje aplikacji → Produkcyjna** lub najpierw do testów.
9. Jeśli masz nowe konto osobiste: uruchom **test zamknięty** (Testowanie → Test zamknięty), zaproś min. 12 testerów mailem lub linkiem i poczekaj na 14 nieprzerwanych dni aktywnego testowania.
10. Po spełnieniu wymogu testów złóż wniosek o dostęp produkcyjny w konsoli (Dashboard → poproś o dostęp produkcyjny), odpowiadając na pytania o aplikację i proces testowy.
11. Po zatwierdzeniu (zwykle do 7 dni) opublikuj wersję produkcyjną — aplikacja trafia do weryfikacji Google (zwykle godziny do kilku dni), a następnie staje się widoczna w Google Play.
12. Zarządzaj aktualizacjami, wgrywając kolejne wersje `.aab` z wyższym numerem `versionCode`.

---

## Część 4: Apple App Store (droga oficjalna)

### Wymagania i koszty

- Konto Apple Developer Program: **99 USD/rok** (indywidualne) lub 299 USD/rok (Enterprise — tylko dystrybucja wewnętrzna, nie do App Store).
- Mac z zainstalowanym Xcode (do budowania i przesyłania aplikacji) — chyba że korzystasz z CI/CD w chmurze.
- Aplikacja zgodna z [App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/) oraz aktualnymi wymogami technicznymi (od 28 kwietnia 2026 obowiązkowe budowanie aplikacji przy użyciu SDK dla iOS 26 / Xcode 26 lub nowszego).
- Prywatność: plik `PrivacyInfo.xcprivacy` (privacy manifest) deklarujący, jakie API i po co są używane, oraz wypełnione „Etykiety prywatności” (App Privacy „Nutrition Labels”) w App Store Connect.

### Krok po kroku

1. Zarejestruj się w [Apple Developer Program](https://developer.apple.com/programs/) i opłać roczną składkę 99 USD.
2. W Xcode skonfiguruj identyfikator aplikacji (Bundle ID), certyfikaty podpisywania i profil provisioningowy (Xcode może zrobić to automatycznie przez „Automatically manage signing”).
3. Zbuduj aplikację w konfiguracji „Release” i utwórz archiwum: **Product → Archive**.
4. Prześlij archiwum do App Store Connect bezpośrednio z Xcode (Organizer → Distribute App → App Store Connect) lub przez aplikację **Transporter**.
5. Zaloguj się do [App Store Connect](https://appstoreconnect.apple.com/) i utwórz nowy wpis aplikacji: nazwa, główny język, Bundle ID, SKU.
6. Uzupełnij kartę produktu: opis, słowa kluczowe, zrzuty ekranu dla wymaganych rozmiarów ekranu, ikona, kategoria, adres URL polityki prywatności.
7. Wypełnij ankietę klasyfikacji wiekowej (Age Rating) oraz sekcję prywatności (App Privacy) — jakie dane są zbierane i w jakim celu.
8. Przypisz przesłaną wersję (build) do wpisu aplikacji w sekcji „Build”.
9. Wybierz metodę udostępnienia: automatyczne po zatwierdzeniu, ręczne, lub zaplanowane na konkretną datę.
10. Prześlij aplikację do recenzji (**Submit for Review**). Apple zatwierdza ok. 90% zgłoszeń w ciągu 24–48 godzin; aplikacje w kategoriach wrażliwych (finanse, zdrowie, AI) lub bardziej złożone mogą czekać dłużej. W nagłych przypadkach można poprosić o przyspieszoną recenzję.
11. Po zatwierdzeniu aplikacja publikuje się zgodnie z wybraną metodą udostępnienia i staje się widoczna w App Store (do 175 regionów, 50 języków).
12. Aktualizacje wymagają powtórzenia kroków 3–10 dla każdej nowej wersji (numer wersji musi być wyższy niż poprzedni).

### Koszty transakcyjne

- Standardowa prowizja Apple: 15–30% od sprzedaży aplikacji i zakupów w aplikacji (niższa stawka m.in. w ramach Small Business Program dla przychodów poniżej 1 mln USD/rok).
- W UE od 1 października 2026 obowiązują nowe, ujednolicone stawki: m.in. 26% przy standardowym In-App Purchase, 21% przy płatnościach alternatywnych w aplikacji, 15% za sprzedaż domkniętą na stronie zewnętrznej w ciągu 7 dni od kliknięcia linku.

---

## Porównanie wszystkich metod

| Metoda | Koszt | Kto może z niej skorzystać | Zasięg / ograniczenia |
|---|---|---|---|
| Android — APK na własnej stronie | 0 zł (opcjonalnie weryfikacja dewelopera w Google) | Każdy, od razu | Cały świat; od 2026/2027 wymagana weryfikacja tożsamości dewelopera |
| Android — Google Play (oficjalnie) | 25 USD jednorazowo | Każdy deweloper | Cały świat; nowe konta osobiste muszą przejść test zamknięty (12 testerów / 14 dni) |
| iPhone — App Store (oficjalnie) | 99 USD/rok | Każdy deweloper | Cały świat, 175 regionów; pełna recenzja Apple |
| iPhone — TestFlight | 99 USD/rok | Każdy deweloper | Do 10 000 testerów, build ważny 90 dni — to wersja beta |
| iPhone — Ad Hoc / Xcode | 99 USD/rok | Każdy deweloper | Max 100 zarejestrowanych urządzeń, ręczna instalacja |
| iPhone — Web Distribution (własna domena) | 99 USD/rok + 5% prowizji | W praktyce duże firmy/organizacje | Tylko użytkownicy UE z Apple ID zarejestrowanym w UE |
| iPhone — AltStore PAL (alternatywny sklep) | 99 USD/rok | Praktycznie każdy solo-deweloper | Tylko użytkownicy UE, Japonii i Brazylii z odpowiednim Apple ID |

## Rekomendacja

- **Android:** jeśli chcesz maksymalnego zasięgu i wiarygodności — publikuj w Google Play (25 USD, jednorazowo). Jeśli chcesz ominąć proces recenzji i testów — hostuj `.apk` na własnej stronie, pamiętając o nadchodzącej weryfikacji deweloperów Google.
- **iPhone:** dla maksymalnego zasięgu — App Store, mimo kosztu 99 USD/rok i recenzji Apple. Jeśli zależy Ci na dystrybucji poza App Store i nie spełniasz wygórowanych wymagań Web Distribution — AltStore PAL to obecnie najbardziej realna droga.

## Źródła

- [Apple Developer – Web Distribution in the EU](https://developer.apple.com/support/web-distribution-eu)
- [AltStore PAL – Distribute with AltStore PAL](https://faq.altstore.io/developers/distribute-with-altstore-pal)
- [AltStore – strona dla deweloperów](https://developer.altstore.io/)
- [Android Developer Console – Understanding Android developer verification](https://support.google.com/android-developer-console/answer/16561738?hl=en)
- [Apple – nowe warunki biznesowe w UE, sierpień 2026](https://xenospectrum.com/en/apple-eu-unified-app-terms/)
- [Google Play Console – Pierwsze kroki](https://support.google.com/googleplay/android-developer/answer/6112435?hl=pl)
- [Google Play Console – Wymagania dotyczące testowania nowych kont](https://support.google.com/googleplay/android-developer/answer/14151465?hl=pl)
- [Apple App Store Submission Guide 2026](https://gotechsolutions.co/blog/apple-app-store-submission-guide-2026/)
