package com.verbigem.app.ui.screens.chat

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.verbigem.app.data.local.AppDatabase
import com.verbigem.app.data.model.ChatSummary
import com.verbigem.app.data.model.ContactSettings
import com.verbigem.app.data.model.PublicProfile
import com.verbigem.app.data.repository.AuthRepository
import com.verbigem.app.data.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One row of the inbox: the conversation plus everything needed to draw it. */
data class ChatRow(
    val chatId: String,
    val otherUid: String,
    val nickname: String,
    val avatar: String,
    val lastMessage: String,
    val lastMessageAt: Long,
    val lastMessageIsMine: Boolean,
    /** Faza 5: rodzaj ostatniej wiadomości — inbox pokazuje zlokalizowany placeholder. */
    val lastMessageType: String = "text",
    val unread: Boolean,
    /** Pinned conversations float to the top of the inbox. */
    val pinned: Boolean = false,
    val muted: Boolean = false
)

/** One message that matched a search, with everything needed to draw the row. */
data class MessageSearchHit(
    val chatId: String,
    val otherUid: String,
    val nickname: String,
    val avatar: String,
    val text: String,
    val createdAt: Long,
    val isMine: Boolean
)

class ChatListViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ChatListViewModel"

        /**
         * Krótsze zapytania niż 3 znaki są bez sensu: to jedno zapytanie do Firestore
         * na rozmowę, a jedna i dwie litery trafią w większość wiadomości w bazie.
         */
        const val MIN_SEARCH_LENGTH = 3
    }

    private val authRepository = AuthRepository()
    private val chatRepository = ChatRepository()
    private val readDao = AppDatabase.getInstance(application).chatReadDao()
    private val hiddenDao = AppDatabase.getInstance(application).chatHiddenDao()

    private val _rows = MutableStateFlow<List<ChatRow>>(emptyList())
    val rows: StateFlow<List<ChatRow>> = _rows.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // ── Wyszukiwanie w wiadomościach (1.12) ─────────────────────────────────
    //
    // Uruchamiane jawnie (przycisk / akcja IME), nie przy każdym naciśnięciu
    // klawisza: koszt to jedno zapytanie na rozmowę, więc wyszukiwanie na żywo
    // mieliłoby Firestore przy każdej literze bez żadnej korzyści dla użytkownika.

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchHits = MutableStateFlow<List<MessageSearchHit>>(emptyList())
    val searchHits: StateFlow<List<MessageSearchHit>> = _searchHits.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    /** Czy wyszukiwanie w ogóle było uruchomione — odróżnia „brak wyników" od „nie szukano". */
    private val _searchDone = MutableStateFlow(false)
    val searchDone: StateFlow<Boolean> = _searchDone.asStateFlow()

    private val chats = MutableStateFlow<List<ChatSummary>>(emptyList())
    private val profiles = MutableStateFlow<Map<String, PublicProfile>>(emptyMap())
    private val friendNicks = MutableStateFlow<Map<String, String>>(emptyMap())
    private val reads = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val settings = MutableStateFlow<Map<String, ContactSettings>>(emptyMap())
    private val hiddenChatIds = MutableStateFlow<Set<String>>(emptySet())

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
            // Alias / pin / mute / block, keyed by the other person's uid.
            viewModelScope.launch {
                chatRepository.watchContactSettings(uid).collect { map ->
                    settings.value = map
                    recompute()
                }
            }
            viewModelScope.launch {
                hiddenDao.watchAll().collect { rows ->
                    hiddenChatIds.value = rows.map { it.chatId }.toSet()
                    recompute()
                }
            }
        }
    }

    fun onSearchQueryChanged(value: String) {
        _searchQuery.value = value
        if (value.isBlank()) {
            clearSearch()
            return
        }
        // Edycja zapytania cofa skrzynkę do listy rozmów: stare wyniki nie pasują
        // do nowego tekstu, a trzymanie ich na ekranie kłamałoby o tym, co znaleziono.
        _searchHits.value = emptyList()
        _searchDone.value = false
    }

    /** Szuka tylko w rozmowach, które użytkownik i tak widzi (bez zablokowanych i ukrytych). */
    fun search() {
        val query = _searchQuery.value.trim()
        if (query.length < MIN_SEARCH_LENGTH) return
        if (currentUid.isBlank()) return

        val visible = _rows.value
        if (visible.isEmpty()) {
            _searchHits.value = emptyList()
            _searchDone.value = true
            return
        }

        _isSearching.value = true
        viewModelScope.launch {
            val hits = withContext(Dispatchers.IO) {
                chatRepository.searchMessages(visible.map { it.chatId }, query)
            }
            val byChatId = visible.associateBy { it.chatId }
            _searchHits.value = hits.mapNotNull { hit ->
                val row = byChatId[hit.chatId] ?: return@mapNotNull null
                MessageSearchHit(
                    chatId = hit.chatId,
                    otherUid = row.otherUid,
                    nickname = row.nickname,
                    avatar = row.avatar,
                    text = hit.text,
                    createdAt = hit.createdAt,
                    isMine = hit.authorId == currentUid
                )
            }
            _isSearching.value = false
            _searchDone.value = true
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchHits.value = emptyList()
        _searchDone.value = false
        _isSearching.value = false
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
            // Blocked and deleted conversations never reach the inbox. Both are
            // local-only decisions: the other person still sees the thread.
            .filterNot { (summary, other) ->
                summary.chatId in hiddenChatIds.value ||
                    (settings.value[other]?.blocked == true)
            }
            .map { (summary, other) ->
                val contact = settings.value[other] ?: ContactSettings.EMPTY
                ChatRow(
                    chatId = summary.chatId,
                    otherUid = other,
                    nickname = displayName(other, contact.alias),
                    avatar = profiles.value[other]?.photoURL?.takeIf { it.isNotBlank() } ?: "🙂",
                    lastMessage = summary.lastMessage,
                    lastMessageAt = summary.lastMessageAt,
                    lastMessageType = summary.lastMessageType,
                    lastMessageIsMine = summary.lastMessageAuthorId == me,
                    // Local watermark vs. the newest message in the conversation.
                    // Muting hides the dot — there are no push notifications to
                    // silence yet, so this is what "mute" can honestly mean today.
                    unread = !contact.muted &&
                        summary.lastMessageAt > (reads.value[summary.chatId] ?: 0L) &&
                        summary.lastMessageAuthorId != me,
                    pinned = contact.pinned,
                    muted = contact.muted
                )
            }
            .sortedWith(compareByDescending<ChatRow> { it.pinned }
                .thenByDescending { it.lastMessageAt })
        _isLoading.value = false
    }

    private fun displayName(uid: String, alias: String): String {
        if (alias.isNotBlank()) return alias
        val fromProfile = profiles.value[uid]?.nickname?.takeIf { it.isNotBlank() }
        if (fromProfile != null) return fromProfile
        return friendNicks.value[uid]?.takeIf { it.isNotBlank() } ?: uid.take(6)
    }
}
