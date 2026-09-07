package com.verbigem.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.verbigem.app.data.model.GlossaryEntry
import com.verbigem.app.data.model.LangCode

/**
 * Local termbase for the translation glossary (see [GlossaryEntry]).
 *
 * (sourceLang, targetLang, sourceTerm) is unique: the same source word cannot be
 * mapped to two different translations for the same pair. Room enforces it via a
 * UNIQUE index rather than a composite primary key, because the pair alone is not
 * unique and the term alone is not either.
 */
@Entity(
    tableName = "glossary",
    indices = [
        Index(value = ["sourceLang", "targetLang", "sourceTerm"], unique = true),
        Index(value = ["sourceLang", "targetLang"])
    ]
)
data class GlossaryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceLang: String = LangCode.EN.code,
    val targetLang: String = LangCode.PL.code,
    val sourceTerm: String = "",
    val targetTerm: String = "",
    val caseSensitive: Boolean = false,
    val createdAt: Long = 0
) {
    fun toDomain(): GlossaryEntry = GlossaryEntry(
        id = id,
        sourceLang = LangCode.fromCode(sourceLang),
        targetLang = LangCode.fromCode(targetLang),
        sourceTerm = sourceTerm,
        targetTerm = targetTerm,
        caseSensitive = caseSensitive,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(entry: GlossaryEntry): GlossaryEntity = GlossaryEntity(
            id = entry.id,
            sourceLang = entry.sourceLang.code,
            targetLang = entry.targetLang.code,
            sourceTerm = entry.sourceTerm.trim(),
            targetTerm = entry.targetTerm.trim(),
            caseSensitive = entry.caseSensitive,
            createdAt = entry.createdAt
        )
    }
}
