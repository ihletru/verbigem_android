package com.verbigem.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM translation_history ORDER BY timestamp DESC LIMIT 50")
    fun getAllHistory(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM translation_history ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getPage(offset: Int, limit: Int): List<HistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: HistoryEntity)

    // Used by Firestore sync: insert-or-replace by primary key (id), preserving
    // remote rows whose local id may differ is handled via syncId upsert below.
    @Update
    suspend fun update(item: HistoryEntity)

    // Delta-sync source: rows changed after [since] (exclusive).
    @Query("SELECT * FROM translation_history WHERE updatedAt > :since ORDER BY updatedAt ASC")
    suspend fun getSince(since: Long): List<HistoryEntity>

    @Query("SELECT * FROM translation_history WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): HistoryEntity?

    // Insert a synced remote row, replacing any local row with the same syncId.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBySyncId(item: HistoryEntity)

    @Query("DELETE FROM translation_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM translation_history WHERE syncId = :syncId")
    suspend fun deleteBySyncId(syncId: String)

    @Query("DELETE FROM translation_history")
    suspend fun clearAll()

    // Local cap: keep only the newest 200 entries; delete anything older. Called after an
    // insert (and after a sync) so the table never grows past HISTORY_MAX_ENTRIES.
    @Query("DELETE FROM translation_history WHERE id NOT IN (SELECT id FROM translation_history ORDER BY timestamp DESC LIMIT 200)")
    suspend fun pruneToLimit()
}
