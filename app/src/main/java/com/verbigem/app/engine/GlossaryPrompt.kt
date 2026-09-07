package com.verbigem.app.engine

import com.verbigem.app.data.model.GlossaryEntry

/**
 * Renders the glossary as the terminology block Hy-MT2 understands.
 *
 * The format is Tencent's documented one and was verified on-device against both
 * tiers (docs/SILNIK_PRO_RESEARCH.md §11):
 *
 *   Reference the following translations:
 *   `board` translates to `rada nadzorcza`
 *   `report` translates to `sprawozdanie kwartalne`
 *
 * Two rules matter and are both enforced here:
 *
 *  1. TERMS ARE ONLY INJECTED WHEN THEY ACTUALLY OCCUR IN THE INPUT. A static
 *     block would pay ~40 prompt tokens on every single translation, including the
 *     overwhelming majority where no glossary term appears.
 *  2. THE BLOCK IS CAPPED. n_ctx is 1024 tokens and a big termbase could eat a
 *     meaningful slice of it, so both the term count and the character budget are
 *     bounded. Past the cap the extra terms are dropped, not truncated — a
 *     half-written line would be worse than no line.
 */
object GlossaryPrompt {

    /** More than this and the block starts to dominate a short prompt. */
    private const val MAX_TERMS = 12

    /** Roughly 200 tokens worst case. Leaves >700 for the segment itself. */
    private const val MAX_BLOCK_CHARS = 600

    /**
     * Returns the terminology block including its trailing blank line, or an empty
     * string when nothing applies. Safe to prepend unconditionally.
     */
    fun build(entries: List<GlossaryEntry>, text: String): String {
        if (text.isBlank()) return ""

        val hits = ArrayList<GlossaryEntry>(MAX_TERMS)
        var used = 0

        for (entry in entries) {
            if (!entry.isUsable) continue
            if (hits.size >= MAX_TERMS) break
            if (!containsTerm(text, entry.sourceTerm, entry.caseSensitive)) continue

            val line = "`${clean(entry.sourceTerm)}` translates to `${clean(entry.targetTerm)}`"
            if (used + line.length + 1 > MAX_BLOCK_CHARS) break

            hits.add(entry)
            used += line.length + 1
        }

        if (hits.isEmpty()) return ""

        return buildString {
            appendLine("Reference the following translations:")
            for (e in hits) {
                appendLine("`${clean(e.sourceTerm)}` translates to `${clean(e.targetTerm)}`")
            }
            appendLine() // blank line: separates the block from the instruction
        }
    }

    /**
     * Word-boundary match for ASCII terms, plain substring for the rest.
     *
     * `\b` and `\w` are ASCII-only in Java's default regex mode, so a term that
     * starts or ends with a non-ASCII letter (ą, ü, 中) cannot be bounded with `\b`
     * — it would match "ą" as a non-word char and split the term in the wrong place.
     * Those fall back to a substring check: slightly more eager, never wrong in the
     * direction that matters (it can add a term, not silently drop one).
     */
    private fun containsTerm(text: String, term: String, caseSensitive: Boolean): Boolean {
        if (term.isBlank()) return false
        if (!term.first().isAsciiWord() || !term.last().isAsciiWord()) {
            return text.contains(term, ignoreCase = !caseSensitive)
        }
        val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
        return Regex("\\b${Regex.escape(term)}\\b", options).containsMatchIn(text)
    }

    private fun Char.isAsciiWord(): Boolean =
        this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' || this == '_'

    /** Backticks would terminate the `\`term\`` wrapper and corrupt the block. */
    private fun clean(s: String): String = s.replace("`", "'").trim()
}
