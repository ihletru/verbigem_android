package com.verbigem.app.data.repository

import com.verbigem.app.data.local.OcrHistoryDao
import com.verbigem.app.data.local.OcrHistoryEntity
import com.verbigem.app.data.local.PendingDeleteDao
import com.verbigem.app.data.local.PendingDeleteEntity
import com.verbigem.app.data.model.TranslationHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Local store for OCR translation history, synced to Firestore `ocr_history`.
 * Separate table/collection from the Translator's history, but the same
 * last-write-wins sync semantics (syncId key + tombstone-driven deletes).
 */
class OcrHistoryRepository(
    private val dao: OcrHistoryDao,
    private val pendingDeleteDao: PendingDeleteDao
) : HistoryLike {

    private fun toDomain(e: OcrHistoryEntity) = TranslationHistory(
        id = e.id,
        syncId = e.syncId,
        sourceText = e.sourceText,
        translatedText = e.translatedText,
        sourceLang = e.sourceLang,
        targetLang = e.targetLang,
        timestamp = e.timestamp,
        updatedAt = e.updatedAt
    )

    private fun fromDomain(h: TranslationHistory) = OcrHistoryEntity(
        id = h.id,
        syncId = h.syncId,
        sourceText = h.sourceText,
        translatedText = h.translatedText,
        sourceLang = h.sourceLang,
        targetLang = h.targetLang,
        timestamp = h.timestamp,
        updatedAt = h.updatedAt
    )

    val allHistory: Flow<List<TranslationHistory>> = dao.getAll().map { list ->
        list.map { e -> toDomain(e) }
    }

    /** Delta-sync source: local rows changed after [since]. */
    override suspend fun getLocalSince(since: Long): List<TranslationHistory> =
        dao.getSince(since).map { toDomain(it) }

    /**
     * Offset-based page for the infinite-scroll UI. Newest-first; [offset] is the
     * number of rows already loaded, [limit] the page size. Returns an empty list
     * when there are no more rows (UI stops requesting).
     */
    suspend fun getPage(offset: Int, limit: Int): List<TranslationHistory> =
        dao.getPage(offset, limit).map { toDomain(it) }

    suspend fun addHistory(
        sourceText: String,
        translatedText: String,
        sourceLang: String,
        targetLang: String
    ): TranslationHistory {
        val created = TranslationHistory.create(sourceText, translatedText, sourceLang, targetLang)
        dao.insert(fromDomain(created))
        // Enforce the 200-entry cap locally: drop the oldest rows beyond the limit.
        dao.pruneToLimit()
        return created
    }

    suspend fun deleteHistory(item: TranslationHistory): String {
        val syncId = item.syncId.ifBlank { return "" }
        dao.deleteById(item.id)
        return syncId
    }

    override suspend fun deleteBySyncId(syncId: String) {
        dao.deleteBySyncId(syncId)
    }

    suspend fun clearAll() {
        dao.clearAll()
    }

    // ---- Used by Firestore sync (last-write-wins) ----

    override suspend fun getLocalBySyncId(syncId: String): TranslationHistory? =
        dao.getBySyncId(syncId)?.let { toDomain(it) }

    override suspend fun upsertFromRemote(history: TranslationHistory) {
        dao.upsertBySyncId(fromDomain(history))
    }

    override suspend fun assignSyncIdIfMissing(item: TranslationHistory): TranslationHistory {
        if (item.syncId.isNotBlank()) return item
        val withId = item.copy(syncId = java.util.UUID.randomUUID().toString())
        dao.upsertBySyncId(fromDomain(withId))
        return withId
    }

    override suspend fun getPendingDeletes(): List<PendingDeleteEntity> {
        // OCR tombstones share the same pending_deletes queue; the sync routes by `collection`.
        return pendingDeleteDao.getAll()
    }

    override suspend fun pruneToLimit() = dao.pruneToLimit()

    override suspend fun removePendingDelete(syncId: String, collection: String) {
        pendingDeleteDao.deleteBySyncIdAndCollection(syncId, collection)
    }
}
