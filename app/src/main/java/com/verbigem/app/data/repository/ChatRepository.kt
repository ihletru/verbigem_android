package com.verbigem.app.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.verbigem.app.data.MessageSearch
import com.verbigem.app.data.model.ChatMessage
import com.verbigem.app.data.model.ChatSummary
import com.verbigem.app.data.model.ContactSettings
import com.verbigem.app.data.model.Friendship
import com.verbigem.app.data.model.SenderTranslation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/** One message that matched a search. */
data class MessageHit(
    val chatId: String,
    val messageId: String,
    val authorId: String,
    val text: String,
    val createdAt: Long
)

class ChatRepository {

    private val firestore = FirebaseFirestore.getInstance()

    /** Page size for the thread (newest page is live, older pages are loaded on scroll). */
    val pageSize: Long = 50

    fun getChatId(uidA: String, uidB: String): String {
        return listOf(uidA, uidB).sorted().joinToString("__")
    }

    /**
     * Members are derived from `chatId` (`sortedUidA__sortedUidB`) instead of being
     * passed in, so every device writes the identical array. Firebase uids are
     * `[A-Za-z0-9]`, so "__" is an unambiguous separator.
     */
    private fun membersFromChatId(chatId: String): List<String> = chatId.split("__")

    // ------------------------------------------------------------------ thread

    private fun messagesQuery(chatId: String) =
        firestore.collection("chats").document(chatId).collection("messages")
            .orderBy("createdAt", Query.Direction.DESCENDING)

    /**
     * Live listener on the NEWEST page only. Older pages are fetched once by
     * [loadOlderMessages] — keeping the realtime listener small is what keeps a
     * long thread from costing a document read per message on every keystroke
     * elsewhere in the app.
     */
    fun watchLatestMessages(chatId: String): Flow<List<ChatMessage>> = callbackFlow {
        val listener = messagesQuery(chatId).limit(pageSize)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    android.util.Log.w("ChatRepository", "Latest messages listener failed", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snap?.documents?.mapNotNull { doc ->
                    doc.toObject(ChatMessage::class.java)?.copy(id = doc.id)
                } ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    // ----------------------------------------------------------------- search

    /**
     * Finds messages whose text starts with [rawQuery], across the given chats.
     *
     * **One query per chat, not a collection-group query.** A collection-group query
     * on `messages` would sweep every conversation in the database, and the security
     * rules cannot scope it — access is decided by a `get()` on the parent chat, which
     * a group query cannot express. Walking the user's OWN chat list keeps the read
     * inside documents they are already a member of.
     *
     * **Prefix only.** Firestore has no full-text search; the range trick below
     * (`>= q`, `< q + F8FF`) matches the beginning of the indexed string, so "kot"
     * finds "kot ma Alego" but not "Ala ma kota". Stating that in the UI beats
     * letting the user discover it.
     *
     * No `orderBy`: adding one to a range query would force a composite index. Sorting
     * the handful of hits on the device is free and needs no deploy coordination.
     *
     * A failure in one chat is logged and skipped — a thread the user cannot read
     * must not erase results from the ones they can.
     */
    suspend fun searchMessages(
        chatIds: List<String>,
        rawQuery: String,
        limitPerChat: Long = 20
    ): List<MessageHit> {
        val query = MessageSearch.normalize(rawQuery)
        if (query.isBlank() || chatIds.isEmpty()) return emptyList()

        val upper = query + MessageSearch.PREFIX_UPPER_BOUND_SUFFIX

        return coroutineScope {
            chatIds.map { chatId ->
                async(Dispatchers.IO) {
                    runCatching {
                        messagesQuery(chatId)
                            .whereGreaterThanOrEqualTo("searchText", query)
                            .whereLessThan("searchText", upper)
                            .limit(limitPerChat)
                            .get()
                            .await()
                            .documents
                            .mapNotNull { doc ->
                                val text = doc.getString("text") ?: return@mapNotNull null
                                val createdAt = doc.getTimestamp("createdAt")?.toDate()?.time
                                    ?: return@mapNotNull null
                                MessageHit(
                                    chatId = chatId,
                                    messageId = doc.id,
                                    authorId = doc.getString("authorId").orEmpty(),
                                    text = text,
                                    createdAt = createdAt
                                )
                            }
                    }.onFailure {
                        android.util.Log.w("ChatRepository", "Search failed in chat $chatId", it)
                    }.getOrDefault(emptyList())
                }
            }.awaitAll()
                .flatten()
                .sortedByDescending { it.createdAt }
        }
    }

    /** One-shot fetch of the page strictly older than [before]. Empty list = no more history. */
    suspend fun loadOlderMessages(chatId: String, before: Timestamp): List<ChatMessage> {
        return try {
            messagesQuery(chatId).startAfter(before).limit(pageSize).get().await()
                .documents.mapNotNull { doc ->
                    doc.toObject(ChatMessage::class.java)?.copy(id = doc.id)
                }
        } catch (e: Exception) {
            android.util.Log.w("ChatRepository", "Loading older messages failed", e)
            emptyList()
        }
    }

    /**
     * Sends (or re-sends) a message.
     *
     * The document id is [clientMsgId], generated on the device BEFORE the network
     * call. Firestore `set()` on an existing id is a no-op, so retrying a message
     * after a dropped connection cannot duplicate it — that is the whole reason the
     * outbox exists.
     *
     * The chat document is upserted first and MUST carry `members`: the security
     * rules for `messages` do `get(/chats/$(chatId)).data.members`, so a message
     * written into a chat without a document is always denied.
     */
    suspend fun sendMessage(
        chatId: String,
        authorId: String,
        text: String,
        sourceLang: String,
        hintLang: String,
        hintText: String,
        clientMsgId: String,
        // Faza 5 — załączniki (domyślnie puste dla zwykłego tekstu).
        type: String = "text",
        attachmentUrl: String = "",
        ocrText: String = "",
        transcript: String = ""
    ) {
        val msg = ChatMessage(
            authorId = authorId,
            sourceLang = sourceLang,
            text = text,
            senderTranslation = if (hintText.isBlank()) null else SenderTranslation(hintLang, hintText),
            type = type,
            clientMsgId = clientMsgId,
            attachmentUrl = attachmentUrl,
            ocrText = ocrText,
            transcript = transcript,
            createdAt = Timestamp.now()
        )
        // Podgląd w inboxie: dla mediów pokazujemy OCR/transkrypcję (jeśli jest),
        // w przeciwnym razie puste — i tak zlokalizowany placeholder wg lastMessageType.
        val preview = when (type) {
            "image" -> ocrText.takeIf { it.isNotBlank() } ?: ""
            "audio" -> transcript.takeIf { it.isNotBlank() } ?: ""
            else -> text
        }
        val chatRef = firestore.collection("chats").document(chatId)
        chatRef.set(
            mapOf(
                "members" to membersFromChatId(chatId),
                "lastMessage" to preview.take(80),
                "lastMessageType" to type,
                "lastMessageAuthorId" to authorId,
                "lastMessageAt" to System.currentTimeMillis()
            ),
            SetOptions.merge()
        ).await()
        chatRef.collection("messages").document(clientMsgId).set(msg).await()
    }

    // ------------------------------------------------------------------- inbox

    /**
     * Every conversation `uid` belongs to.
     *
     * No `orderBy`: Firestore needs a COMPOSITE index for array-contains combined
     * with ordering on a different field, and that index would have to be deployed
     * separately (and would break the inbox until it was). With a few dozen
     * conversations, sorting in the ViewModel is free — see [ChatSummary].
     */
    fun watchChats(uid: String): Flow<List<ChatSummary>> = callbackFlow {
        if (uid.isBlank()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val listener = firestore.collection("chats")
            .whereArrayContains("members", uid)
            .limit(50)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    android.util.Log.w("ChatRepository", "Chats listener failed", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snap?.documents?.mapNotNull { doc ->
                    doc.toObject(ChatSummary::class.java)?.copy(chatId = doc.id)
                } ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    // ------------------------------------------------------------ read receipts

    /**
     * `uid -> lastReadAt (ms)` for every member of the chat.
     *
     * Kept in a subcollection with one document PER MEMBER instead of a `readBy`
     * map on each message: the rules then reduce to "you may only write your own
     * document", which needs no nested-map diff. Changing `allow update` on
     * `messages` from `if false` (which exists for a reason) was the alternative,
     * and it is a much bigger security surface.
     */
    fun watchReadReceipts(chatId: String): Flow<Map<String, Long>> = callbackFlow {
        val listener = firestore.collection("chats").document(chatId)
            .collection("readReceipts")
            .addSnapshotListener { snap, _ ->
                trySend(
                    snap?.documents?.associate { doc ->
                        doc.id to (doc.getLong("lastReadAt") ?: 0L)
                    } ?: emptyMap()
                )
            }
        awaitClose { listener.remove() }
    }

    suspend fun markRead(chatId: String, uid: String, lastReadAt: Long) {
        try {
            firestore.collection("chats").document(chatId)
                .collection("readReceipts").document(uid)
                .set(
                    mapOf(
                        "uid" to uid,
                        "lastReadAt" to lastReadAt,
                        "updatedAt" to System.currentTimeMillis()
                    )
                ).await()
        } catch (e: Exception) {
            android.util.Log.w("ChatRepository", "Could not mark chat read", e)
        }
    }

    // ------------------------------------------------------------------- typing

    /**
     * `uid -> expiresAt (ms)`. A member is "typing" while their expiry is in the
     * future, so a crashed app stops looking like it is typing after ~8 s instead
     * of forever. Same one-document-per-member trick as the read receipts.
     */
    fun watchTyping(chatId: String): Flow<Map<String, Long>> = callbackFlow {
        val listener = firestore.collection("chats").document(chatId)
            .collection("typing")
            .addSnapshotListener { snap, _ ->
                trySend(
                    snap?.documents?.associate { doc ->
                        doc.id to (doc.getLong("expiresAt") ?: 0L)
                    } ?: emptyMap()
                )
            }
        awaitClose { listener.remove() }
    }

    suspend fun setTyping(chatId: String, uid: String, isTyping: Boolean) {
        try {
            firestore.collection("chats").document(chatId)
                .collection("typing").document(uid)
                .set(
                    mapOf(
                        "uid" to uid,
                        "expiresAt" to if (isTyping) System.currentTimeMillis() + TYPING_TTL_MS else 0L
                    )
                ).await()
        } catch (e: Exception) {
            // Presence is cosmetic — never let it surface as a user-visible error.
            android.util.Log.w("ChatRepository", "Could not update typing state", e)
        }
    }

    companion object {
        /** How long a "typing" flag stays valid without a refresh from the sender. */
        const val TYPING_TTL_MS = 8_000L
    }

    // -------------------------------------------------------------- friendships

    /**
     * ONE listener for every friendship `uid` is part of.
     *
     * The old code queried `whereEqualTo("uidA", uid)`, and since `uidA` is the
     * lexicographically smaller uid, the person whose uid sorts second never saw the
     * friendship at all — half of all accepted invitations simply vanished.
     * Firestore has no OR, so the fix is a `members` array + `whereArrayContains`.
     *
     * Requires `members` to exist on the document — see `backfill_faza0.js`.
     */
    fun watchFriendships(uid: String): Flow<List<Friendship>> = callbackFlow {
        if (uid.isBlank()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val listener = firestore.collection("friendships")
            .whereArrayContains("members", uid)
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.mapNotNull {
                    it.toObject(Friendship::class.java)?.copy(id = it.id)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    /** Accepted friendships — the actual friend list. */
    fun watchAccepted(uid: String): Flow<List<Friendship>> =
        watchFriendships(uid).map { list -> list.filter { it.isAccepted } }

    /** Pending invitations addressed to `uid` (created by the other side). */
    fun watchIncoming(uid: String): Flow<List<Friendship>> =
        watchFriendships(uid).map { list -> list.filter { it.isIncoming(uid) } }

    /** Pending invitations `uid` has sent and is still waiting on. */
    fun watchOutgoing(uid: String): Flow<List<Friendship>> =
        watchFriendships(uid).map { list -> list.filter { it.isOutgoing(uid) } }

    suspend fun requestFriendship(myUid: String, myNick: String, otherUid: String, otherNick: String) {
        val id = getChatId(myUid, otherUid)
        val sorted = listOf(myUid, otherUid).sorted()
        val friendship = Friendship(
            id = id,
            uidA = sorted[0],
            uidB = sorted[1],
            members = sorted,
            status = "pending",
            requestedBy = myUid,
            nicknameA = if (sorted[0] == myUid) myNick else otherNick,
            nicknameB = if (sorted[0] == myUid) otherNick else myNick,
            createdAt = Timestamp.now()
        )
        firestore.collection("friendships").document(id).set(friendship).await()
    }

    suspend fun acceptFriendship(friendshipId: String) {
        firestore.collection("friendships").document(friendshipId)
            .update("status", "accepted").await()
    }

    suspend fun declineFriendship(friendshipId: String) {
        firestore.collection("friendships").document(friendshipId)
            .delete().await()
    }

    // --------------------------------------------------------- contact settings

    /**
     * Every per-contact setting `uid` has, keyed by the other person's uid.
     *
     * One listener for the whole subcollection rather than one per contact: the
     * inbox needs the map anyway (alias / pinned / blocked / muted all affect how
     * rows are drawn), and a single listener costs one subscription instead of N.
     * Contacts with no document simply do not appear in the map — the UI falls
     * back to [ContactSettings.EMPTY], so "no settings" never allocates documents.
     */
    fun watchContactSettings(uid: String): Flow<Map<String, ContactSettings>> = callbackFlow {
        if (uid.isBlank()) {
            trySend(emptyMap())
            awaitClose { }
            return@callbackFlow
        }
        val listener = firestore.collection("users").document(uid)
            .collection("contacts")
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    android.util.Log.w("ChatRepository", "Contact settings listener failed", error)
                    trySend(emptyMap())
                    return@addSnapshotListener
                }
                trySend(
                    snap?.documents?.associate { doc ->
                        doc.id to (doc.toObject(ContactSettings::class.java) ?: ContactSettings.EMPTY)
                    } ?: emptyMap()
                )
            }
        awaitClose { listener.remove() }
    }

    /**
     * Writes the given settings for one contact. Passing [ContactSettings.EMPTY]
     * does not delete the document (an empty document is still cheaper to write
     * than to explain) — use [clearContactSettings] to remove it outright.
     */
    suspend fun saveContactSettings(uid: String, otherUid: String, settings: ContactSettings) {
        if (uid.isBlank() || otherUid.isBlank()) return
        try {
            firestore.collection("users").document(uid)
                .collection("contacts").document(otherUid)
                .set(settings.copy(updatedAt = System.currentTimeMillis()))
                .await()
        } catch (e: Exception) {
            // Losing an alias is annoying; losing the thread is not acceptable, so
            // this never surfaces as an error.
            android.util.Log.w("ChatRepository", "Could not save contact settings", e)
        }
    }

    /** Removes the document entirely, returning the contact to defaults. */
    suspend fun clearContactSettings(uid: String, otherUid: String) {
        if (uid.isBlank() || otherUid.isBlank()) return
        try {
            firestore.collection("users").document(uid)
                .collection("contacts").document(otherUid)
                .delete().await()
        } catch (e: Exception) {
            android.util.Log.w("ChatRepository", "Could not clear contact settings", e)
        }
    }
}
