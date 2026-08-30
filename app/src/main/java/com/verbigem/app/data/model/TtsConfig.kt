package com.verbigem.app.data.model

import com.verbigem.app.data.local.TtsConfigEntity

/**
 * Configuration for the paid "Read Pro" feature (OpenRouter TTS).
 * Stored locally in Room and synced to Firestore; admin webapp (future) is the
 * canonical source. Older configs are overwritten on startup (newer updatedAt wins).
 */
data class TtsConfig(
    val apiKey: String = "",
    val defaultModelId: String = "google/gemini-3.1-flash-tts-preview",
    val chineseModelId: String = "fish-audio/s2.1-pro",
    val defaultVoice: String = "default",
    val chineseVoice: String = "default",
    val updatedAt: Long = 0
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()

    /** OpenRouter model id for the given target language (Chinese gets its own model). */
    fun modelIdFor(lang: LangCode): String =
        if (lang == LangCode.ZH) chineseModelId else defaultModelId

    /** OpenRouter voice for the given target language. */
    fun voiceFor(lang: LangCode): String =
        if (lang == LangCode.ZH) chineseVoice else defaultVoice

    fun toEntity(): TtsConfigEntity = TtsConfigEntity.fromDomain(this)
}
