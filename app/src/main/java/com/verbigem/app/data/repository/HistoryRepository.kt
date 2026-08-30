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
) {

    val allHistory: Flow<List<TranslationHistory>> = historyDao.getAllHistory().map { entities ->
        entities.map { it.toDomain() }
    }

    /** One-shot snapshot of all local history (for the startup sync merge). */
    suspend fun allHistoryValue(): List<TranslationHistory> =
        historyDao.getAllHistory().first().map { it.toDomain() }

    suspend fun addHistory(sourceText: String, translatedText: String, sourceLang: String, targetLang: String) {
        historyDao.insert(
            HistoryEntity.fromDomain(
                TranslationHistory.create(sourceText, translatedText, sourceLang, targetLang)
            )
        )
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

    suspend fun deleteHistoryBySyncId(syncId: String) {
        historyDao.deleteBySyncId(syncId)
    }

    suspend fun clearHistory() {
        historyDao.clearAll()
        pendingDeleteDao.clearAll()
    }

    // ---- Used by Firestore sync (last-write-wins) ----

    suspend fun getLocalBySyncId(syncId: String): TranslationHistory? =
        historyDao.getBySyncId(syncId)?.toDomain()

    /** Insert/replace a row coming from Firestore, preserving local auto-id collisions. */
    suspend fun upsertFromRemote(history: TranslationHistory) {
        historyDao.upsertBySyncId(HistoryEntity.fromDomain(history))
    }

    // ---- Pending deletions (tombstones to push) ----

    suspend fun getPendingDeletes(): List<PendingDeleteEntity> = pendingDeleteDao.getAll()

    suspend fun removePendingDelete(syncId: String) {
        pendingDeleteDao.deleteBySyncId(syncId)
    }
}
