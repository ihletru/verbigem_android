package com.verbigem.app.data.repository

import android.content.Context
import com.verbigem.app.data.OutboundTarget
import com.verbigem.app.data.PhoneContact
import com.verbigem.app.data.local.AppDatabase
import com.verbigem.app.data.local.ExternalContactEntity
import com.verbigem.app.data.local.ExternalOutboxEntity
import kotlinx.coroutines.flow.Flow

/** How long hand-off history is kept. It is a log of things already gone; nothing to archive. */
private const val HISTORY_KEEP_MS = 90L * 24 * 60 * 60 * 1000

/**
 * One-way conversations with people who do not have Verbigem (3.6).
 *
 * Everything here is local. There is no counterpart to sync with: the other person
 * has no account, no device record, no idea that Verbigem exists. So this is less a
 * repository than a diary — who we wrote to, in what language, and through which
 * app it left the phone.
 */
class ExternalThreadRepository(context: Context) {

    private val contacts = AppDatabase.getInstance(context).externalContactDao()
    private val outbox = AppDatabase.getInstance(context).externalOutboxDao()

    fun watchContacts(): Flow<List<ExternalContactEntity>> = contacts.watchAll()

    fun watchHistory(phone: String): Flow<List<ExternalOutboxEntity>> = outbox.watchFor(phone)

    suspend fun contact(phone: String): ExternalContactEntity? = contacts.get(phone)

    /**
     * Remembers an address-book contact, refreshing only what the address book owns.
     *
     * Called on the way INTO the thread, not on a background sync: nothing about these
     * people is worth reading until the user actually opens one of them, and reading
     * the whole address book into Room would be a surprising thing to do with it.
     */
    suspend fun remember(contact: PhoneContact) {
        val existing = contacts.get(contact.phone)
        val e164 = contact.e164Candidates.firstOrNull().orEmpty()
        if (existing == null) {
            contacts.insert(
                ExternalContactEntity(
                    phone = contact.phone,
                    name = contact.name,
                    e164 = e164,
                    email = contact.email
                )
            )
        } else {
            contacts.updateFromAddressBook(contact.phone, contact.name, e164, contact.email)
        }
    }

    suspend fun setLang(phone: String, lang: String) = contacts.setLang(phone, lang)

    /**
     * Records a hand-off and marks the contact as used.
     *
     * Called **after** the channel has taken the message, never before: a row here is
     * a claim that something left the phone. Writing it first would put failed
     * hand-offs in the history, which is the one thing this table exists to be honest
     * about.
     *
     * @param channelId stable id of the channel (`whatsapp`, `sms`, …), not a
     *   translated label — a label would make old rows unreadable after a language
     *   change.
     */
    suspend fun recordHandOff(
        phone: String,
        channelId: String,
        originalText: String,
        translatedText: String,
        lang: String
    ) {
        outbox.insert(
            ExternalOutboxEntity(
                phone = phone,
                channel = channelId,
                originalText = originalText,
                translatedText = translatedText,
                lang = lang
            )
        )
        contacts.touch(phone, System.currentTimeMillis())
    }

    /** Removes a person and everything we ever sent them. */
    suspend fun forget(phone: String) {
        outbox.deleteFor(phone)
        contacts.delete(phone)
    }

    suspend fun pruneHistory() = outbox.prune(System.currentTimeMillis() - HISTORY_KEEP_MS)

    fun targetFor(entity: ExternalContactEntity): OutboundTarget = OutboundTarget(
        name = entity.name,
        e164 = entity.e164,
        loose = entity.phone,
        email = entity.email
    )
}
