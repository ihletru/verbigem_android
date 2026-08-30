package com.verbigem.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.verbigem.app.data.model.TranslationHistory

@Entity(tableName = "translation_history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    // Stable cross-device key (UUID). Used as the Firestore document id so the
    // same translation syncs identically across devices (local auto-id would not).
    val syncId: String = "",
    val sourceText: String,
    val translatedText: String,
    val sourceLang: String,
    val targetLang: String,
    val timestamp: Long = System.currentTimeMillis(),
    // Last local write time; drives the last-write-wins merge on sync.
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): TranslationHistory = TranslationHistory(
        id = id,
        syncId = syncId,
        sourceText = sourceText,
        translatedText = translatedText,
        sourceLang = sourceLang,
        targetLang = targetLang,
        timestamp = timestamp,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(history: TranslationHistory): HistoryEntity = HistoryEntity(
            id = history.id,
            syncId = history.syncId,
            sourceText = history.sourceText,
            translatedText = history.translatedText,
            sourceLang = history.sourceLang,
            targetLang = history.targetLang,
            timestamp = history.timestamp,
            updatedAt = history.updatedAt
        )
    }
}
