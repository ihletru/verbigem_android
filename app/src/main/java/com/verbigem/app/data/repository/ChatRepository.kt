package com.verbigem.app.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.verbigem.app.data.model.ChatMessage
import com.verbigem.app.data.model.Friendship
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ChatRepository {

    private val firestore = FirebaseFirestore.getInstance()

    fun getChatId(uidA: String, uidB: String): String {
        return listOf(uidA, uidB).sorted().joinToString("__")
    }

    fun watchMessages(chatId: String): Flow<List<ChatMessage>> = callbackFlow {
        val listener = firestore.collection("chats").document(chatId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(200)
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(ChatMessage::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun sendMessage(
        chatId: String,
        authorId: String,
        text: String,
        sourceLang: String,
        translatedText: String
    ) {
        val msg = ChatMessage(
            authorId = authorId,
            sourceLang = sourceLang,
            text = text,
            translatedText = translatedText,
            createdAt = Timestamp.now()
        )
        val chatRef = firestore.collection("chats").document(chatId)
        chatRef.collection("messages").add(msg).await()
        chatRef.set(
            mapOf(
                "lastMessage" to text.take(80),
                "lastMessageAt" to System.currentTimeMillis()
            ),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
    }

    fun watchFriendships(uid: String): Flow<List<Friendship>> = callbackFlow {
        val listenerA = firestore.collection("friendships")
            .whereEqualTo("uidA", uid)
            .addSnapshotListener { snapA, _ ->
                val listA = snapA?.documents?.mapNotNull { it.toObject(Friendship::class.java)?.copy(id = it.id) } ?: emptyList()
                trySend(listA)
            }
        awaitClose { listenerA.remove() }
    }

    fun watchIncoming(uid: String): Flow<List<Friendship>> = callbackFlow {
        val listenerB = firestore.collection("friendships")
            .whereEqualTo("uidB", uid)
            .addSnapshotListener { snapB, _ ->
                val listB = snapB?.documents?.mapNotNull { it.toObject(Friendship::class.java)?.copy(id = it.id) } ?: emptyList()
                trySend(listB)
            }
        awaitClose { listenerB.remove() }
    }

    suspend fun requestFriendship(myUid: String, myNick: String, otherUid: String, otherNick: String) {
        val id = getChatId(myUid, otherUid)
        val sorted = listOf(myUid, otherUid).sorted()
        val friendship = Friendship(
            id = id,
            uidA = sorted[0],
            uidB = sorted[1],
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
