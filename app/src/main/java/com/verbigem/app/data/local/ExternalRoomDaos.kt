package com.verbigem.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExternalContactDao {

    @Query("SELECT * FROM external_contacts ORDER BY lastUsedAt DESC, name ASC")
    fun watchAll(): Flow<List<ExternalContactEntity>>

    @Query("SELECT * FROM external_contacts WHERE phone = :phone")
    suspend fun get(phone: String): ExternalContactEntity?

    // `IGNORE`, not `REPLACE`: replacing would wipe the chosen language and the
    // creation time every time the address book is re-read. Refreshing the fields
    // that come from the address book is a separate, narrower update below.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: ExternalContactEntity): Long

    /** Overwrites ONLY the fields the address book owns. Never `lang`, never `createdAt`. */
    @Query(
        "UPDATE external_contacts SET name = :name, e164 = :e164, email = :email WHERE phone = :phone"
    )
    suspend fun updateFromAddressBook(phone: String, name: String, e164: String, email: String)

    @Query("UPDATE external_contacts SET lang = :lang WHERE phone = :phone")
    suspend fun setLang(phone: String, lang: String)

    @Query("UPDATE external_contacts SET lastUsedAt = :at WHERE phone = :phone")
    suspend fun touch(phone: String, at: Long)

    @Query("DELETE FROM external_contacts WHERE phone = :phone")
    suspend fun delete(phone: String)
}

@Dao
interface ExternalOutboxDao {

    @Query("SELECT * FROM external_outbox WHERE phone = :phone ORDER BY createdAt DESC")
    fun watchFor(phone: String): Flow<List<ExternalOutboxEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ExternalOutboxEntity): Long

    @Query("DELETE FROM external_outbox WHERE phone = :phone")
    suspend fun deleteFor(phone: String)

    /** Keeps history bounded; called with a 90-day cut-off from the repository. */
    @Query("DELETE FROM external_outbox WHERE createdAt < :before")
    suspend fun prune(before: Long)
}
