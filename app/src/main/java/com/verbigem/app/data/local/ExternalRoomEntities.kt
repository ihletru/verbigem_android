package com.verbigem.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Someone from the address book who does NOT have Verbigem, kept on the device.
 *
 * WHY A TABLE AND NOT JUST THE ADDRESS BOOK: the address book is read-only and
 * shared with every other app. It cannot remember that we translate to Spanish for
 * Aunt Maria, or that we sent her something last Tuesday. Everything Verbigem knows
 * about a person who is not a user has to live here.
 *
 * Keyed by the loose phone spelling (`+595981123456`, digits and a leading plus) —
 * the same thing [PhoneContactsImporter] de-duplicates on, so a contact read twice
 * is still one row.
 *
 * Deliberately local-only. Nothing here is synced: it describes the device owner's
 * relationship with someone, and there is no "the other side" to agree with.
 */
@Entity(tableName = "external_contacts")
data class ExternalContactEntity(
    @PrimaryKey val phone: String = "",
    val name: String = "",
    /** Best E.164 guess, for handing off to WhatsApp/SMS. Blank when unparseable. */
    val e164: String = "",
    val email: String = "",
    /**
     * Language we translate to for this person. Blank = not chosen yet, and the
     * thread asks before translating.
     *
     * We cannot detect it: unlike a Verbigem user, an external contact has no
     * profile with a language in it. Guessing from the SIM country would be wrong
     * often enough to be annoying, so we ask once and remember.
     */
    val lang: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = 0
)

/**
 * One thing we handed off to another app.
 *
 * `status` exists and is always `handed_off`, which looks pointless until you read
 * it as a statement: **we do not know whether it was sent.** Once we hand a message
 * to WhatsApp, SMS or e-mail, it is gone from our reach — no callback, no receipt.
 * A record that could only ever say "handed off" is the honest model; pretending to
 * track delivery would be a lie in the UI.
 *
 * Called `external_outbox` and not `outbox_messages` (the name in the plan) because
 * `chat_outbox` already exists and means something completely different: messages
 * waiting to reach Firestore. Two tables a few characters apart, with unrelated
 * meanings, is how someone eventually flushes the wrong one.
 */
@Entity(
    tableName = "external_outbox",
    indices = [androidx.room.Index(value = ["phone"])]
)
data class ExternalOutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Which [ExternalContactEntity] this belongs to. */
    val phone: String = "",
    /** whatsapp | sms | email | telegram | system */
    val channel: String = "",
    /** What the user typed, in their own language. */
    val originalText: String = "",
    /** What we actually handed over. */
    val translatedText: String = "",
    /** Language of [translatedText]. */
    val lang: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /** Always `handed_off` — see the class comment. */
    val status: String = "handed_off"
)
