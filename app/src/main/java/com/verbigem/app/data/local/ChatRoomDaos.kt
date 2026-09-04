package com.verbigem.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatTranslationDao {

    @Query("SELECT * FROM chat_translations WHERE msgId = :msgId AND targetLang = :lang")
    suspend fun get(msgId: String, lang: String): ChatTranslationEntity?

    @Upsert
    suspend fun upsert(entity: ChatTranslationEntity)

    /** Used by "translate into another language" — drops every cached variant. */
    @Query("DELETE FROM chat_translations WHERE msgId = :msgId")
    suspend fun deleteForMessage(msgId: String)

    /** Keeps the cache from growing forever; called with a 30-day cut-off. */
    @Query("DELETE FROM chat_translations WHERE updatedAt < :before")
    suspend fun prune(before: Long)
}

@Dao
interface ChatOutboxDao {

    @Query("SELECT * FROM chat_outbox WHERE chatId = :chatId ORDER BY createdAt ASC")
    fun watch(chatId: String): Flow<List<ChatOutboxEntity>>

    @Query("SELECT * FROM chat_outbox ORDER BY createdAt ASC")
    suspend fun all(): List<ChatOutboxEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ChatOutboxEntity)

    @Query("UPDATE chat_outbox SET status = :status, attempts = :attempts WHERE clientMsgId = :id")
    suspend fun updateStatus(id: String, status: String, attempts: Int)

    @Query("DELETE FROM chat_outbox WHERE clientMsgId = :id")
    suspend fun delete(id: String)
}

@Dao
interface ChatReadDao {

    @Query("SELECT * FROM chat_reads")
    fun watchAll(): Flow<List<ChatReadEntity>>

    @Upsert
    suspend fun upsert(entity: ChatReadEntity)
}

@Dao
interface ChatDeletedDao {

    @Query("SELECT * FROM chat_deleted_messages")
    fun watchAll(): Flow<List<ChatDeletedEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: ChatDeletedEntity)
}

@Dao
interface ChatHiddenDao {

    @Query("SELECT * FROM chat_hidden")
    fun watchAll(): Flow<List<ChatHiddenEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun hide(entity: ChatHiddenEntity)

    /** Brings a removed conversation back into the inbox. */
    @Query("DELETE FROM chat_hidden WHERE chatId = :chatId")
    suspend fun unhide(chatId: String)
}
