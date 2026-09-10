# Verbigem Android — historia zmian

---

## v1.0.65 (2026-09-10) — versionCode 65

**Naprawione: część komunikatów ignorowała wybrany język interfejsu.**

- Po ustawieniu angielskiego część komunikatów nadal pojawiała się po polsku (i odwrotnie). Dotyczyło to błędów logowania, tłumaczenia, rozpoznawania mowy i tekstu z obrazka, doładowania konta oraz okna aktualizacji aplikacji.
- Przyczyna: te teksty pobierały tłumaczenie z kontekstu systemowego, który nie wie nic o języku wybranym w aplikacji — brał język telefonu. Wszystkie takie miejsca przechodzą teraz przez jedno wspólne, poprawne źródło tekstów.
- Błędy techniczne z bibliotek (np. „HTTP 402") przestały trafiać na ekran — zostają w logach, a użytkownik widzi zrozumiały komunikat w wybranym języku.
- Błędy rozpoznawania mowy miały **jedenaście** polskich tekstów wpisanych na sztywno w kodzie (m.in. „Błąd sieci", „Brak dźwięku mowy"). Teraz wszystkie pochodzą z tłumaczeń i zmieniają się razem z językiem interfejsu.
- Błędne hasło, zajęty e-mail i brak internetu przy logowaniu mają wreszcie własne komunikaty zamiast angielskiego tekstu z Firebase.
- Błąd pobierania aktualizacji (niekompletny plik, błąd serwera, błąd instalatora) też jest w wybranym języku.

## v1.0.64 (2026-09-10) — versionCode 64

**Naprawione: w wersji z Google Play były tylko dwa języki interfejsu.**

- Z Play instalowały się wyłącznie polski i angielski — niemieckiego, hiszpańskiego, tureckiego i chińskiego nie było w ogóle, mimo że w wersji pobieranej z naszej strony działały wszystkie sześć.
- Przyczyna: Google Play dzieli paczkę AAB na mniejsze części i domyślnie dzieli ją także **po języku** — telefon dostawał tylko zasoby swojego języka plus angielski jako zapasowy. Plik APK z naszej strony jest jeden i zawiera wszystko, dlatego tam problemu nie było.
- Dzielenie po języku zostało wyłączone dla wersji z Play. Kosztuje to kilkaset kilobajtów, ale każdy język działa na każdym telefonie.

**Naprawione: okno pobierania modelu kłamało i mówiło po polsku.**

- Po pobraniu modelu Szybkiego przełączenie na Dokładny pokazywało zielony komunikat „Model Dokładny jest gotowy do użycia", choć tego modelu wcale nie było na telefonie. Aplikacja brała stan ostatniego pobrania i doklejała do niego nazwę nowego modelu.
- Okno pobierania nie reagowało na wybrany język interfejsu — przy angielskim nadal pisało po polsku. Komunikaty w oknach systemowych trzeba było jawnie przekazać z języka wybranego w aplikacji, czego to jedno okno nie robiło.
- Ten sam błąd miało **osiem innych okien** w aplikacji (m.in. potwierdzenia w Profilu, wybór kanału w Kontaktach, podgląd zdjęcia w czacie, okna aktualizacji). Wszystkie pokazywały się w języku telefonu zamiast w wybranym — teraz mają wspólne, poprawne opakowanie.
- Usunięte zostały też polskie teksty wpisane na sztywno w kodzie pobierania (brak pamięci, brak miejsca, błąd serwera) — te pokazywałyby się po polsku niezależnie od języka.
- Wygasły kod SMS dostał osobny komunikat: wcześniej „to nie wygląda na kod, który wysłaliśmy" znaczyło zarówno literówkę, jak i to, że kod zdążył wygasnąć — a to wymaga zupełnie innej reakcji.
- Czas na wpisanie kodu wydłużony z 60 do 120 sekund. Przy wolniejszym doręczeniu SMS-a poprzednie 60 sekund mijało, zanim użytkownik zdążył przepisać kod, i wtedy poprawny kod był odrzucany.

## v1.0.63 (2026-09-10) — versionCode 63

**Naprawione: aplikacja nie chciała się uruchomić na nowszych telefonach.**

- Na Androidzie 14 i nowszych apka wywalała się w ułamku sekundy po starcie — czarny ekran i komunikat o błędzie, bez szansy na wejście do środka.
- Przyczyna: jedno pole w głównym ekranie było tworzone zbyt wcześnie, jeszcze zanim system zdążył podpiąć aplikację. Starsze Androidy to wybaczały, nowsze rzucają błąd.
- Teraz to pole powstaje dopiero wtedy, gdy jest naprawdę potrzebne. Dla wersji z Google Play nie powstaje w ogóle, bo nie jest tam używane.
- Uwaga: od tej wersji numer wersji zgadza się z numerem kompilacji (63 = 1.0.63). Wcześniej były rozjechane o jeden.

## v1.0.60 (2026-09-10) — versionCode 61

**Build: cel kompilacji podniesiony do API 36 (Android 16).**

- Google Play wymaga od 2026-08-31, żeby nowe aplikacje targetowały co najmniej API 36 — podnieśliśmy `compileSdk` i `targetSdk` z 35 na 36.
- Żadnych zmian w kodzie; tylko cel budowania. Użytkownicy nic nie zauważą.

## v1.0.59 (2026-09-10) — versionCode 60

**Naprawione: „1 kontaktów", „1 mutual friends".**

- Trzy komunikaty z liczbą mówiły zawsze to samo, niezależnie od wyniku: po imporcie pliku z pięcioma kontaktami aplikacja pisała „Imported 5 contact", a przy jednym wspólnym znajomym — „1 mutual friends".
- Teraz te trzy komunikaty odmieniają się przez liczbę, zgodnie z zasadami każdego języka. Po polsku to osobne formy dla 1, 2–4 i 5+ („1 kontakt", „3 kontakty", „10 kontaktów"); po turecku i chińsku jedna, bo te języki nie odmieniają rzeczownika po liczebniku.
- Zrobione mechanizmem Androida do liczby mnogiej, nie ręcznym „jeśli 1 to…", żeby nowe języki nie wymagały poprawek w kodzie.


## v1.0.58 (2026-09-10) — versionCode 59

**Naprawione: dwa miejsca, które udawały, że się udało.**

- Usunięcie rozmowy, którego nie udało się zapisać, nie dawało żadnego znaku. Wracałeś do skrzynki, a rozmowa nadal tam była — bez słowa dlaczego. Teraz telefon pokazuje krótki komunikat, że się nie udało.
- Szukanie znajomych, które padło (brak sieci albo brak uprawnień), wyglądało dokładnie jak „nie ma takiego użytkownika": po prostu pusta lista. Teraz pod polem wyszukiwania pojawia się informacja, że zapytanie się nie udało. Pusta lista znów znaczy tylko tyle, że nikogo nie znaleziono.
- Komunikaty są w 6 językach, jak reszta aplikacji.


## v1.0.57 (2026-09-10) — versionCode 58

**Naprawione: druga wiadomość potrafiła utknąć na „wysyłanie".**

- Kiedy wysyłałeś coś, a poprzednia wiadomość jeszcze się wysyłała (typowe przy dwóch zdjęciach pod rząd), nowa czekała na następny impuls — powrót sieci albo kolejną wysyłkę. Teraz kolejka dopina się sama, od razu po tamtej.
- Czytaj Pro, gdy nie udało się wygenerować mowy, nie robiło nic — ani dźwięku, ani słowa wyjaśnienia. Teraz mówi wprost, że się nie udało.


## v1.0.56 (2026-09-10) — versionCode 57

**Naprawione: mikrofon milczał, gdy coś nie działało.**

- Brak zgody na mikrofon, brak rozpoznawania mowy w telefonie i błąd rozpoznawania kończyły się tylko wpisem w logu systemowym. Dla Ciebie wyglądały identycznie: mikrofon „po prostu nie działa", bez podania powodu.
- Teraz każda z tych trzech sytuacji mówi wprost, co się stało — krótkim komunikatem nad polem wpisywania. Teksty są w 6 językach, jak reszta aplikacji.


## v1.0.55 (2026-09-10) — versionCode 56

**Naprawione: nieudane wysłanie zdjęcia albo głosówki znikało bez śladu.**

- Zwykła wiadomość tekstowa trafia do lokalnej kolejki: gdy padnie sieć, dostajesz czerwony dymek „nie wysłano" z przyciskiem ponawiania. Zdjęcie i głosówka szły dotąd obok tej kolejki — przy błędzie nie było ani dymka, ani komunikatu. Wiadomość po prostu znikała, a jedyny ślad zostawał w logu systemowym.
- Teraz zdjęcie i głosówka idą przez tę samą kolejkę co tekst: dymek pojawia się od razu (z miniaturą z pamięci telefonu albo z transkrypcją), a po nieudanej wysyłce dostaje „nie wysłano" i „ponów" — dokładnie tak jak wiadomość tekstowa. Ponawianie nie prosi o wybranie zdjęcia jeszcze raz.
- Wymaga jednorazowej migracji lokalnej bazy (v9 → v10). Nic nie ginie.


## v1.0.54 (2026-09-10) — versionCode 55

**Naprawione: komunikaty błędów były po polsku (albo po angielsku) bez względu na język aplikacji.**

- Część błędów — logowanie, tłumaczenie w trybie rozmowy, rozpoznawanie tekstu ze zdjęcia, Czytaj Pro — miała treść wpisaną na sztywno w kodzie. Przy aplikacji po angielsku, niemiecku, hiszpańsku, turecku albo chińsku dostawałeś polskie zdanie, np. „Błąd logowania".
- Wszystkie komunikaty błędów idą teraz przez te same zasoby co reszta interfejsu, więc są w 6 językach.
- Przy okazji przejrzeliśmy całą lokalizację: 482 teksty, żadnego braku w żadnym z 6 języków.

## v1.0.53 (2026-09-09) — versionCode 54

**Nowe: ekran skanera (OCR) ma własny wybór silnika.**

- Do tej pory skaner zawsze tłumaczył modelem Szybkim, bez względu na to, co wybrałeś na ekranie tłumacza. Teraz nad polem tekstu jest ten sam pasek wyboru: ⚡ Szybki, 🎯 Dokładny, ⚖️ Oba, ☁️ Online.
- Wybór jest wspólny z ekranem tłumacza — ustawiasz raz i działa w obu miejscach.
- Tryb ⚖️ Oba pokazuje dwa wyniki jeden pod drugim, każdy z własną etykietą (⚡ Szybki / 🎯 Dokładny).
- Gdy wagi wybranego modelu nie są pobrane, skaner pokazuje to samo okno pobierania co tłumacz — nie trzeba wracać do głównego ekranu.

**Naprawione: nagłówek wyniku skanera mówił „(Szybki)" w każdym języku.**

- Etykieta nad wynikiem miała na sztywno wpisane „Szybki" — także w wersjach angielskiej, niemieckiej, hiszpańskiej, tureckiej i chińskiej, gdzie zostawało polskie słowo. Teraz wpisuje się tam nazwa wybranego silnika.
- Przycisk **Tłumacz (X)** na ekranie tłumacza pokazuje teraz krótką nazwę silnika (Szybki / Dokładny / Oba / Online) zamiast długiego opisu z rozmiarem modelu.

## v1.0.52 (2026-09-09) — versionCode 53

**Naprawione: reklamy w ogóle się nie pojawiały na kontach darmowych.**

- Na kontach, dla których Google nie wymaga zgody (czyli poza EOG i Wielką Brytanią), formularz zgód potrafił odpowiedzieć „nie można wczytać reklam" — najczęściej na świeżo zatwierdzonym koncie AdMob bez skonfigurowanego „Privacy & messaging". SDK reklamowe czekało wtedy na zgodę, która nigdy nie nadejdzie, i baner zostawał pusty na zawsze. Teraz po 3 sekundach aplikacja inicjuje reklamy sama.
- W EOG i Wielkiej Brytanii bez zmian: bez Twojej zgody reklamy nie ruszają — nie łamiemy zasad Google.
- W logach jest teraz pełny stan zgód i kod błędu, gdy reklama się nie wczyta — łatwiej dojść, dlaczego baner jest pusty.

**Naprawione: dialog pobierania i przycisk tłumacza mówiły o modelu Szybkim bez względu na wybór.**

- Okno „Pobierz model" było sztywno napisane pod model Szybki (~440 MB). Przy pobieraniu Dokładnego (~1,1 GB) widziało się jednocześnie „Szybki" w tytule i „~1,1 GB" pod spodem — sprzeczne informacje.
- Tytuł, treść, etykieta pobierania, etykieta postępu i etykieta „model gotowy" są teraz zparametryzowane i same podstawiają właściwy model z jego rozmiarem.
- Przycisk **Tłumacz (X)** w głównym ekranie tłumacza mówi teraz o aktualnie wybranym silniku — Szybki, Dokładny, Oba albo Online — a nie zawsze „Szybki".

## v1.0.50 (2026-09-09) — versionCode 51

**Naprawione: konta PRO znowu się logują.**

- Cloud Function zapisywał pole `noAdsUntil` w profilu jako `Firestore Timestamp`, a Android oczekiwał zwykłej liczby (ms). Przy odczycie profilu konto wybuchało z błędem „Failed to convert a value of type com.google.firebase.Timestamp to long" i **nie dało się zalogować na konto PRO**. Teraz `UserProfile` akceptuje oba formaty, a nowe wpisy idą już jako `number`.

## v1.0.49 (2026-09-09) — versionCode 50

**Reklamy w wersji darmowej i pełna kontrola zgód.**

- Na ekranie tłumaczenia pojawia się **baner reklamowy Google**. Konta PRO 💎 (wykupione „Usuń reklamy" albo saldo portfela > 0) nie widzą reklam — dla nich nic się nie zmienia.
- W krajach EOG, Wielkiej Brytanii i Szwajcarii przy pierwszym uruchomieniu pokazujemy **formularz zgód Google**. Reklamy ładujemy dopiero po Twojej decyzji — ani chwili wcześniej.
- W profilu, w karcie **Prywatność**, dodałem **Ustawienia prywatności reklam** — możesz w każdej chwili zmienić albo wycofać zgody.
- Reklamy dobiera Google. Treść Twoich tłumaczeń nigdy do nich nie trafia, bo tłumaczenie dzieje się na Twoim urządzeniu.

## v1.0.48 (2026-09-08) — versionCode 49

**Status PRO jest teraz wyliczany, a nie zapisany na stałe.**

- Konto jest PRO tylko wtedy, gdy masz aktywny zakup „Usuń reklamy" (`noAdsUntil` w przyszłości) albo saldo portfela > 0.
- Po wygaśnięciu `noAdsUntil` przy pustym portfelu konto wraca do Free. Zapisane pole `plan` jest tylko informacyjne.

## v1.0.47 (2026-09-08) — versionCode 48

**Status konta po wykupieniu „Bez reklam".**

- Po zakupie **konto przechodzi na PRO 💎**. Nie tylko znika banner — odblokowują się też funkcje Pro (wcześniej część z nich wciąż widziała konto jako Free).
- W karcie statusu konta pokazujemy teraz **saldo portfela zawsze** — jeśli nie doładowałeś, zobaczysz **0.00**. „Bez reklam" i portfel to dwie osobne płatności.
- Pod spodem jest nowy **licznik czasu do wznowienia reklam** — od razu widać, ile dni zostało z wykupionego okresu.

## v1.0.46 (2026-09-08) — versionCode 47

**Usuń reklamy — jednorazowa przedpłata, bez abonamentu.**

- W karcie statusu konta, obok **Doładuj portfel**, pojawił się przycisk **Usuń reklamy**. Otwiera bezpieczną płatność jednorazową — wybierasz okres, a po zakupie banner reklamowy znika na ten czas.
- Do wyboru cztery pakiety: **$1 · 1 msc**, **$3 · 3 msc**, **$5 · 5 msc**, **$10 · 10 msc**.
- To **nie jest abonament** — płacisz raz, reklamy pozostają ukryte przez wybrany okres i nic nie odnawia się samo. **Konto przechodzi na status PRO 💎.**
- W karcie statusu konta widać teraz też **saldo portfela** — jeśli go nie doładowałeś, pokaże się **0.00** („Bez reklam" nie doładowuje portfela, to osobna płatność) oraz **licznik czasu do wznowienia reklam**, czyli ile dni zostało do końca wykupionego okresu.

## v1.0.45 (2026-09-08) — versionCode 46

**Naprawione: płatne tłumaczenia online i doładowanie portfela.**

- Serwer Verbigema przeniósł się do innego regionu (zgodnie z regionem projektu). Aplikacja wciąż wołała stary adres, przez co **tłumaczenie płatnymi modelami online kończyło się błędem** — teraz działa ponownie.
- **Doładowanie portfela z aplikacji w ogóle nie działało** — przycisk nie potrafił otworzyć płatności z tego samego powodu. Naprawione; po zakupie kredyty dopisują się automatycznie.

## v1.0.44 (2026-09-07) — versionCode 45

**Łatwiejsze korzystanie z własnego klucza OpenRouter.**

- Karta wyboru modelu online nazywa się teraz **Domyślny model tłumaczenia online** — od razu widać, że to ustawienie modelu używanego do tłumaczeń online.
- W karcie **Własny klucz OpenRouter** (darmowe modele na własny klucz) dodałem klikalny link do strony generowania klucza — i w opisie pod kartą, i w oknie pomocy. Po rejestracji w OpenRouter wystarczy jedno kliknięcie, by przejść do utworzenia klucza.

## v1.0.43 (2026-09-07) — versionCode 44

**Doładowanie konta prosto z aplikacji.**

- W karcie statusu konta pojawił się przycisk **Doładuj portfel**. Otwiera on bezpieczną płatność i po zakupie automatycznie dopisuje kredyty do Twojego portfela — bez przeładowywania ani ręcznego wpisywania czegokolwiek.
- Do wyboru trzy pakiety: Mały (300 kreditów), Średni (500 kreditów) i Duży (1000 kreditów).
- Saldo portfela odświeża się samo w tle zaraz po udanej płatności.

## v1.0.42 (2026-09-06) — versionCode 43

**Pomoc działa teraz także na nieaktywnych przyciskach.**

- Długie przytrzymanie przycisku pokazuje jego opis nawet wtedy, gdy przycisk jest wyszarzony — na przykład przy pustym polu tekstowym, przy silnikach zablokowanych dla darmowego konta czy przy braku zdjęcia do odczytu.

## v1.0.41 (2026-09-06) — versionCode 42

**Poprawki wyglądu po wprowadzeniu okien pomocy.**

- Przycisk „Rozumiem" w oknach pomocy jest teraz zawsze w wybranym języku interfejsu.
- Dolny pasek wrócił do 5 ikon: Tłumacz, Rozmowa, Czat, Kontakty, Profil.
- Ekran Tłumacza przewija się nad klawiaturę, gdy wpisujesz tekst.
- Ikony silników (dokładny, oba, online) mają opis nawet przy wyłączonym koncie.
- W profilu pojawiła się karta **O aplikacji** z numerem wersji oraz linkiem **Co nowego**.
- Ikona aplikacji zmieniła tło na kremowe, zgodne z motywem stron.

## v1.0.40 (2026-09-06) — versionCode 41

**Każda ikona w aplikacji ma teraz okno z wyjaśnieniem.**

- Kliknięcie ikony wykonuje jej zadanie, a długie przytrzymanie otwiera opis: czym jest, co robi i jak z niej korzystać.
- Dotyczy to ekranów Tłumacza, Rozmowy, Czatu, Kontaktów i Profilu — łącznie kilkudziesięciu nowych opisów w 6 językach.

## v1.0.39 (2026-09-05) — versionCode 40

**Największa paczka nowości do tej pory.**

- Czat 1:1 i Kontakty: zaproszenia, import zapisanych kontaktów (plik .vcf), wyszukiwanie znajomych.
- Weryfikacja numeru telefonu przez SMS działa w każdym regionie świata, a błędy są czytelne.
- Powiadomienia push (FCM) o nowych wiadomościach.
- Zdjęcia z podglądem na pełnym ekranie i odczyt tekstu ze zdjęć (OCR) w czacie.
- Własne kody QR do dodawania znajomych oraz skaner kodów.
- Polityka prywatności w 6 językach i przejrzysta informacja o uprawnieniach.
- Aplikacja działa w 6 językach: polskim, angielskim, hiszpańskim, chińskim, niemieckim i tureckim.
