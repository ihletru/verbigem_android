package com.verbigem.app.ui.screens.contacts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.verbigem.app.data.PhoneContact
import com.verbigem.app.data.PhoneContactsImporter
import com.verbigem.app.data.model.Friendship
import com.verbigem.app.data.repository.AuthRepository
import com.verbigem.app.data.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

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

    // --- Kontakty z książki telefonicznej (prominent disclosure + READ_CONTACTS) ---

    /** true = pokaż ekran wyjaśnienia ZANIM poprosimy system o uprawnienie. */
    private val _showPermissionDisclosure = MutableStateFlow(false)
    val showPermissionDisclosure: StateFlow<Boolean> = _showPermissionDisclosure.asStateFlow()

    private val _phoneContacts = MutableStateFlow<List<PhoneContact>>(emptyList())
    val phoneContacts: StateFlow<List<PhoneContact>> = _phoneContacts.asStateFlow()

    private val _permissionDenied = MutableStateFlow(false)
    val permissionDenied: StateFlow<Boolean> = _permissionDenied.asStateFlow()

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

    // --- Kontakty z telefonu ---

    fun hasContactsPermission(): Boolean =
        PhoneContactsImporter.hasPermission(getApplication())

    /**
     * Krok 1: użytkownik chce znaleźć znajomych. Jeśli nie mamy zgody,
     * pokazujemy prominent disclosure — NIE odpalamy dialogu systemowego.
     */
    fun onFindFromPhoneClicked() {
        if (hasContactsPermission()) {
            loadPhoneContacts()
        } else {
            _showPermissionDisclosure.value = true
        }
    }

    /** Krok 2: wynik systemowego dialogu (albo „pomiń" z ekranu wyjaśnienia). */
    fun onPermissionResult(granted: Boolean) {
        _showPermissionDisclosure.value = false
        if (granted) {
            loadPhoneContacts()
        } else {
            _permissionDenied.value = true
        }
    }

    fun dismissDisclosure() {
        _showPermissionDisclosure.value = false
    }

    private fun loadPhoneContacts() {
        _permissionDenied.value = false
        viewModelScope.launch {
            // Odczyt ContentProvidera — na IO, żeby nie blokować wątku głównego
            // przy dużej książce adresowej.
            _phoneContacts.value = withContext(Dispatchers.IO) {
                PhoneContactsImporter.read(getApplication())
            }
        }
    }
}
