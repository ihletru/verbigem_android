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
    val sourceText: String,
    val translatedText: String,
    val sourceLang: String,
    val targetLang: String,
    val timestamp: Long = System.currentTimeMillis()
)

sealed interface ModelDownloadState {
    data object Idle : ModelDownloadState
    data class Downloading(val progressPercent: Int, val bytesDownloaded: Long, val totalBytes: Long) : ModelDownloadState
    data object LoadingToMemory : ModelDownloadState
    data object Ready : ModelDownloadState
    data class Error(val message: String) : ModelDownloadState
}
