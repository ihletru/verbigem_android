package com.verbigem.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TtsConfigDao {
    @Query("SELECT * FROM tts_config WHERE id = 1 LIMIT 1")
    suspend fun get(): TtsConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TtsConfigEntity)
}
