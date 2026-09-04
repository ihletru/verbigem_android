package com.verbigem.app.data

import java.text.Normalizer

/**
 * Normalizacja tekstu do wyszukiwania — **musi być identyczna z serwerem**.
 *
 * Odpowiednik po stronie chmury: `normalizeForSearch()` w
 * `functions/src/searchIndex.ts`. Funkcja zapisuje `searchText` na wiadomości,
 * aplikacja normalizuje to, co wpisał użytkownik, i pyta Firestore o ten sam
 * łańcuch. Jeśli obie strony kiedykolwiek się rozjadą, wyszukiwanie zacznie
 * zwracać zero wyników i **nic w logach tego nie powie** — stąd ten komentarz.
 *
 * Dwa kroki, oba celowe:
 * 1. **NFD, potem usunięcie znaków łącznych.** Polacy piszą „jestes" równie
 *    często jak „jesteś" — bez tego jedna z tych postaci nigdy się nie znajdzie.
 * 2. **Małe litery.** Porównania łańcuchów w Firestore rozróżniają wielkość znaków.
 */
object MessageSearch {

    /** Najdłuższy fragment, jaki bierzemy pod uwagę — zgodny z serwerem. */
    private const val MAX_INDEXED_CHARS = 2000

    fun normalize(raw: String): String =
        stripMarks(Normalizer.normalize(raw, Normalizer.Form.NFD))
            .lowercase()
            .trim()
            .take(MAX_INDEXED_CHARS)

    /**
     * Usuwa znaki łączne (akcenty) zostawione przez dekompozycję NFD.
     *
     * Filtr po typie znaku zamiast wyrażenia regularnego: `\p{M}` w `java.util.regex`
     * działa, ale `Character.getType` jest jawny i nie zależy od tego, jak dana
     * wersja Androida kompiluje klasy Unicode.
     */
    private fun stripMarks(value: String): String {
        val nonSpacing = Character.NON_SPACING_MARK.toInt()
        val enclosing = Character.ENCLOSING_MARK.toInt()
        val combining = Character.COMBINING_SPACING_MARK.toInt()
        return value.filter { ch ->
            when (Character.getType(ch)) {
                nonSpacing, enclosing, combining -> false
                else -> true
            }
        }
    }

    /**
     * Sufiks do zapytań „od początku słowa".
     *
     * Firestore nie ma wyszukiwania pełnotekstowego. Da się tylko zapytać o zakres
     * łańcuchów, więc „kot" znajdzie „kot ma Alego", ale **nie** „Ala ma kota".
     * `\uF8FF` to ostatnia dozwolona w UTF-8 szesnastkowa wartość z prywatnego
     * obszaru użytku — jest większa niż jakikolwiek normalny znak, więc `q + F8FF`
     * jest górną granicą wszystkiego, co zaczyna się od `q`.
     */
    const val PREFIX_UPPER_BOUND_SUFFIX = "\uF8FF"
}
