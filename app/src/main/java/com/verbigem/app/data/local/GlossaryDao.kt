package com.verbigem.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface GlossaryDao {

    /** All terms for one language pair, alphabetically. */
    @Query(
        "SELECT * FROM glossary WHERE sourceLang = :source AND targetLang = :target " +
            "ORDER BY sourceTerm COLLATE NOCASE"
    )
    suspend fun forPair(source: String, target: String): List<GlossaryEntity>

    /** Everything, for a one-shot load into the in-memory cache. */
    @Query("SELECT * FROM glossary ORDER BY sourceTerm COLLATE NOCASE")
    suspend fun all(): List<GlossaryEntity>

    /** REPLACE (not IGNORE): re-adding an existing term must update the translation. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GlossaryEntity): Long

    @Query("DELETE FROM glossary WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM glossary WHERE sourceLang = :source AND targetLang = :target")
    suspend fun clearPair(source: String, target: String)

    @Query("SELECT COUNT(*) FROM glossary")
    suspend fun count(): Int
}
