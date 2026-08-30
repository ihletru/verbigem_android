package com.verbigem.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

data class ChatMessage(
    val id: String = "",
    val authorId: String = "",
    val sourceLang: String = "pl",
    val text: String = "",
    val translatedText: String = "",
    @ServerTimestamp
    val createdAt: Timestamp? = null
)

data class Friendship(
    val id: String = "",
    val uidA: String = "",
    val uidB: String = "",
    val status: String = "pending",
    val requestedBy: String = "",
    val nicknameA: String = "",
    val nicknameB: String = "",
    @ServerTimestamp
    val createdAt: Timestamp? = null
) {
    val isAccepted: Boolean
        get() = status == "accepted"
}

data class TranslationHistory(
    val id: Long = 0,
    // Stable cross-device key (UUID); empty only for pre-sync local rows.
    val syncId: String = "",
    val sourceText: String,
    val translatedText: String,
    val sourceLang: String,
    val targetLang: String,
    val timestamp: Long = System.currentTimeMillis(),
    // Last local write time (ms). Drives last-write-wins merge during Firestore sync.
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        /** Creates a new history entry with a fresh UUID syncId and current timestamps. */
        fun create(
            sourceText: String,
            translatedText: String,
            sourceLang: String,
            targetLang: String
        ): TranslationHistory {
            val now = System.currentTimeMillis()
            return TranslationHistory(
                syncId = java.util.UUID.randomUUID().toString(),
                sourceText = sourceText,
                translatedText = translatedText,
                sourceLang = sourceLang,
                targetLang = targetLang,
                timestamp = now,
                updatedAt = now
            )
        }
    }
}

sealed interface ModelDownloadState {
    data object Idle : ModelDownloadState
    data class Downloading(val progressPercent: Int, val bytesDownloaded: Long, val totalBytes: Long) : ModelDownloadState
    data object LoadingToMemory : ModelDownloadState
    data object Ready : ModelDownloadState
    data class Error(val message: String) : ModelDownloadState
}
