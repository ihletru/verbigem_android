package com.verbigem.app.ui.screens.contacts

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.verbigem.app.data.PhoneContact
import com.verbigem.app.data.PhoneContactsImporter
import com.verbigem.app.data.VcfImporter
import com.verbigem.app.data.local.ExternalContactEntity
import com.verbigem.app.data.model.Friendship
import com.verbigem.app.data.model.UserProfile
import com.verbigem.app.data.repository.AuthRepository
import com.verbigem.app.data.repository.ChatRepository
import com.verbigem.app.data.repository.ContactInviteStatus
import com.verbigem.app.data.repository.ContactMatch
import com.verbigem.app.data.repository.ContactMatchException
import com.verbigem.app.data.repository.ContactMatchFailure
import com.verbigem.app.data.repository.ContactMatchRepository
import com.verbigem.app.data.repository.ExternalThreadRepository
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

    // --- Import wizytówek .vcf (3.7) ---

    /** Kontakty zaimportowane ręcznie z pliku, poza książką urządzenia. */
    private val _importedContacts = MutableStateFlow<List<PhoneContact>>(emptyList())
    val importedContacts: StateFlow<List<PhoneContact>> = _importedContacts.asStateFlow()

    /**
     * Ile kontaktów właśnie wpadło z ostatniego pliku (`null` = cisza). `0` to
     * komunikat „plik pusty", a nie błąd — stąd osobna flaga dla czytania.
     */
    private val _vcfImportedCount = MutableStateFlow<Int?>(null)
    val vcfImportedCount: StateFlow<Int?> = _vcfImportedCount.asStateFlow()

    private val _vcfReadError = MutableStateFlow(false)
    val vcfReadError: StateFlow<Boolean> = _vcfReadError.asStateFlow()

    // --- Zewnętrzne kontakty (3.6 / zakładka 3.8) ---

    /** Osoby, do których kiedyś napisaliśmy przez wątek jednokierunkowy. */
    private val _externalContacts = MutableStateFlow<List<ExternalContactEntity>>(emptyList())
    val externalContacts: StateFlow<List<ExternalContactEntity>> = _externalContacts.asStateFlow()

    // --- Dopasowanie książki telefonicznej do kont Verbigem (2.3) ---

    /** phone (znormalizowany) -> konto, które do niego pasuje. */
    private val _phoneMatches = MutableStateFlow<Map<String, ContactMatch>>(emptyMap())
    val phoneMatches: StateFlow<Map<String, ContactMatch>> = _phoneMatches.asStateFlow()

    private val _isMatching = MutableStateFlow(false)
    val isMatching: StateFlow<Boolean> = _isMatching.asStateFlow()

    /** Komunikat do pokazania, gdy dopasowanie się nie udało. Null = wszystko OK. */
    private val _matchFailure = MutableStateFlow<ContactMatchFailure?>(null)
    val matchFailure: StateFlow<ContactMatchFailure?> = _matchFailure.asStateFlow()

    /** Numery, dla których zapisaliśmy już zaproszenie w tym uruchomieniu ekranu. */
    private val _invitedPhones = MutableStateFlow<Set<String>>(emptySet())
    val invitedPhones: StateFlow<Set<String>> = _invitedPhones.asStateFlow()

    private val contactMatchRepository = ContactMatchRepository()
    private val externalThreads = ExternalThreadRepository(application)

    val currentUid: String
        get() = authRepository.currentUser?.uid ?: ""

    /** My own profile — source of the language pair and the nickname used in invites. */
    private val _myProfile = MutableStateFlow<UserProfile?>(null)
    val myProfile: StateFlow<UserProfile?> = _myProfile.asStateFlow()

    init {
        viewModelScope.launch {
            externalThreads.watchContacts().collect { _externalContacts.value = it }
        }

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
            val contacts = withContext(Dispatchers.IO) {
                PhoneContactsImporter.read(getApplication())
            }
            _phoneContacts.value = contacts
            matchPhoneContacts(contacts)
        }
    }

    /**
     * Wysyła wyłącznie skróty SHA-256 numerów i prosi `matchContacts` o konta,
     * które już istnieją. Nazwiska i numery zostają na telefonie.
     *
     * Brak dopasowań nie jest błędem: `phoneDirectory` zapełnia się dopiero w 2.6
     * (weryfikacja numeru), więc do tego czasu odpowiedź będzie pusta.
     */
    private suspend fun matchPhoneContacts(contacts: List<PhoneContact>) {
        if (contacts.isEmpty()) return
        _isMatching.value = true
        _matchFailure.value = null
        try {
            val matches = contactMatchRepository.match(contacts)
            _phoneMatches.value = matches.associateBy { it.phone }
        } catch (e: ContactMatchException) {
            // Pokazujemy komunikat, ale lista kontaktów zostaje — niedziałające
            // dopasowanie nie może ukryć książki adresowej.
            Log.w("ContactsViewModel", "Contact matching failed: ${e.reason}")
            _matchFailure.value = e.reason
            _phoneMatches.value = emptyMap()
        } finally {
            _isMatching.value = false
        }
    }

    /** Czy ten numer ma już konto w Verbigem. */
    fun matchFor(phone: String): ContactMatch? =
        _phoneMatches.value[PhoneContactsImporter.normalize(phone)]

    /**
     * Zapisuje zaproszenie dla jednego kontaktu z książki.
     *
     * Numer wysyłamy wyłącznie jako skrót — patrz `ContactMatchRepository`. Jeśli
     * osoba zdążyła w międzyczasie założyć konto, funkcja od razu tworzy zaproszenie
     * do znajomych zamiast zostawiać zaproszenie, którego nikt już nie rozwiąże.
     *
     * Ekran i tak otwiera potem arkusz udostępniania: link i zaproszenie pod numer
     * to dwa różne sposoby na ten sam cel, a każdy z osobna jest niepełny.
     */
    fun inviteContact(contact: PhoneContact) {
        viewModelScope.launch {
            _matchFailure.value = null
            try {
                val status = contactMatchRepository.invite(contact)
                if (status != ContactInviteStatus.NOOP) {
                    _invitedPhones.value = _invitedPhones.value + contact.phone
                }
                if (status == ContactInviteStatus.FRIEND_REQUESTED) {
                    // Konto istniało — odśwież matching, żeby wiersz pokazał „Napisz".
                    matchPhoneContacts(_phoneContacts.value)
                }
            } catch (e: ContactMatchException) {
                Log.w("ContactsViewModel", "Inviting a contact failed: ${e.reason}")
                _matchFailure.value = e.reason
            }
        }
    }

    /** Czy zaproszenie dla tego numeru zostało już zapisane. */
    fun isInvited(phone: String): Boolean =
        _invitedPhones.value.contains(PhoneContactsImporter.normalize(phone))

    /**
     * Importuje kontakty z pliku `.vcf` (3.7). Własny parser, zero zależności.
     *
     * `parseUri` czyta plik przez `contentResolver` — dlatego metoda bierze `Uri`,
     * a nie surowy tekst. Wynik dołączamy do listy zaimportowanych, pomijając
     * numery, które już są na liście (książka lub wcześniejszy import) — import
     * dwukrotny tej samej wizytówki nie ma dodać drugiego wiersza.
     *
     * Błąd czytania pliku (np. URI bez uprawnienia) jest sygnałem dla UI, ale
     * nie przerywa — parser sam ignoruje uszkodzone karty, więc „nic nie wpadło"
     * to częściej pusty plik niż awaria.
     */
    fun importVcf(uri: Uri) {
        viewModelScope.launch {
            _vcfReadError.value = false
            _vcfImportedCount.value = null
            val parsed = VcfImporter.parseUri(getApplication(), uri)
            if (parsed.isEmpty()) {
                _vcfImportedCount.value = 0
                return@launch
            }
            val known = (_phoneContacts.value + _importedContacts.value)
                .map { it.phone }.toSet()
            val added = parsed.filter { it.phone !in known }
            _importedContacts.value = _importedContacts.value + added
            _vcfImportedCount.value = added.size
        }
    }

    /** Czyści komunikat o wyniku importu (po jego przeczytaniu w UI). */
    fun clearVcfStatus() {
        _vcfImportedCount.value = null
        _vcfReadError.value = false
    }

    /**
     * Zapisuje osobę spoza Verbigem w Room i dopiero potem pozwala wejść do wątku (3.6).
     *
     * **Suspend, i to nie przypadek.** Wątek czyta kontakt z bazy po kluczu
     * telefonu; gdyby ekran otworzył się przed zapisem, zastałby pusty ekran z
     * kręcącym się kółkiem i niczym więcej — nazwa i adres są w `PhoneContact`,
     * który żyje tylko w pamięci. Wpis powstaje więc w momencie wejścia, nie przy
     * synchronizacji książki: czytanie całej książki do bazy po to, żeby ktoś
     * *może* kiedyś napisał do jednej osoby, byłoby nadużyciem zaufania.
     */
    suspend fun rememberExternal(contact: PhoneContact) = externalThreads.remember(contact)
}
