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

### Stan na 2026-09-13 — kanał zaproszeń jest GOTOWY

Milosz założył grupę z innego konta Google i przekazał działające linki. Poniżej stan
**zweryfikowany własnym odczytem**, nie przepisany z jego wiadomości:

| Element | Wartość | Weryfikacja |
|---|---|---|
| Grupa Google | `https://groups.google.com/g/verbigem-mini` | HTTP 200; strona publiczna renderuje się bez logowania |
| Nazwa / adres grupy | „Verbigem mini" / `verbigem-mini@googlegroups.com` | odczytane z HTML strony grupy |
| Opt-in (test) | `https://play.google.com/apps/testing/com.verbigem.app` | HTTP 200, przekierowuje na logowanie Google |
| Sklep (bezpośredni) | `https://play.google.com/store/apps/details?id=com.verbigem.app` | ⚠️ **HTTP 404 dla nie-testerów** — patrz sekcja 5f |
| Stara grupa `verbigem` | `groups.google.com/g/verbigem` | **nie używać** — zastąpiona przez `verbigem-mini` |

⚠️ **Grupa jest dołączalna dla obcych — to potwierdzone, nie założone.** Odczyt z publicznej
strony „O grupie" (bez logowania):

| Kto | Co może |
|---|---|
| Anyone on the web | can see group |
| Anyone on the web | **can join group** ✓ |
| Group members | can view members |
| Group members | can view conversations |
| Group members | can post |

To jest dokładnie ten warunek, od którego zależy powodzenie ogłoszenia na `r/AndroidTesting`
(sekcja 5d). Brak bramki „ask to join" — dołączenie jest bezpośrednie.

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
> 1. Zapisz się do grupy (jeden klik): https://groups.google.com/g/verbigem-mini
> 2. Otwórz ten link **na telefonie z Androidem** i kliknij **„Zostań testerem"**:
>    https://play.google.com/apps/testing/com.verbigem.app
> 3. Pobierz aplikację ze Sklepu Play (przycisk pojawi się na tej samej stronie).
>
> Kolejność ma znaczenie — najpierw grupa, potem „Zostań testerem". Inaczej Sklep powie,
> że nie masz dostępu.
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
> 1. Unite al grupo (un clic): https://groups.google.com/g/verbigem-mini
> 2. Abrí este enlace **en un teléfono Android** y tocá **„Ser tester"**:
>    https://play.google.com/apps/testing/com.verbigem.app
> 3. Descargá la app desde Play Store (el botón aparece en esa misma página).
>
> El orden importa — primero el grupo, después „Ser tester". Si no, Play dice que no
> tenés acceso.
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
> 1. Join the group (one click): https://groups.google.com/g/verbigem-mini
> 2. Open this link **on an Android phone** and tap **"Become a tester"**:
>    https://play.google.com/apps/testing/com.verbigem.app
> 3. Download the app from Play Store (the button appears on that same page).
>
> The order matters — group first, then "Become a tester". Otherwise Play will tell you
> that you don't have access.
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
> Pomógłbyś? To 2 minuty. Najpierw zapisz się do grupy (jeden klik):
> https://groups.google.com/g/verbigem-mini — a potem otwórz to na telefonie:
> https://play.google.com/apps/testing/com.verbigem.app i kliknij „Zostań testerem".
> Aplikacja zainstaluje się z tej samej strony. Potem tylko nie odinstalowuj przez
> 2 tygodnie. Dzięki!

Wersja hiszpańska:

> ¡Hola! Estoy haciendo un traductor offline para Android y Google pide 12 testers por
> 14 días. ¿Me ayudarías? Son 2 minutos. Primero unite al grupo (un clic):
> https://groups.google.com/g/verbigem-mini — y después abrí esto en el teléfono:
> https://play.google.com/apps/testing/com.verbigem.app y tocá „Ser tester". La app se
> instala desde esa misma página. Después solo no la desinstales por 2 semanas. ¡Gracias!

---

## 5b. Wpis na Reddita (r/AndroidTesting) — wersja z grupą Google

Przeredagowane na wzór ogłoszenia innego dewelopera z tego subreddita (schemat:
krótki opis → prośba → trzy kroki `HOW TO JOIN` → ostrzeżenie o kolejności → osobista nota).
Zmiany względem tamtego wzoru: **nie ma zdania „there are no ads"** — Twoja darmowa wersja
ma baner AdMob i testerzy i tak go zobaczą. Ukrycie tego kosztowałoby Cię zaufanie przy
pierwszej opinii.

Tytuł:

> [Test for Test] Offline translator, 6 languages — need 12 testers for 14 days, I'll test yours back

Treść:

> **Need Testers**
>
> Verbigem is an offline translator for Android. It translates text and photos **without an
> internet connection**, in six languages (Polish, English, German, Spanish, Chinese,
> Turkish). It's built for the situations where you can't count on the network — travelling,
> abroad, on a bad connection — and for people who don't want everything they type sent to a
> server.
>
> The app is now entering Google Play Closed Testing, and I need **at least 12 testers** willing
> to stay opted in for **14 consecutive days**. Actually using it and telling me what feels off
> would be hugely appreciated.
>
> The free version shows a small banner ad; PRO removes it. There's nothing to unlock — it's a
> tool, not a game.
>
> HOW TO JOIN:
>
> 1. Join the Google Group:
>
> https://groups.google.com/g/verbigem-mini
>
> 2. Opt into the Closed Test:
>
> https://play.google.com/apps/testing/com.verbigem.app
>
> 3. Download the app — the Play Store page opens once you're opted in:
>
> https://play.google.com/store/apps/details?id=com.verbigem.app
>
> IMPORTANT: please join the Google Group BEFORE using the opt-in link, otherwise Google Play
> may tell you that you don't have access.
>
> If you can help, please stay opted in for the full 14 days. You don't need to use it
> constantly, although any feedback is extremely useful — especially if a translation comes out
> wrong, or if something in the interface is confusing.
>
> This is an app I'm building on my own, and having people actually test what I've been working
> on means a lot. If you find a bug, have a suggestion, or just want to tell me what you think,
> leave a comment or message me.
>
> **If you're a developer:** drop your app and opt-in link in the comments and I'll install it
> and keep it for the full 14 days. Active T4T.
>
> Thanks to anyone willing to give it a shot.
>
> — Milosz

---

## 5c. Wersja polska — dla własnego kręgu (Facebook, WhatsApp, e-mail)

> **Szukam testerów**
>
> Verbigem to tłumacz offline na Androida. Tłumaczy tekst i zdjęcia **bez internetu**,
> w sześciu językach (polski, angielski, niemiecki, hiszpański, chiński, turecki). Powstał
> dla sytuacji, w których nie można liczyć na sieć — w podróży, za granicą, przy słabym
> zasięgu — i dla ludzi, którzy nie chcą, żeby wszystko, co wpisują, leciało na serwer.
>
> Aplikacja wchodzi właśnie do zamkniętych testów w Google Play i potrzebuję **co najmniej
> 12 testerów**, którzy zostaną zapisani przez **14 dni bez przerwy**. Naprawdę przydałoby
> się, gdybyście jej użyli i powiedzieli, co zgrzyta.
>
> W darmowej wersji jest mały baner reklamowy; PRO go usuwa. Nie ma nic do odblokowywania —
> to narzędzie, nie gra.
>
> JAK DOŁĄCZYĆ:
>
> 1. Zapisz się do grupy Google: https://groups.google.com/g/verbigem-mini
> 2. Włącz test: https://play.google.com/apps/testing/com.verbigem.app
> 3. Pobierz aplikację: https://play.google.com/store/apps/details?id=com.verbigem.app
>
> WAŻNE: najpierw zapisz się do grupy, a dopiero potem użyj linku do testu — inaczej Sklep
> Play powie, że nie masz dostępu.
>
> Jeśli możesz pomóc, zostań zapisany przez pełne 14 dni. Nie musisz używać jej bez przerwy,
> ale każda uwaga jest bezcenna — zwłaszcza gdy tłumaczenie wyjdzie źle albo coś w interfejsie
> jest niejasne.
>
> Buduję tę aplikację sam i to, że ktoś naprawdę ją sprawdzi, znaczy dla mnie dużo. Błąd,
> sugestia, albo po prostu opinia — napisz w komentarzu albo w wiadomości.
>
> Dzięki, że dajesz jej szansę.
>
> — Milosz

---

## 5d. Grupa Google jako lista testerów — co musi być ustawione

Wybór grupy dyskusyjnej zamiast listy adresów e-mail ma jedną wielką zaletę: **nie dopisujesz
ludzi ręcznie** — kto dołączy do grupy, ten jest testerem. Ma też dwa warunki, od których
zależy, czy cała instrukcja w ogłoszeniu w ogóle zadziała.

**1. Grupa musi być dostępna dla obcych. ✓ SPRAWDZONE 2026-09-13.**

Wcześniej nie dało się tego ustalić anonimowo (strona grupy bez logowania zwracała
„Content unavailable"). Teraz się udało — dla grupy `verbigem-mini` warunek **jest spełniony**:

| Kto | Co może |
|---|---|
| Anyone on the web | can see group |
| Anyone on the web | **can join group** |
| Group members | can view members |
| Group members | can view conversations |
| Group members | can post |

Dołączenie jest **bezpośrednie** — nie ma bramki „ask to join", więc człowiek z Reddita
klika i jest w grupie. To jest dokładnie ten warunek, bez którego całe ogłoszenie spaliłoby
się na pierwszym kroku.

**Jak to sprawdzić ponownie** (np. po zmianie grupy): wylogowany albo w oknie incognito
otwórz `https://groups.google.com/g/<nazwa-grupy>/about` i poszukaj wiersza
„Anyone on the web → can join group". Jeśli zamiast tego jest „tylko zaproszeni" albo
ograniczenie do Twojej domeny — nikt z zewnątrz nie dołączy i trzeba przejść na listę
adresów e-mail (sekcje 2–5 albo 5e).

**2. Grupa musi być dodana jako lista testerów w Play Console.**
**Testuj i publikuj → Test zamknięty → Testerzy → zakładka „Grupy dyskusyjne Google"** →
wklej `verbigem-mini@googlegroups.com`. Samo istnienie grupy nic nie daje — dopóki nie jest
podpięta do ścieżki, Play nie wie, że jej członkowie mają dostęp.

⚠️ **To jedyny krok, którego nie dało się zweryfikować z zewnątrz** — wymaga zalogowania do
Play Console. Sprawdź, czy `verbigem-mini@googlegroups.com` faktycznie widnieje na liście
testerów ścieżki. Jeśli nie, ludzie będą dołączać do grupy i **nic z tego nie wyniknie** —
dokładnie ten scenariusz, w którym testerzy piszą „nie mam dostępu", a przyczyna leży po
Twojej stronie.

**3. Kolejność jest obowiązkowa.** Oficjalna dokumentacja Google:

> „W przypadku testów zamkniętych z wykorzystaniem Grupy dyskusyjnej Google użytkownicy muszą
> dołączyć do grupy, zanim wezmą udział w teście."

Dlatego ostrzeżenie `IMPORTANT` w ogłoszeniu nie jest ozdobnikiem — to najczęstszy powód
komunikatu „nie masz dostępu" u testerów, którzy kliknęli link opt-in jako pierwsi.

⚠️ Zanim wkleisz ogłoszenie: nie zmieniaj nic, jeśli linki się zgadzają — ale **sprawdź, czy
wszystkie trzy adresy działają** (grupa, opt-in, strona w Sklepie). Wpis z martwym linkiem
na `r/AndroidTesting` zbierze same negatywne komentarze. I **faktycznie odwzajemniaj**:
ten subreddit pamięta, kto bierze i nie daje.

---

## 5e. Grupa Google NIE jest obowiązkowa — i co robić, gdy jej założenie się blokuje

To najważniejsza rzecz w tym pliku, jeśli utkniesz. **Play Console przyjmuje testerów na dwa
sposoby**, a grupa dyskusyjna jest tylko jednym z nich:

| Sposób | Gdzie | Kiedy się opłaca |
|---|---|---|
| **Lista adresów e-mail** (wklejana albo CSV) | Play Console, wewnątrz aplikacji | Mała, stała grupa; zero zależności od Google Groups |
| **Grupa dyskusyjna Google** | `groups.google.com` + podpięcie w Play Console | Wymiana testerów (T4T) — ludzie dochodzą sami, bez edycji listy |

Czyli: **jeśli nie możesz założyć grupy, nie utknąłeś.** Ścieżka z listą e-mail działa w 100%
wewnątrz Play Console i nie wymaga przechodzenia przez żadną CAPTCHA Google Groups.

### Ścieżka „lista e-mail" (dokładne kliknięcia)

1. Play Console → **Testuj i publikuj** → **Test zamknięty** → wybierz ścieżkę
2. zakładka **Testerzy** → **Utwórz listę adresów e-mail** → nadaj nazwę
3. wklej adresy (jeden na linię) albo wgraj CSV → **Zapisz zmiany**
4. ⚠️ **teraz zaznacz checkbox obok utworzonej listy** i kliknij **Zapisz** na dole strony

⚠️ **Krok 3 i krok 4 to dwie osobne operacje.** Wgranie adresów *tworzy* listę, ale dopiero
zaznaczenie checkboxa *podpina* ją do ścieżki. Bez kroku 4 lista jest niewidoczna dla wydania
i testerzy dostają „nie masz dostępu" — mimo że lista wygląda na poprawną.

⚠️ **Testerzy muszą mieć konto Google** (Gmail albo Google Workspace). Adres na innym
dostawcy nie przejdzie opt-inu — nie zbieraj adresów „na outlooku".

### Gdy zakładanie grupy zapętla CAPTCHA

Objawy z 2026-09-13: CAPTCHA wraca mimo poprawnego rozwiązywania, czerwony pasek
„Please input your API key!" (to **wtyczka przeglądarki**, nie Google — patrz pamięć
użytkownika). Co zostało **wykluczone eksperymentem**:

| Test | Wynik | Wniosek |
|---|---|---|
| Hotspot z telefonu (inny operator, inny ASN) | CAPTCHA dalej | **To NIE jest blokada IP** — Starlink jest czysty |
| Nowa przeglądarka Brave (czysty profil, zero wtyczek) | CAPTCHA dalej | **To NIE jest wtyczka ani profil** |
| Chrome | CAPTCHA dalej | — |

Skoro sieć i przeglądarka odpadają, **przyczyna siedzi w koncie Google**. Konsekwencja
praktyczna: **VPN nie pomoże** — i to jest już ustalone eksperymentem, a nie domysłem.
Nie tracić na to czasu.

Najbardziej prawdopodobna przyczyna i kolejność działań:

1. **Wiele kont Google zalogowanych jednocześnie.** Zakładanie grupy w tym stanie jest
   znanym źródłem zapętlonych weryfikacji. Wyloguj się ze **wszystkich** kont Google, zaloguj
   **dokładnie jedno**, wejdź bezpośrednio na `groups.google.com` (nie przez Play Console).
2. **Brak zweryfikowanego numeru telefonu na koncie** — najskuteczniejsza dźwignia obniżenia
   CAPTCHY dla kont o niskim zaufaniu. Dodaj numer w ustawieniach konta Google.
3. **Przestać próbować na 24–48 h.** Każda nieudana próba podnosi ocenę ryzyka; dobijanie
   się do formularza pogarsza sprawę, nie poprawia.
4. **Założyć grupę z innego, starszego konta Google** i dodać się jako właściciel. Grupa nie
   musi należeć do tego samego konta co konto deweloperskie w Play — liczy się tylko to, że
   jest podpięta jako lista testerów. ✓ **ZADZIAŁAŁO 2026-09-13** — tak powstała grupa
   `verbigem-mini`, po tym jak założenie jej z konta deweloperskiego zapętlało CAPTCHĘ.
   To potwierdza, że problem był **na poziomie konta**, a nie sieci.

⚠️ **ROZWIĄZANE (2026-09-13).** Grupa `verbigem-mini` została założona z innego konta
i działa. Stara grupa `groups.google.com/g/verbigem` jest **nieaktualna** — nie używać jej
w żadnym ogłoszeniu ani nie podpinać jej w Play Console. Wszystkie zaproszenia w tym pliku
wskazują już na `verbigem-mini`.

Wniosek na przyszłość, jeśli problem wróci: nie ma sensu zmieniać sieci ani przeglądarki
(oba wykluczone eksperymentem) — **zmień konto, z którego zakładasz grupę**.

---

## 5f. ⚠️ Link do Sklepu zwraca 404 dla kogoś, kto nie jest jeszcze testerem

Zmierzone 2026-09-13, tym samym zapytaniem, z tego samego IP i z tym samym User-Agentem:

| Adres | HTTP |
|---|---|
| `play.google.com/store/apps/details?id=com.whatsapp` (kontrola) | **200** |
| `play.google.com/store/apps/details?id=com.verbigem.app` | **404** |
| `play.google.com/apps/testing/com.verbigem.app` | **200** (przekierowanie na logowanie Google) |

Kontrola na WhatsAppie wyklucza blokadę bota — **404 jest prawdziwe**. Karta aplikacji
w Sklepie Play nie istnieje publicznie, dopóki aplikacja nie jest opublikowana w produkcji.
Widzą ją wyłącznie osoby **już zapisane** do testu (po opt-inie).

**Konsekwencja dla ogłoszenia:** jeśli wkleisz goły link do Sklepu jako trzeci krok, **każdy,
kto kliknie go przed opt-inem, zobaczy „Not Found"** — i napisze w komentarzu, że link jest
zepsuty. To dokładnie ten rodzaj wpisu, który psuje odbiór ogłoszenia na `r/AndroidTesting`.

Dlatego:

- krok „pobierz aplikację" opisuj jako **„przycisk na tej samej stronie"** — strona opt-inu
  po zapisie pokazuje przycisk pobierania. Nie jako osobny link do Sklepu.
- link do Sklepu zostawiaj tylko jako uzupełnienie, z wyraźnym „otwiera się po zapisie do testu"
  (tak jest już zredagowany krok 3 w sekcji 5b).
- ⚠️ **Sprawdzenie „czy link działa" zalogowany na koncie deweloperskim nic nie dowodzi** —
  Tobie zadziała zawsze, bo jesteś zapisany do testu. Testuj wylogowany albo z konta, które
  nigdy nie było testerem.

Milosz przekazał ten adres jako „link do zaproszenia z Androida" — to link skopiowany
z Play Store na telefonie, który **już jest zapisany do testu**. Dla obcego ten sam adres
zwraca 404.

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
2. **Tester zalogował się innym kontem Google** niż to, którym dołączył do grupy (albo niż to,
   które dodałeś do listy e-mail). Konto Google musi być **to samo** na wszystkich etapach.
3. **Telefon 32-bitowy** — AAB wspiera wyłącznie `arm64-v8a`, taka instalacja się nie powiedzie.
4. **Grupa nie jest podpięta w Play Console** (sekcja 5d pkt 2) — testerzy dołączają do grupy,
   a Play o niczym nie wie. Objaw jest identyczny jak przy punkcie 2, więc sprawdź to
   **w pierwszej kolejności**, bo to jedyna przyczyna leżąca po Twojej stronie.

---

## 7. Kolejność działań

Punkt wyjścia jest już policzony (sekcja 0): **5 realnych osób**, nie 9. Kanał zaproszeń
jest gotowy (sekcja 0 — grupa `verbigem-mini` działa i jest dołączalna dla obcych).

0. **Zanim ktokolwiek dołączy do grupy:** podepnij `verbigem-mini@googlegroups.com`
   w Play Console (Test zamknięty → Testerzy → „Grupy dyskusyjne Google"). To jedyny krok,
   którego nie dało się sprawdzić z zewnątrz, a bez niego cała reszta nie zadziała.
1. Napisz do tych 5 pojedynczo (sekcja 2/3/4 — w zależności od języka). Pamiętaj, że żadna
   z nich nie ma jeszcze pakietu Play — muszą przejść pełny opt-in.
2. Uruchom **test wewnętrzny** na 2–3 osobach od razu — waliduje build, nie ma wymogów,
   nie zużywa niczego z puli na test zamknięty.
3. Brakujące **7–10 osób** dobierz przez `r/AndroidTesting` (sekcja C). Licz się z tym, że
   T4T działa **1:1** — za każdego pozyskanego testera sam musisz przetestować czyjąś apkę
   przez pełne 14 dni. To realna praca, nie formalność.
4. Równolegle zbieraj resztę na **test zamknięty**. Zaproś 14–15 osób.
5. Gdy wszyscy się zapiszą — **zanotuj datę**. Od niej liczy się 14 dni.
