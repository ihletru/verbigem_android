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
 *
 * Since v10 the queue is not text-only: a photo or a voice message is queued the
 * same way, so a failed upload gets the same red "failed / retry" bubble instead of
 * disappearing into logcat (README §5.4). `type` decides what the flush has to do
 * before it can write the Firestore document:
 *
 *   text  — translate the hint and send. `text` is the message body.
 *   image — upload `localUri` to Storage first, run OCR on the device, then send
 *           with `attachmentUrl` + `ocrText`. `attachmentUrl` stays empty until the
 *           upload succeeds; the pending bubble shows `localUri` instead.
 *   audio — `transcript` is the body (STT runs live, there is no audio file to
 *           upload — see `sendVoice`).
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
    val attempts: Int = 0,
    /** text | image | audio */
    val type: String = "text",
    /** Remote https URL after upload. Empty while pending — see `localUri`. */
    val attachmentUrl: String = "",
    /**
     * `content://` URI picked from the gallery (type = "image" only).
     * Kept so a retry after a failed upload does not have to ask the user to
     * pick the photo again. The read grant dies with the process, so a retry
     * after a restart can still fail — that is a "failed" bubble, not a crash.
     */
    val localUri: String = "",
    /** Live STT result (type = "audio"). */
    val transcript: String = "",
    /** Text recognised in the photo on the sender's device (type = "image"). */
    val ocrText: String = ""
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

/**
 * Conversations the user removed from their inbox ("usuń rozmowę").
 *
 * Messages are append-only in Firestore (`allow update, delete: if false`), and
 * there is no Cloud Function to prune them yet, so deleting a conversation can
 * only ever be local: the row hides it from MY inbox while the other person keeps
 * the thread intact. That is the same honesty rule as "delete for me" on a single
 * message — see the contact card, which says so out loud.
 */
@Entity(tableName = "chat_hidden", primaryKeys = ["chatId"])
data class ChatHiddenEntity(
    val chatId: String = "",
    val hiddenAt: Long = System.currentTimeMillis()
)
