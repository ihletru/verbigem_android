package com.verbigem.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.verbigem.app.data.model.TtsConfig

/**
 * Local cache of the paid "Read Pro" (OpenRouter TTS) configuration.
 * Only a single row (id = 1) is stored; the admin webapp (future) and Firestore
 * push the canonical config which the app merges on startup (newer wins).
 */
@Entity(tableName = "tts_config")
data class TtsConfigEntity(
    @PrimaryKey
    val id: Int = 1,
    val apiKey: String = "",
    // OpenRouter TTS model id used for non-Chinese languages (e.g. google/gemini-3.1-flash-tts-preview).
    val defaultModelId: String = "google/gemini-3.1-flash-tts-preview",
    // Separate model for Chinese (e.g. fish-audio/s2.1-pro) — required by product spec.
    val chineseModelId: String = "fish-audio/s2.1-pro",
    // Comma-separated list of OpenRouter voice ids to pick from, per language group below.
    val defaultVoice: String = "default",
    val chineseVoice: String = "default",
    val updatedAt: Long = 0
) {
    fun toDomain(): TtsConfig = TtsConfig(
        apiKey = apiKey,
        defaultModelId = defaultModelId,
        chineseModelId = chineseModelId,
        defaultVoice = defaultVoice,
        chineseVoice = chineseVoice,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(cfg: TtsConfig): TtsConfigEntity = TtsConfigEntity(
            id = 1,
            apiKey = cfg.apiKey,
            defaultModelId = cfg.defaultModelId,
            chineseModelId = cfg.chineseModelId,
            defaultVoice = cfg.defaultVoice,
            chineseVoice = cfg.chineseVoice,
            updatedAt = cfg.updatedAt
        )
    }
}
