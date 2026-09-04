package com.verbigem.app.data.local

import androidx.room.Entity

/**
 * Cache of translations produced ON THE RECEIVING DEVICE (decision D1).
 *
 * Without it every recomposition of the thread would re-run Hy-MT2 over the same
 * message — at ~3-4 tok/s that is seconds of model time per scroll. Keyed by
 * (msgId, targetLang) so re-translating a message into another language (phase 1.6)
 * simply adds/replaces a row instead of invalidating the whole cache.
 */
@Entity(tableName = "chat_translations", primaryKeys = ["msgId", "targetLang"])
data class ChatTranslationEntity(
    val msgId: String = "",
    val targetLang: String = "",
    val chatId: String = "",
    val translatedText: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Outgoing messages that have not reached Firestore yet.
 *
 * The UX contract: tapping Send is instant. The row is written locally, the bubble
 * shows a "sending" state, and [com.verbigem.app.ui.screens.chat.ChatThreadViewModel]
 * flushes the queue when the network is available. `clientMsgId` doubles as the
 * Firestore document id, so a flush that runs twice (network flap, reconnect while
 * the thread is open) can never create a duplicate message.
 */
@Entity(tableName = "chat_outbox", primaryKeys = ["clientMsgId"])
data class ChatOutboxEntity(
    val clientMsgId: String = "",
    val chatId: String = "",
    val text: String = "",
    val sourceLang: String = "pl",
    val createdAt: Long = System.currentTimeMillis(),
    /** pending | failed — successfully sent rows are deleted from the table. */
    val status: String = "pending",
    val attempts: Int = 0
)

/**
 * Per-conversation "I have read up to here" watermark, kept locally.
 *
 * The inbox uses it to draw the unread dot. Deliberately local: it is a per-device
 * notion, and reading it from Room costs nothing, whereas a Firestore listener per
 * conversation would cost one listener per row. The *other* side's read state is
 * realtime and lives in `chats/{chatId}/readReceipts/{uid}`.
 */
@Entity(tableName = "chat_reads", primaryKeys = ["chatId"])
data class ChatReadEntity(
    val chatId: String = "",
    val lastReadAt: Long = 0
)

/**
 * Messages hidden on this device only ("delete for me").
 *
 * Deleting a message for everyone would need `allow update/delete` on
 * `chats/{chatId}/messages`, which is deliberately locked down. Local tombstones
 * keep the security model intact while still letting a user tidy up their own view.
 */
@Entity(tableName = "chat_deleted_messages", primaryKeys = ["msgId"])
data class ChatDeletedEntity(
    val msgId: String = "",
    val deletedAt: Long = System.currentTimeMillis()
)
