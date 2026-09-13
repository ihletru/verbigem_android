# AGENTS.md — tablica routingu dla agentów AI

> **Wczytaj ten plik na początku każdej sesji. Potem wczytaj TYLKO pliki wskazane dla danego zadania.**
>
> `README.md` to indeks dla ludzi. Ten plik to mapa dla maszyn.
> Dawniej cała wiedza była w jednym `README.md` (111 kB) i ładowała się w całości albo wcale — stąd ten podział.

---

## Reguły twarde (obowiązują zawsze, bez czytania czegokolwiek)

1. **Język: po polsku.** Dokumentacja, komentarze w kodzie, opisy commitów — Milosz nie czyta po angielsku ani chińsku.
2. **Nigdy nie przedstawiaj założenia jako diagnozy.** Brakuje ci faktu (ile ma kont, co widzi na ekranie, czy testował na produkcji)? Zapytaj wprost zamiast zakładać.
3. **Nie zgaduj wersji.** `versionCode` / `versionName` czytaj z `app/build.gradle.kts`. **Konwencja: `versionCode` = numer patch** (68 → `1.0.68`).
4. **Play nie pozwala powtórzyć `versionCode`.** Po odrzuceniu AAB od razu podbij numer — nie próbuj wgrać tego samego kodu.
5. **Po każdej zmianie większej niż kosmetycznej:** zaktualizuj właściwy plik w `docs/`, potem commit + `git push` (repo `android`; w `mini` tylko `src/`, `public/`, `vite.config.ts`).
6. **Nie czytaj `docs/README_ARCHIWUM.md`** — usunięty 2026-09-11, była to martwa kopia README z v1.0.39.

---

## Tablica routingu: zadanie → co wczytać

| Zadanie | Wczytaj |
|---|---|
| **Nowy ekran / funkcja produktu** | [`docs/funkcje.md`](docs/funkcje.md) + [`docs/ikony-i-pomoc.md`](docs/ikony-i-pomoc.md) |
| **Tekst UI, nowy string, nowy język, daty/liczby** | [`docs/jezyki-ui.md`](docs/jezyki-ui.md) |
| **Ikona, gest, okno pomocy, dolny pasek** | [`docs/ikony-i-pomoc.md`](docs/ikony-i-pomoc.md) |
| **Tłumaczenie, silniki, modele, pobieranie modelu** | [`docs/tlumaczenie.md`](docs/tlumaczenie.md) + [`docs/architektura.md`](docs/architektura.md) |
| **OpenRouter, Czytaj Pro, portfel, Paddle** | [`docs/tlumaczenie.md`](docs/tlumaczenie.md) |
| **Płatności: webhook, checkout, funkcje** | [`docs/tlumaczenie.md`](docs/tlumaczenie.md) + [`docs/functions.md`](docs/functions.md) |
| **Reklamy AdMob w aplikacji** | [`docs/reklamy.md`](docs/reklamy.md) |
| **Reklamy AdSense na stronie** | [`docs/reklamy.md`](docs/reklamy.md) + repo `verbigem-mini` |
| **Wydanie wersji, AAB, APK, auto-update** | [`docs/dystrybucja.md`](docs/dystrybucja.md) + [`docs/uruchomienie.md`](docs/uruchomienie.md) |
| **Gdzie publikować: własna strona vs Google Play vs App Store (iPhone)** | [`docs/dystrybucja-kanaly.md`](docs/dystrybucja-kanaly.md) |
| **Firebase, Firestore, App Check, push, Phone Auth** | [`docs/firebase.md`](docs/firebase.md) |
| **Cloud Functions, sekrety, deploy funkcji** | [`docs/functions.md`](docs/functions.md) |
| **Prywatność, dane użytkownika, usunięcie konta** | [`docs/prywatnosc.md`](docs/prywatnosc.md) |
| **Czat, kontakty, znajomi** | [`docs/czat-i-kontakty.md`](docs/czat-i-kontakty.md) + [`docs/funkcje.md`](docs/funkcje.md) |
| **Szyfrowanie czatu E2E, klucze, model zagrożeń** | [`docs/czat-e2e.md`](docs/czat-e2e.md) |
| **Build, Gradle, NDK, środowisko Windows** | [`docs/uruchomienie.md`](docs/uruchomienie.md) |
| **Play Console: opis, Data Safety, testowanie** | `PLAY_INTERNAL_TESTING.md`, `PLAY_TESTERZY_ZAPROSZENIA.md` (gotowe zaproszenia + gdzie szukać testerów), `PLAY_CONSOLE_FILLIN.md`, `PLAY_PUBLISHING_PLAN.md` |
| **Webapp `mini.verbigem.com`** | osobne repo `verbigem-mini` — tutaj tylko kontekst |

Wiedza długoterminowa projektu (decyzje, pułapki, ID) jest też w `.workbuddy-ai/memory/MEMORY.md` i dziennikach `.workbuddy-ai/memory/YYYY-MM-DD.md`.

---

## Zanim zaczniesz edytować

1. **Znajdź, nie zakładaj.** Zanim zmienisz zachowanie: `grep -rn` po `app/src/main/java` i upewnij się, że rozumiesz obecny stan. W tym projekcie wielokrotnie „naprawiano" coś, co już działało.
2. **Szukaj pułapki po słowie „⚠️".** Każdy plik w `docs/` oznacza nimi miejsca, gdzie już kiedyś polegliśmy. Przeczytaj je przed edycją.
3. **Sprawdź, czy to nie jest celowe.** Dziwne zachowanie bywa decyzją architektoniczną (np. landing celowo bez reklam, PRO nie widzi banerów). Szukaj frazy „celowo" / „decyzja".
4. **Weryfikuj po edycji.** Narzędzie do edycji w tej sesji potrafi zgłosić sukces bez zapisu — po każdej zmianie zrób `grep -c` albo `git diff --stat`.

---

## Czego nie robić

- ❌ Nie ładuj całego `docs/` „na wszelki wypadek" — 116 kB kontekstu. Weź 1–3 pliki z tabeli.
- ❌ Nie deployuj funkcji bez wylistowania nazw (`firebase deploy --only functions` podmienia wszystko).
- ❌ Nie nadpisuj istniejącej nazwy pliku APK na `/android/**` — tam jest cache `immutable`.
- ❌ Nie używaj `cmd.exe` / `powershell.exe` z poziomu Bash — są blokowane. Gradle przez wrapper Javy (patrz `docs/uruchomienie.md`).
- ❌ Nie wpisuj treści z backtickami bezpośrednio w polecenie Bash — powłoka je wycina. Zapisz do pliku i wczytaj.
- ❌ Nie mieszaj `/tmp` w Git Bash z `/tmp` w natywnym Pythonie — to różne katalogi.

---

## Jak aktualizować dokumentację

- Zmiana zachowania / architektury / decyzji → dopisz do **właściwego pliku w `docs/`**, nie do `README.md`.
- `README.md` zmienia się tylko wtedy, gdy: zmienia się wersja, dochodzi nowy plik w `docs/`, albo zmienia się quick start.
- Nowa pułapka, na której polegliśmy → dopisz do odpowiedniego pliku z nagłówkiem zaczynającym się od `⚠️`, żeby dało się ją znaleźć grepem.
- Nowy obszar bez własnego pliku → złóż go do najbliższego tematycznie, a gdy przekroczy ~15 kB, wydziel osobny plik i dopisz wiersz do tabeli wyżej.
