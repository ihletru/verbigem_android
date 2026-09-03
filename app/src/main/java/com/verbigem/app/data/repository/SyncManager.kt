package com.verbigem.app.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.verbigem.app.data.local.AppDatabase
import com.verbigem.app.data.local.PreferencesManager
import com.verbigem.app.data.model.TranslationHistory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

/**
 * Startup synchronizer: pushes local changes to Firestore and pulls remote changes.
 *
 * DELTA SYNC (no full-list transfer):
 *  - Each collection keeps a `lastSync` timestamp in DataStore (per collection: history / ocr_history).
 *  - PUSH: only local rows with `updatedAt > lastSync` are uploaded. Unchanged rows are skipped,
 *          so re-syncing a 10k-row history does not re-send megabytes of text.
 *  - PULL: `firestore.whereGreaterThan("updatedAt", lastSync)` — the server returns ONLY rows
 *          newer than the last sync (including tombstones, which carry their own updatedAt).
 *          No full list of ids is ever fetched.
 *  - After a successful sync, `lastSync` is advanced to the newest updatedAt seen (local + remote).
 *
 * What is synced:
 *  - user profile (users/{uid})
 *  - translation history (users/{uid}/history/{syncId}) — the Translator's list
 *  - OCR history (users/{uid}/ocr_history/{syncId}) — the OCR list (separate collection)
 *  - paid TTS config (app_config/tts)
 *
 * Deletions: a local delete records a tombstone in `pending_deletes` (with updatedAt = now) and
 * removes the row locally. On the next sync the tombstone is pushed as `{syncId, deleted:true,
 * updatedAt}`; because updatedAt > lastSync it propagates to other devices, which then delete
 * the row locally. `collection` on the tombstone routes it to history vs ocr_history.
 *
 * Must be called once at app start (after the user is signed in). If offline or no user is
 * signed in, it returns gracefully without throwing.
 */
class SyncManager(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val historyRepository = HistoryRepository(db.historyDao(), db.pendingDeleteDao())
    private val ocrHistoryRepository = OcrHistoryRepository(db.ocrHistoryDao(), db.pendingDeleteDao())
    private val preferencesManager = PreferencesManager(context)
    private val proTtsRepository = ProTtsRepository(context)
    private val ttsConfigSync = TtsConfigSync(proTtsRepository)
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    suspend fun syncNow(uid: String? = null) {
        val userId = uid ?: FirebaseAuth.getInstance().currentUser?.uid ?: return
        try {
            syncProfile(userId)
            syncCollection(userId, "history", historyRepository, preferencesManager.lastSyncHistoryFlow.first()) { ts ->
                preferencesManager.setLastSyncHistory(ts)
            }
            syncCollection(userId, "ocr_history", ocrHistoryRepository, preferencesManager.lastSyncOcrFlow.first()) { ts ->
                preferencesManager.setLastSyncOcr(ts)
            }
            ttsConfigSync.syncFromRemote()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
        }
    }

    private suspend fun syncProfile(uid: String) {
        firestore.collection("users").document(uid).get().await()
    }

    /**
     * Delta-syncs one subcollection. [lastSync] is the timestamp of the previous successful
     * sync (0 on first run). Only rows newer than it are transferred in either direction.
     * [onSynced] persists the new watermark after the sync.
     */
    private suspend fun syncCollection(
        uid: String,
        collection: String,
        repo: HistoryLike,
        lastSync: Long,
        onSynced: suspend (Long) -> Unit
    ) {
        val colRef = firestore.collection("users").document(uid).collection(collection)

        // Pull: only rows newer than lastSync (server-side filter). Returns text + tombstones
        // for anything changed since the last sync — never the full list.
        val remoteSnap = colRef
            .whereGreaterThan("updatedAt", lastSync)
            .orderBy("updatedAt", Query.Direction.ASCENDING)
            .get().await()

        val remoteById = remoteSnap.documents.associateBy { it.id }

        // 0) Push tombstones for locally-deleted rows. They carry updatedAt = deletion time,
        //    so if that is > lastSync they propagate; otherwise they were already pushed.
        val pending = repo.getPendingDeletes().filter {
            it.collection == collection && it.updatedAt > lastSync
        }
        for (pd in pending) {
            val tombstone = mapOf(
                "syncId" to pd.syncId,
                "deleted" to true,
                "updatedAt" to pd.updatedAt
            )
            colRef.document(pd.syncId).set(tombstone).await()
            repo.removePendingDelete(pd.syncId, collection)
        }

        // 1) Push local rows newer than lastSync (last-write-wins by updatedAt).
        var newWatermark = lastSync
        val localList = repo.getLocalSince(lastSync)
        for (local in localList) {
            val withId = repo.assignSyncIdIfMissing(local)
            val syncId = withId.syncId
            val remoteDoc = remoteById[syncId]
            val remoteUpdated = remoteDoc?.getLong("updatedAt") ?: 0L
            if (remoteDoc == null || withId.updatedAt >= remoteUpdated) {
                val map = mapOf(
                    "syncId" to syncId,
                    "sourceText" to withId.sourceText,
                    "translatedText" to withId.translatedText,
                    "sourceLang" to withId.sourceLang,
                    "targetLang" to withId.targetLang,
                    "timestamp" to withId.timestamp,
                    "updatedAt" to withId.updatedAt
                )
                colRef.document(syncId).set(map, SetOptions.merge()).await()
            }
            if (withId.updatedAt > newWatermark) newWatermark = withId.updatedAt
        }

        // 2) Pull remote rows newer than lastSync and apply locally (insert / update / tombstone).
        for ((syncId, remoteDoc) in remoteById) {
            val remoteUpdated = remoteDoc.getLong("updatedAt") ?: 0L
            if (remoteUpdated > newWatermark) newWatermark = remoteUpdated

            if (remoteDoc.getBoolean("deleted") == true) {
                repo.deleteBySyncId(syncId)
                continue
            }
            val local = repo.getLocalBySyncId(syncId)
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
                repo.upsertFromRemote(history)
            }
        }

        // Advance the watermark so the next sync only moves forward.
        onSynced(newWatermark)

        // Enforce the 200-entry local cap after applying remote rows.
        repo.pruneToLimit()
    }

    companion object {
        private const val TAG = "SyncManager"
    }
}
