package com.verbigem.app.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.verbigem.app.data.local.AppDatabase
import com.verbigem.app.data.model.TranslationHistory
import kotlinx.coroutines.tasks.await

/**
 * Startup synchronizer: pushes local changes to Firestore and pulls remote changes,
 * merging per-field with a LAST-WRITE-WINS rule (the newer `updatedAt` wins).
 *
 * What is synced:
 *  - user profile (users/{uid}) — plan, nickname, langs, etc.
 *  - translation history (users/{uid}/history/{syncId})
 *  - paid TTS config (app_config/tts)
 *
 * Must be called once at app start (after the user is signed in). If offline or no
 * user is signed in, it returns gracefully without throwing.
 */
class SyncManager(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val historyRepository = HistoryRepository(db.historyDao())
    private val proTtsRepository = ProTtsRepository(context)
    private val ttsConfigSync = TtsConfigSync(proTtsRepository)
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    suspend fun syncNow() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        try {
            syncProfile(uid)
            syncHistory(uid)
            ttsConfigSync.syncFromRemote()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
        }
    }

    private suspend fun syncProfile(uid: String) {
        // The profile document is kept up to date in real time by ProfileViewModel
        // (watchProfile + updateProfile). Nothing to merge here on startup beyond what
        // the UI already subscribes to; this hook exists so future profile fields can
        // be pulled/last-write-wins merged without changing the call site.
        firestore.collection("users").document(uid).get().await()
    }

    private suspend fun syncHistory(uid: String) {
        val localList = historyRepository.allHistoryValue()
        val remoteSnap = firestore.collection("users").document(uid)
            .collection("history").get().await()

        val remoteById = remoteSnap.documents.associateBy { it.id }
        val seenSyncIds = mutableSetOf<String>()

        // 1) Push local rows to remote (last-write-wins by updatedAt).
        for (local in localList) {
            val syncId = local.syncId.ifBlank {
                // Pre-migration row without syncId: assign one and persist.
                val withId = local.copy(syncId = java.util.UUID.randomUUID().toString())
                historyRepository.upsertFromRemote(withId)
                withId.syncId
            }
            seenSyncIds.add(syncId)
            val remoteDoc = remoteById[syncId]
            val remoteUpdated = remoteDoc?.getLong("updatedAt") ?: 0L
            if (remoteDoc == null || local.updatedAt >= remoteUpdated) {
                val map = mapOf(
                    "syncId" to syncId,
                    "sourceText" to local.sourceText,
                    "translatedText" to local.translatedText,
                    "sourceLang" to local.sourceLang,
                    "targetLang" to local.targetLang,
                    "timestamp" to local.timestamp,
                    "updatedAt" to local.updatedAt
                )
                firestore.collection("users").document(uid)
                    .collection("history").document(syncId)
                    .set(map, SetOptions.merge()).await()
            }
        }

        // 2) Pull remote rows that are newer than (or missing from) local.
        for ((syncId, remoteDoc) in remoteById) {
            val remoteUpdated = remoteDoc.getLong("updatedAt") ?: 0L
            val local = historyRepository.getLocalBySyncId(syncId)
            if (local == null || remoteUpdated > local.updatedAt) {
                val history = TranslationHistory(
                    syncId = syncId,
                    sourceText = remoteDoc.getString("sourceText") ?: "",
                    translatedText = remoteDoc.getString("translatedText") ?: "",
                    sourceLang = remoteDoc.getString("sourceLang") ?: "en",
                    targetLang = remoteDoc.getString("targetLang") ?: "pl",
                    timestamp = remoteDoc.getLong("timestamp") ?: remoteUpdated,
                    updatedAt = remoteUpdated
                )
                historyRepository.upsertFromRemote(history)
            }
        }

        // 3) Delete local rows whose syncId no longer exists remotely (only if remote
        //    collection is non-empty, to avoid wiping local data on a failed read).
        if (remoteById.isNotEmpty()) {
            for (local in localList) {
                val syncId = local.syncId
                if (syncId.isNotBlank() && !remoteById.containsKey(syncId)) {
                    historyRepository.deleteHistoryBySyncId(syncId)
                }
            }
        }
    }

    companion object {
        private const val TAG = "SyncManager"
    }
}
