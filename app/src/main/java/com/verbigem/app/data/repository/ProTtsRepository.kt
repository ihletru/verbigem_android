package com.verbigem.app.data.repository

import android.content.Context
import com.verbigem.app.data.local.AppDatabase
import com.verbigem.app.data.model.TtsConfig

/**
 * Local source of truth for the paid "Read Pro" TTS configuration.
 * The config is cached in Room and refreshed from Firestore on startup
 * (newer updatedAt wins). The admin webapp (future) is the canonical writer.
 */
class ProTtsRepository(context: Context) {

    private val dao = AppDatabase.getInstance(context).ttsConfigDao()

    suspend fun getConfig(): TtsConfig {
        return dao.get()?.toDomain() ?: TtsConfig()
    }

    /** Persist a config locally. Caller is responsible for last-write-wins checks. */
    suspend fun saveConfig(config: TtsConfig) {
        dao.upsert(config.toEntity())
    }
}
