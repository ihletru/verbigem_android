package com.verbigem.app.ui.screens.contacts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.verbigem.app.data.PhoneContact
import com.verbigem.app.data.PhoneContactsImporter
import com.verbigem.app.data.model.Friendship
import com.verbigem.app.data.model.UserProfile
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

    /** My own profile — source of the language pair and the nickname used in invites. */
    private val _myProfile = MutableStateFlow<UserProfile?>(null)
    val myProfile: StateFlow<UserProfile?> = _myProfile.asStateFlow()

    init {
        val uid = currentUid
        if (uid.isNotBlank()) {
            viewModelScope.launch {
                authRepository.watchProfile(uid).collect { _myProfile.value = it }
            }
            // All three streams come from the ONE `members`-based listener, so both
            // sides of a friendship see the same state (the old uidA-only query
            // hid the friendship from whoever sorted second).
            viewModelScope.launch {
                chatRepository.watchAccepted(uid).collect { _friends.value = it }
            }
            viewModelScope.launch {
                chatRepository.watchIncoming(uid).collect { _incoming.value = it }
            }
            viewModelScope.launch {
                chatRepository.watchOutgoing(uid).collect { list ->
                    _sentRequests.value = list.map { it.otherUid(uid) }.toSet()
                }
            }
        }
    }

    fun onSearchTermChanged(term: String) {
        _searchTerm.value = term
    }

    /**
     * Searches `usersPublic`, never `users`.
     *
     * `users/{uid}` is readable only by its owner, so the old query on that collection
     * returned PERMISSION_DENIED for every search — finding people was impossible.
     * `usersPublic` is the searchable projection maintained by `AuthRepository`.
     *
     * Two queries (nickname + e-mail) because Firestore has no OR. Results are merged
     * and de-duplicated by uid.
     */
    fun searchUsers() {
        val term = _searchTerm.value.trim()
        if (term.length < 2) return

        val needle = term.lowercase()
        _isSearching.value = true
        viewModelScope.launch {
            try {
                val end = needle + "\uf8ff"
                val byNick = firestore.collection("usersPublic")
                    .whereGreaterThanOrEqualTo("searchNick", needle)
                    .whereLessThanOrEqualTo("searchNick", end)
                    .limit(10)
                    .get()
                    .await()
                val byEmail = firestore.collection("usersPublic")
                    .whereGreaterThanOrEqualTo("searchEmail", needle)
                    .whereLessThanOrEqualTo("searchEmail", end)
                    .limit(10)
                    .get()
                    .await()

                val merged = LinkedHashMap<String, SearchResultUser>()
                (byNick.documents + byEmail.documents).forEach { doc ->
                    val uid = doc.id
                    if (uid != currentUid) {
                        merged[uid] = SearchResultUser(
                            uid = uid,
                            nickname = doc.getString("nickname") ?: "user",
                            photoURL = doc.getString("photoURL") ?: "🙂"
                        )
                    }
                }
                _searchResults.value = merged.values.toList()
            } catch (e: Exception) {
                // Previously swallowed by `finally` — a denied search looked like
                // "no results" instead of an error.
                android.util.Log.w("ContactsViewModel", "User search failed", e)
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun sendRequest(targetUid: String, targetNick: String) {
        val myUid = currentUid
        // Profile nickname, not the Auth display name — that's what other users see,
        // and it survives a Google sign-in where displayName can be null.
        val myNick = _myProfile.value?.nickname
            ?.ifBlank { null }
            ?: authRepository.currentUser?.displayName
            ?: authRepository.currentUser?.email?.substringBefore("@")
            ?: "user"
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
