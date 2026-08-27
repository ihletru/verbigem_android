package com.verbigem.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.verbigem.app.data.model.TranslationHistory

@Entity(tableName = "translation_history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceText: String,
    val translatedText: String,
    val sourceLang: String,
    val targetLang: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toDomain(): TranslationHistory = TranslationHistory(
        id = id,
        sourceText = sourceText,
        translatedText = translatedText,
        sourceLang = sourceLang,
        targetLang = targetLang,
        timestamp = timestamp
    )

    companion object {
        fun fromDomain(history: TranslationHistory): HistoryEntity = HistoryEntity(
            id = history.id,
            sourceText = history.sourceText,
            translatedText = history.translatedText,
            sourceLang = history.sourceLang,
            targetLang = history.targetLang,
            timestamp = history.timestamp
        )
    }
}
