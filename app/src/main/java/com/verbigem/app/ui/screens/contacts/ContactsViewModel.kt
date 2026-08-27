package com.verbigem.app.ui.screens.contacts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.verbigem.app.data.model.Friendship
import com.verbigem.app.data.repository.AuthRepository
import com.verbigem.app.data.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class SearchResultUser(
    val uid: String = "",
    val nickname: String = "",
    val photoURL: String? = "🙂"
)

class ContactsViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository = AuthRepository()
    private val chatRepository = ChatRepository()
    private val firestore = FirebaseFirestore.getInstance()

    private val _friends = MutableStateFlow<List<Friendship>>(emptyList())
    val friends: StateFlow<List<Friendship>> = _friends.asStateFlow()

    private val _incoming = MutableStateFlow<List<Friendship>>(emptyList())
    val incoming: StateFlow<List<Friendship>> = _incoming.asStateFlow()

    private val _searchTerm = MutableStateFlow("")
    val searchTerm: StateFlow<String> = _searchTerm.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResultUser>>(emptyList())
    val searchResults: StateFlow<List<SearchResultUser>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _sentRequests = MutableStateFlow<Set<String>>(emptySet())
    val sentRequests: StateFlow<Set<String>> = _sentRequests.asStateFlow()

    val currentUid: String
        get() = authRepository.currentUser?.uid ?: ""

    init {
        val uid = currentUid
        if (uid.isNotBlank()) {
            viewModelScope.launch {
                chatRepository.watchFriendships(uid).collect { list ->
                    _friends.value = list.filter { it.isAccepted }
                }
            }
            viewModelScope.launch {
                chatRepository.watchIncoming(uid).collect { list ->
                    _incoming.value = list.filter { !it.isAccepted }
                }
            }
        }
    }

    fun onSearchTermChanged(term: String) {
        _searchTerm.value = term
    }

    fun searchUsers() {
        val term = _searchTerm.value.trim()
        if (term.length < 2) return

        _isSearching.value = true
        viewModelScope.launch {
            try {
                val snap = firestore.collection("users")
                    .whereGreaterThanOrEqualTo("nickname", term)
                    .whereLessThanOrEqualTo("nickname", term + "\uf8ff")
                    .limit(10)
                    .get()
                    .await()

                val results = snap.documents.mapNotNull { doc ->
                    val uid = doc.id
                    if (uid != currentUid) {
                        SearchResultUser(
                            uid = uid,
                            nickname = doc.getString("nickname") ?: "user",
                            photoURL = doc.getString("photoURL") ?: "🙂"
                        )
                    } else null
                }
                _searchResults.value = results
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun sendRequest(targetUid: String, targetNick: String) {
        val myUid = currentUid
        val myNick = authRepository.currentUser?.displayName ?: "user"
        viewModelScope.launch {
            chatRepository.requestFriendship(myUid, myNick, targetUid, targetNick)
            _sentRequests.value = _sentRequests.value + targetUid
        }
    }

    fun acceptFriend(friendship: Friendship) {
        viewModelScope.launch {
            chatRepository.acceptFriendship(friendship.id)
        }
    }

    fun declineFriend(friendship: Friendship) {
        viewModelScope.launch {
            chatRepository.declineFriendship(friendship.id)
        }
    }
}
