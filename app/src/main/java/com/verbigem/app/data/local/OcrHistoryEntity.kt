package com.verbigem.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.verbigem.app.data.model.TranslationHistory

/**
 * Local row for an OCR translation. Kept in its OWN table (separate from the
 * Translator's [HistoryEntity]) so the two lists never mix on screen, but it
 * carries a [syncId]/[updatedAt] pair and IS synced to Firestore into the
 * `ocr_history` subcollection (mirroring the Translator's `history` sync).
 */
@Entity(tableName = "ocr_history")
data class OcrHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    // Stable cross-device key (UUID), used as the Firestore document id (same row
    // syncs identically across devices; local auto-id would not).
    val syncId: String = "",
    val sourceText: String,
    val translatedText: String,
    val sourceLang: String,
    val targetLang: String,
    val timestamp: Long = System.currentTimeMillis(),
    // Last local write time; drives the last-write-wins merge on sync.
    val updatedAt: Long = System.currentTimeMillis()
)
