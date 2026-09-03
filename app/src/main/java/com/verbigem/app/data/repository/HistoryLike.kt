package com.verbigem.app.data.repository

import com.verbigem.app.data.local.PendingDeleteEntity
import com.verbigem.app.data.model.TranslationHistory

/**
 * Minimal surface that [SyncManager.syncCollection] needs from a history store.
 * Implemented by both [HistoryRepository] (Translator, `history` collection) and
 * [OcrHistoryRepository] (OCR, `ocr_history` collection) so the two sync in lockstep
 * with identical last-write-wins + tombstone rules.
 */
interface HistoryLike {
    /** Local rows changed since [since] (updatedAt > since) — the delta to push. */
    suspend fun getLocalSince(since: Long): List<TranslationHistory>
    suspend fun getPendingDeletes(): List<PendingDeleteEntity>
    suspend fun removePendingDelete(syncId: String, collection: String)
    suspend fun assignSyncIdIfMissing(item: TranslationHistory): TranslationHistory
    suspend fun getLocalBySyncId(syncId: String): TranslationHistory?
    suspend fun upsertFromRemote(history: TranslationHistory)
    suspend fun deleteBySyncId(syncId: String)
    /** Drop oldest rows beyond the local cap (200). Default no-op; DAOs override. */
    suspend fun pruneToLimit() {}
}
