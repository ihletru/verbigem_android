package com.verbigem.app.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.verbigem.app.data.model.ChatMessage
import com.verbigem.app.data.model.ChatSummary
import com.verbigem.app.data.model.Friendship
import com.verbigem.app.data.model.SenderTranslation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

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
        clientMsgId: String
    ) {
        val msg = ChatMessage(
            authorId = authorId,
            sourceLang = sourceLang,
            text = text,
            senderTranslation = if (hintText.isBlank()) null else SenderTranslation(hintLang, hintText),
            type = "text",
            clientMsgId = clientMsgId,
            createdAt = Timestamp.now()
        )
        val chatRef = firestore.collection("chats").document(chatId)
        chatRef.set(
            mapOf(
                "members" to membersFromChatId(chatId),
                "lastMessage" to text.take(80),
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
}
