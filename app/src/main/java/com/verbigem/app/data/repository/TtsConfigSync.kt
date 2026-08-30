package com.verbigem.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.verbigem.app.data.model.TtsConfig
import kotlinx.coroutines.tasks.await

/**
 * Syncs the "Read Pro" TTS configuration between Firestore and the local Room cache.
 * Firestore (seeded by the admin webapp) is canonical; on startup the local row is
 * replaced if the remote is newer (last-write-wins by updatedAt).
 */
class TtsConfigSync(
    private val repository: ProTtsRepository,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun syncFromRemote() {
        try {
            val snap = firestore.collection("app_config").document("tts").get().await()
            if (!snap.exists()) return
            val remote = snap.toObject(TtsConfig::class.java) ?: return
            val local = repository.getConfig()
            // Newer wins. If local is empty (updatedAt == 0) remote always applies.
            if (remote.updatedAt >= local.updatedAt) {
                repository.saveConfig(remote)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "TTS config sync failed", e)
        }
    }

    companion object {
        private const val TAG = "TtsConfigSync"
    }
}
