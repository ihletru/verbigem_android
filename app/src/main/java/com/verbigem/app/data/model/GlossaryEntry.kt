package com.verbigem.app.data.model

import com.verbigem.app.data.local.GlossaryEntity

/**
 * One user-defined term: when translating [sourceLang] -> [targetLang], the word
 * [sourceTerm] must come out as [targetTerm].
 *
 * Why this is the only quality lever left (docs/SILNIK_PRO_RESEARCH.md §10b, §11):
 *  - the sampler is not (greedy is deterministic and no worse than sampling),
 *  - the prompt wording is not (current == Tencent official, measured),
 *  - the model tier IS, but it costs download size and per-token time.
 * Terminology injection costs ~40 prompt tokens per call and measurably works on
 * BOTH tiers — including the default 1.25-bit engine.
 *
 * Entries are keyed by the language PAIR, not by target language alone: "board"
 * is an English word, and the German->Polish glossary is a different list than
 * the English->Polish one.
 */
data class GlossaryEntry(
    val id: Long = 0,
    val sourceLang: LangCode,
    val targetLang: LangCode,
    val sourceTerm: String,
    val targetTerm: String,
    /**
     * When false (default) "Board" and "BOARD" match the term "board". When true
     * only the exact spelling matches. Acronyms are the reason this exists: "IT"
     * must not fire on the English word "it".
     */
    val caseSensitive: Boolean = false,
    val createdAt: Long = 0
) {
    val isUsable: Boolean
        get() = sourceTerm.isNotBlank() && targetTerm.isNotBlank() && sourceLang != targetLang

    fun toEntity(): GlossaryEntity = GlossaryEntity.fromDomain(this)
}
