# Zdobycie 12 testerów do testu zamkniętego — zaproszenia i miejsca

Uzupełnienie `PLAY_INTERNAL_TESTING.md` (§4). Ten plik to **gotowe do wysłania teksty**
i lista miejsc, gdzie realnie zdobywa się testerów. Wymóg formalny (12 osób × 14 dni
nieprzerwanie, tylko test zamknięty) opisany jest w `PLAY_INTERNAL_TESTING.md`.

---

## 0. Punkt wyjścia — co już masz

⚠️ **Sprostowanie (2026-09-13).** Wcześniej zapisałem tu „9 kont = najcieplejsza pula".
To był **błąd we wniosku, nie w liczbie**: 9 to prawdziwy wynik zapytania do Firestore, ale
**4 z tych kont to konta testowe Milosza** — czyli realnych osób jest **5**. Nie wolno
budować planu na liczbie 9.

Odczyt z Firestore (`mini-verbigem`):

| Kolekcja | Liczba | Po odjęciu kont testowych |
|---|---|---|
| `usersPublic` | **9** | **5** |
| `chats` | **0** | — |

**Wszystkie 9 kont pochodzi z APK sideload, nie z Google Play.** Wynika to wprost
z chronologii: wydanie `v1.0.72` (pierwsze z pakietem `com.verbigem.app`) weszło
2026-09-12, a ścieżka testowa w Play nie ma jeszcze nikogo. Zanim więc ktokolwiek
zainstaluje wersję Play, **nie może istnieć żaden użytkownik z pakietu `com.verbigem.app`**.
Wszyscy dotychczasowi to `com.verbigem.app.sideload` — czyli apka z `mini.verbigem.com`.

⚠️ **Konsekwencja, którą łatwo przeoczyć:** te 5 osób **nie liczy się** jako testerzy.
Sideload i Play to **dwa różne pakiety**, więc żadna z nich nie ma jeszcze wersji testowej.
Każda musi przejść pełną ścieżkę opt-in i zainstalować `com.verbigem.app` z Play. Dobra
wiadomość: oba pakiety instalują się obok siebie, więc nie muszą niczego odinstalowywać.

Czego **nie da się** ustalić z danych: Firestore nie zapisuje, z jakiego pakietu ani wersji
przyszedł użytkownik. Pola w `usersPublic` to wyłącznie `nickname`, `photoURL`, `searchEmail`,
`searchNick`, `speakLangSource/Target`, `uiLang`, `uid`. Podział smaków trzeba znać z pamięci,
nie z bazy.

Rozkład `uiLang` wśród tych 9: `pl` 6, `es` 2, `en` 1 — czyli obraz zgodny z „krąg własny
plus znajomi z Paragwaju", a nie z ruchem organicznym.

**Realny plan, licząc uczciwie:** masz **5 realnych osób** (nie 12, nie 9). Brakuje
**7–10**, żeby mieć bufor. To dokładnie ta luka, którą domyka sekcja C (`r/AndroidTesting`).

---

## 1. Gdzie szukać — kolejność od najskuteczniejszych

### A. Twoi użytkownicy (najwyższy zwrot)

Osoby z listy kont, które przeszły rejestrację. Napisz do nich **pojedynczo**, nie masowo —
jeden człowiek, który odpowie, jest wart więcej niż dwudziestu, którzy zignorują mailing.

### B. Własna sieć — rodzina, znajomi, współpracownicy

Tester **nie musi znać polskiego**: apka ma 6 języków interfejsu (PL, EN, DE, ES, ZH, TR),
więc znajomi z Paragwaju są tak samo użyteczni jak ci z Polski. Wymagania: konto Google
+ telefon z Androidem **64-bitowym** (AAB jest budowany wyłącznie dla `arm64-v8a`).

### C. Społeczności wzajemnego testowania — **ZWERYFIKOWANE 2026-09-13**

**`r/AndroidTesting`** na Reddicie: <https://www.reddit.com/r/AndroidTesting/>

To jest właściwe miejsce i jest bardzo żywe. Sprawdzone bezpośrednio — na jednej stronie
`/new/` było **28 świeżych wątków**, praktycznie wszystkie o wymianie testerów. Przykłady
tytułów z tej jednej strony (żeby było jasne, jaki to typ społeczności):

- „[Test for Test] Looking for Android testers – will test back for 14 days"
- „Google Play rejected you too? Let's form a 14-day survival pact (Active T4T)!"
- „[Need Testers] 12 testers for ReplyPixel — 14-day closed test"
- „[TEST4TEST] 6 Android apps — AR travel guides, survival guide, game & more. Will test yours back!"
- „2 testers needed for 14 days for StudyBuddy app"

**Jak to działa:** publikujesz własny wątek w konwencji `[Test for Test]` / `T4T` — opisujesz
apkę, mówisz ile testerów potrzebujesz i deklarujesz, że **odwzajemnisz się** testowaniem
ich aplikacji. Wchodzisz też w istniejące wątki i oferujesz pomoc w zamian za to samo.
Zasada wzajemności jest tu normą, nie wyjątkiem.

**Zasady, żeby nie zostać zignorowanym:**

- W tytule zaznacz `[Test for Test]` albo `T4T` — bez tego wątek wygląda jak prośba bez
  oferty i ludzie go pomijają.
- Napisz, **ile testerów** potrzebujesz i że chodzi o **14 dni**.
- W treści podaj link do opt-in (ten z sekcji 4 w `PLAY_INTERNAL_TESTING.md`) i krótko opisz
  apkę — jedna linia, bez marketingowego lania wody.
- Odwzajemniaj się **szybko**. Licznik 14 dni działa w obie strony: jeśli Ty nie zainstalujesz
  ich apki, oni odinstalują Twoją i spadniesz poniżej progu.

⚠️ Uwaga na podział na wątki: `r/AndroidTesting` (pisownia z wielkimi literami) to ta aktywna
społeczność. Trafiają się też starsze/porzucone subreddity o podobnych nazwach — przed
publikacją sprawdź, czy najnowsze wątki są z **ostatnich dni**, a nie sprzed roku.

⚠️ Nie kupuj testerów z farm (patrz sekcja E) — ale uwaga: **wzajemne testowanie z żywymi
deweloperami to coś zupełnie innego** i jest w pełni zgodne z regulaminem Google.

### D. Nisze, w których tłumacz jest komuś naprawdę potrzebny

Najskuteczniejsza zmiana ramy: nie „pomóż mi spełnić wymóg Google", a „mam narzędzie,
które może Ci się przydać". Verbigem tłumaczy **offline**, więc trafia w konkretne potrzeby:

- grupy osób uczących się języków,
- grupy expatów i imigrantów (w Paragwaju: społeczności polskie, niemieckie, chińskie),
- grupy podróżnicze i backpackerskie (tłumacz bez internetu = realna wartość w terenie).

Ludzie testują chętniej to, z czego sami chcą korzystać.

### E. ⚠️ Czego nie robić

Nie kupuj testerów z farm. Pierwsza strona wyników Google na „12 testers Google Play" to
w większości firmy sprzedające „12 testerów w 24 h". Google odrzuca takie prośby, powołując
się wprost na **niewystarczające zaangażowanie testerów** — a stracone 14 dni liczy się
od nowa. Wymóg istnieje po to, żeby odsiać aplikacje bez odbiorców; farma go nie oszukuje,
tylko przenosi ryzyko na Ciebie.

---

## 2. Zaproszenie — wersja polska

**Temat:** Pomóż mi przetestować aplikację — 2 minuty, a potem 14 dni

> Cześć!
>
> Buduję Verbigem — tłumacza offline na Androida. Tłumaczy tekst i zdjęcia bez internetu,
> w 6 językach. Google wymaga, żeby przed publikacją w Sklepie Play aplikację testowało
> 12 osób przez 14 dni. Bardzo by mi pomogło, gdybyś był jedną z nich.
>
> Co trzeba zrobić (jednorazowo, około 2 minut):
>
> 1. Otwórz ten link **na telefonie z Androidem**: [LINK]
> 2. Zaloguj się kontem Google, na które dostałeś tę wiadomość.
> 3. Kliknij **„Zostań testerem"** — to najważniejszy krok, bez niego link nie zadziała.
> 4. Kliknij **„Pobierz ze Sklepu Play"** i zainstaluj.
>
> Potem przez 14 dni nie odinstalowuj aplikacji i zajrzyj do niej kilka razy. To wszystko.
>
> Ważne: nie znajdziesz jej przez wyszukiwanie w Sklepie Play — działa tylko ten link.
> Aplikacja jest darmowa, a z testu możesz zrezygnować w każdej chwili.
>
> Dzięki!
> Milosz

---

## 3. Zaproszenie — wersja hiszpańska

**Asunto:** ¿Me ayudás a probar una app? 2 minutos y después 14 días

> ¡Hola!
>
> Estoy desarrollando Verbigem, un traductor offline para Android. Traduce texto y fotos
> sin internet, en 6 idiomas. Google exige que 12 personas prueben la app durante 14 días
> antes de publicarla en Play Store. Me ayudaría mucho que fueras una de ellas.
>
> Qué hay que hacer (una sola vez, unos 2 minutos):
>
> 1. Abrí este enlace **en un teléfono Android**: [LINK]
> 2. Iniciá sesión con la cuenta de Google a la que te llegó este mensaje.
> 3. Tocá **„Ser tester"** — es el paso más importante, sin eso el enlace no funciona.
> 4. Tocá **„Descargar de Play Store"** e instalá.
>
> Después, durante 14 días no desinstales la app y abrila algunas veces. Eso es todo.
>
> Importante: no la vas a encontrar buscando en Play Store — solo funciona este enlace.
> La app es gratis y podés salir del programa cuando quieras.
>
> ¡Gracias!
> Milosz

---

## 4. Zaproszenie — wersja angielska

**Subject:** Could you test my app? 2 minutes, then 14 days

> Hi!
>
> I'm building Verbigem, an offline translator for Android. It translates text and photos
> without internet, in 6 languages. Google requires that 12 people test an app for 14 days
> before it can be published on Play Store. It would help me a lot if you were one of them.
>
> What to do (one time, about 2 minutes):
>
> 1. Open this link **on an Android phone**: [LINK]
> 2. Sign in with the Google account this message was sent to.
> 3. Tap **"Become a tester"** — this is the most important step; without it the link won't work.
> 4. Tap **"Download from Play Store"** and install.
>
> Then, for 14 days, don't uninstall the app and open it a few times. That's all.
>
> Important: you won't find it by searching Play Store — only this link works. The app is
> free, and you can leave the test at any time.
>
> Thanks!
> Milosz

---

## 5. Krótka wersja na WhatsApp

Długa wiadomość na WhatsAppie nie zostanie przeczytana. Do znajomych wyślij to:

> Cześć! Robię tłumacza offline na Androida i Google wymaga 12 testerów na 14 dni.
> Pomógłbyś? To 2 minuty: otwórz [LINK] na telefonie → „Zostań testerem" → „Pobierz
> z Play Store". Potem tylko nie odinstalowuj przez 2 tygodnie. Dzięki!

Wersja hiszpańska:

> ¡Hola! Estoy haciendo un traductor offline para Android y Google pide 12 testers por
> 14 días. ¿Me ayudarías? Son 2 minutos: abrí [LINK] en el teléfono → „Ser tester" →
> „Descargar de Play Store". Después solo no la desinstales por 2 semanas. ¡Gracias!

---

## 5b. Wpis na Reddita (r/AndroidTesting) — gotowy do wklejenia

Tytuł:

> [Test for Test] Offline translator, 6 languages — need 12 testers for 14 days, I'll test yours back

Treść:

> Hi everyone,
>
> I need 12 testers for the closed testing of my Android app and I'm happy to test yours in
> return for the full 14 days. Active T4T.
>
> **What it is:** Verbigem — an offline translator. It translates text and photos without an
> internet connection, in 6 languages (PL, EN, DE, ES, ZH, TR). Built for travel and for
> people who don't want to send everything they type to a server.
>
> **What I need:** 12 testers, 14 days, no uninstalling in between. You just need to open the
> app a few times during that period.
>
> **How to join:**
> 1. Open this link on an Android phone: [OPT-IN LINK]
> 2. Sign in with the Google account you added — tap "Become a tester"
> 3. Then "Download from Play Store"
>
> Note: it won't show up in Play Store search, only this link works.
>
> **Drop your app + link in the comments and I'll install it today.** I'll keep it for the
> full 14 days.
>
> Thanks!

⚠️ Zanim wkleisz: podmień `[OPT-IN LINK]` na link z konsoli, i **faktycznie odwzajemniaj**.
Ten subreddit pamięta, kto bierze i nie daje — jeden spalony wątek i nikt Ci nie zaufa
przy kolejnej aplikacji.

---

## 6. Tabela kontrolna — prowadź ją od pierwszego dnia

Bez tego zgubisz, kto na jakim etapie jest, a 14 dni liczy się od momentu, gdy **wszyscy
się zapiszą**. Zaproś **14–15 osób**, nie 12.

| # | Osoba | Kanał | Zaproszony | Kliknął „Zostań testerem" | Zainstalował | Aktywny (14 dni) |
|---|---|---|---|---|---|---|
| 1 |  |  |  |  |  |  |
| 2 |  |  |  |  |  |  |
| 3 |  |  |  |  |  |  |
| … |  |  |  |  |  |  |

Trzy najczęstsze powody, dla których licznik nie działa:

1. **Tester nie kliknął „Zostań testerem"** — sam link nic nie daje.
2. **Tester zalogował się innym kontem Google** niż to, które dodałeś do listy.
3. **Telefon 32-bitowy** — AAB wspiera wyłącznie `arm64-v8a`, taka instalacja się nie powiedzie.

---

## 7. Kolejność działań

Punkt wyjścia jest już policzony (sekcja 0): **5 realnych osób**, nie 9.

1. Napisz do tych 5 pojedynczo (sekcja 2/3/4 — w zależności od języka). Pamiętaj, że żadna
   z nich nie ma jeszcze pakietu Play — muszą przejść pełny opt-in.
2. Uruchom **test wewnętrzny** na 2–3 osobach od razu — waliduje build, nie ma wymogów,
   nie zużywa niczego z puli na test zamknięty.
3. Brakujące **7–10 osób** dobierz przez `r/AndroidTesting` (sekcja C). Licz się z tym, że
   T4T działa **1:1** — za każdego pozyskanego testera sam musisz przetestować czyjąś apkę
   przez pełne 14 dni. To realna praca, nie formalność.
4. Równolegle zbieraj resztę na **test zamknięty**. Zaproś 14–15 osób.
5. Gdy wszyscy się zapiszą — **zanotuj datę**. Od niej liczy się 14 dni.
