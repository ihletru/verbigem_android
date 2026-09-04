package com.verbigem.app.ui.screens.chat

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.verbigem.app.data.local.AppDatabase
import com.verbigem.app.data.model.ChatSummary
import com.verbigem.app.data.model.PublicProfile
import com.verbigem.app.data.repository.AuthRepository
import com.verbigem.app.data.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One row of the inbox: the conversation plus everything needed to draw it. */
data class ChatRow(
    val chatId: String,
    val otherUid: String,
    val nickname: String,
    val avatar: String,
    val lastMessage: String,
    val lastMessageAt: Long,
    val lastMessageIsMine: Boolean,
    val unread: Boolean
)

class ChatListViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ChatListViewModel"
    }

    private val authRepository = AuthRepository()
    private val chatRepository = ChatRepository()
    private val readDao = AppDatabase.getInstance(application).chatReadDao()

    private val _rows = MutableStateFlow<List<ChatRow>>(emptyList())
    val rows: StateFlow<List<ChatRow>> = _rows.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val chats = MutableStateFlow<List<ChatSummary>>(emptyList())
    private val profiles = MutableStateFlow<Map<String, PublicProfile>>(emptyMap())
    private val friendNicks = MutableStateFlow<Map<String, String>>(emptyMap())
    private val reads = MutableStateFlow<Map<String, Long>>(emptyMap())

    val currentUid: String
        get() = authRepository.currentUser?.uid ?: ""

    init {
        val uid = currentUid
        if (uid.isBlank()) {
            _isLoading.value = false
        } else {
            viewModelScope.launch {
                chatRepository.watchChats(uid).collect { list ->
                    chats.value = list
                    // Names come from `usersPublic` (the only collection another user
                    // is allowed to read). Fetch lazily and cache — one read per new
                    // person, not per frame.
                    list.forEach { summary ->
                        val other = summary.otherUid(uid)
                        if (other.isNotBlank() && other !in profiles.value) {
                            fetchProfile(other)
                        }
                    }
                    recompute()
                }
            }
            // Friendships are a cheaper source of nicknames (already one listener,
            // already paid for) and cover people whose public doc is still missing.
            viewModelScope.launch {
                chatRepository.watchAccepted(uid).collect { list ->
                    friendNicks.value = list.associate { f -> f.otherUid(uid) to f.otherNickname(uid) }
                    recompute()
                }
            }
            viewModelScope.launch {
                readDao.watchAll().collect { rows ->
                    reads.value = rows.associate { it.chatId to it.lastReadAt }
                    recompute()
                }
            }
        }
    }

    private fun fetchProfile(uid: String) {
        viewModelScope.launch {
            try {
                authRepository.getPublicProfile(uid)?.let { profile ->
                    profiles.value = profiles.value + (uid to profile)
                    recompute()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not read usersPublic/$uid", e)
            }
        }
    }

    private fun recompute() {
        val me = currentUid
        _rows.value = chats.value
            .mapNotNull { summary ->
                val other = summary.otherUid(me)
                if (other.isBlank()) null else summary to other
            }
            .map { (summary, other) ->
                ChatRow(
                    chatId = summary.chatId,
                    otherUid = other,
                    nickname = displayName(other),
                    avatar = profiles.value[other]?.photoURL?.takeIf { it.isNotBlank() } ?: "🙂",
                    lastMessage = summary.lastMessage,
                    lastMessageAt = summary.lastMessageAt,
                    lastMessageIsMine = summary.lastMessageAuthorId == me,
                    // Local watermark vs. the newest message in the conversation.
                    unread = summary.lastMessageAt > (reads.value[summary.chatId] ?: 0L) &&
                        summary.lastMessageAuthorId != me
                )
            }
            .sortedByDescending { it.lastMessageAt }
        _isLoading.value = false
    }

    private fun displayName(uid: String): String {
        val fromProfile = profiles.value[uid]?.nickname?.takeIf { it.isNotBlank() }
        if (fromProfile != null) return fromProfile
        return friendNicks.value[uid]?.takeIf { it.isNotBlank() } ?: uid.take(6)
    }
}
