package com.verbigem.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OcrHistoryDao {
    @Query("SELECT * FROM ocr_history ORDER BY timestamp DESC LIMIT 50")
    fun getAll(): Flow<List<OcrHistoryEntity>>

    @Query("SELECT * FROM ocr_history ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getPage(offset: Int, limit: Int): List<OcrHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: OcrHistoryEntity)

    @Update
    suspend fun update(item: OcrHistoryEntity)

    // Delta-sync source: rows changed after [since] (exclusive).
    @Query("SELECT * FROM ocr_history WHERE updatedAt > :since ORDER BY updatedAt ASC")
    suspend fun getSince(since: Long): List<OcrHistoryEntity>

    @Query("SELECT * FROM ocr_history WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): OcrHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBySyncId(item: OcrHistoryEntity)

    @Query("DELETE FROM ocr_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM ocr_history WHERE syncId = :syncId")
    suspend fun deleteBySyncId(syncId: String)

    @Query("DELETE FROM ocr_history")
    suspend fun clearAll()

    // Local cap: keep only the newest 200 entries; delete anything older.
    @Query("DELETE FROM ocr_history WHERE id NOT IN (SELECT id FROM ocr_history ORDER BY timestamp DESC LIMIT 200)")
    suspend fun pruneToLimit()
}
