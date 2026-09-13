# Zdobycie 12 testerów do testu zamkniętego — zaproszenia i miejsca

Uzupełnienie `PLAY_INTERNAL_TESTING.md` (§4). Ten plik to **gotowe do wysłania teksty**
i lista miejsc, gdzie realnie zdobywa się testerów. Wymóg formalny (12 osób × 14 dni
nieprzerwanie, tylko test zamknięty) opisany jest w `PLAY_INTERNAL_TESTING.md`.

---

## 0. Punkt wyjścia — co już masz

Odczyt z Firestore (`mini-verbigem`, 2026-09-12):

| Kolekcja | Liczba |
|---|---|
| `usersPublic` | **9** |
| `chats` | **0** |

Dwa wnioski, oba ważne dla planu:

1. **Masz 9 zarejestrowanych kont.** To najcieplejsza pula, jaka istnieje — ci ludzie
   sami ściągnęli apkę z `mini.verbigem.com` i założyli konto. Nie musisz ich przekonywać,
   że aplikacja istnieje.
2. **Zero czatów** oznacza, że część z tych 9 kont to prawdopodobnie Twoje własne testy,
   a nie obcy użytkownicy. Realna liczba osób do policzenia, nie do przyjęcia na wiarę.

**Pierwszy krok:** Firebase Console → **Authentication → Users** (projekt `mini-verbigem`)
→ przejrzyj listę i policz, ile adresów należy do prawdziwych ludzi. Adresy z
`@gmail.com` z sensowną nazwą to kandydaci; własne aliasy i konta testowe odpadają.

---

## 1. Gdzie szukać — kolejność od najskuteczniejszych

### A. Twoi użytkownicy (najwyższy zwrot)

Osoby z listy kont, które przeszły rejestrację. Napisz do nich **pojedynczo**, nie masowo —
jeden człowiek, który odpowie, jest wart więcej niż dwudziestu, którzy zignorują mailing.

### B. Własna sieć — rodzina, znajomi, współpracownicy

Tester **nie musi znać polskiego**: apka ma 6 języków interfejsu (PL, EN, DE, ES, ZH, TR),
więc znajomi z Paragwaju są tak samo użyteczni jak ci z Polski. Wymagania: konto Google
+ telefon z Androidem **64-bitowym** (AAB jest budowany wyłącznie dla `arm64-v8a`).

### C. Społeczności wzajemnego testowania

Reddit i serwery Discord dla twórców Androida, gdzie działa zasada „zainstaluję Twoją,
zainstalujesz moją". **⚠️ Nie mogłem zweryfikować konkretnych adresów** — Reddit blokuje
automatyczny dostęp, a pierwsza strona wyników Google jest dziś zajęta przez komercyjne
serwisy sprzedające testerów. Wyszukaj sam, wpisując na Reddicie:

- `closed testing 12 testers`
- `closed testing exchange`
- `Android closed testing`

Zwróć uwagę, czy w wątku są **żywi deweloperzy odpowiadający sobie nawzajem**, czy tylko
reklamy firm. To jedyny wiarygodny test.

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

1. Policz prawdziwych ludzi na liście kont (Firebase Console → Authentication → Users).
2. Napisz do nich pojedynczo (sekcja 2/3/4 — w zależności od języka).
3. Uruchom **test wewnętrzny** na 2–3 osobach od razu — waliduje build, nie ma wymogów,
   nie zużywa niczego z puli na test zamknięty.
4. Równolegle zbieraj resztę na **test zamknięty**. Zaproś 14–15 osób.
5. Gdy wszyscy się zapiszą — **zanotuj datę**. Od niej liczy się 14 dni.
