package com.verbigem.app.data.repository

import com.verbigem.app.data.local.HistoryDao
import com.verbigem.app.data.local.HistoryEntity
import com.verbigem.app.data.local.PendingDeleteDao
import com.verbigem.app.data.local.PendingDeleteEntity
import com.verbigem.app.data.model.TranslationHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class HistoryRepository(
    private val historyDao: HistoryDao,
    private val pendingDeleteDao: PendingDeleteDao
) : HistoryLike {

    val allHistory: Flow<List<TranslationHistory>> = historyDao.getAllHistory().map { entities ->
        entities.map { it.toDomain() }
    }

    /** Delta-sync source: local rows changed after [since]. */
    override suspend fun getLocalSince(since: Long): List<TranslationHistory> =
        historyDao.getSince(since).map { it.toDomain() }

    /**
     * Offset-based page for the infinite-scroll UI. Newest-first; [offset] is the
     * number of rows already loaded, [limit] the page size. Returns an empty list
     * when there are no more rows (UI stops requesting).
     */
    suspend fun getPage(offset: Int, limit: Int): List<TranslationHistory> =
        historyDao.getPage(offset, limit).map { it.toDomain() }

    suspend fun addHistory(sourceText: String, translatedText: String, sourceLang: String, targetLang: String) {
        historyDao.insert(
            HistoryEntity.fromDomain(
                TranslationHistory.create(sourceText, translatedText, sourceLang, targetLang)
            )
        )
        // Enforce the 200-entry cap locally: drop the oldest rows beyond the limit.
        historyDao.pruneToLimit()
    }

    /**
     * Deletes a history row locally (physical delete) and records its syncId in the
     * pending_deletes queue so the next sync can push a Firestore tombstone to other devices.
     * The local DB never keeps the full deleted row, so it does not bloat.
     */
    suspend fun deleteHistory(item: TranslationHistory) {
        val syncId = item.syncId.ifBlank { return } // nothing to propagate if no syncId
        historyDao.deleteById(item.id)
        pendingDeleteDao.insert(PendingDeleteEntity(syncId = syncId, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun deleteBySyncId(syncId: String) {
        historyDao.deleteBySyncId(syncId)
    }

    suspend fun clearHistory() {
        historyDao.clearAll()
        pendingDeleteDao.clearAll()
    }

    // ---- HistoryLike (shared sync surface) ----

    override suspend fun assignSyncIdIfMissing(item: TranslationHistory): TranslationHistory {
        if (item.syncId.isNotBlank()) return item
        val withId = item.copy(syncId = java.util.UUID.randomUUID().toString())
        upsertFromRemote(withId)
        return withId
    }

    override suspend fun removePendingDelete(syncId: String, collection: String) {
        pendingDeleteDao.deleteBySyncIdAndCollection(syncId, collection)
    }

    // ---- Used by Firestore sync (last-write-wins) ----

    override suspend fun getLocalBySyncId(syncId: String): TranslationHistory? =
        historyDao.getBySyncId(syncId)?.toDomain()

    /** Insert/replace a row coming from Firestore, preserving local auto-id collisions. */
    override suspend fun upsertFromRemote(history: TranslationHistory) {
        historyDao.upsertBySyncId(HistoryEntity.fromDomain(history))
    }

    // ---- Pending deletions (tombstones to push) ----

    override suspend fun getPendingDeletes(): List<PendingDeleteEntity> = pendingDeleteDao.getAll()

    override suspend fun pruneToLimit() = historyDao.pruneToLimit()
}
