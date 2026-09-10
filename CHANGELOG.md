# Verbigem Android — historia zmian

---

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
