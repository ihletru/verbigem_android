package com.verbigem.app.ui.screens.chat

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.verbigem.app.data.local.AppDatabase
import com.verbigem.app.data.local.ChatHiddenEntity
import com.verbigem.app.data.model.ContactSettings
import com.verbigem.app.data.model.LangCode
import com.verbigem.app.data.model.PublicProfile
import com.verbigem.app.data.repository.AuthRepository
import com.verbigem.app.data.repository.ChatRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The contact card: everything *I* think about one other person.
 *
 * Two rules shape this class:
 *
 * 1. **Settings are mine, not theirs.** They live under `users/{myUid}/contacts/{uid}`,
 *    never inside the friendship document — an alias I give someone must not appear
 *    on their phone.
 * 2. **Local edits win while they are in flight.** The Firestore listener keeps
 *    [settings] up to date, but a snapshot arriving mid-typing would otherwise
 *    yank the text back. [pendingWrite] suppresses listener updates until our own
 *    write has landed (and is cleared afterwards, so a later edit from another
 *    device still shows up).
 */
class ContactCardViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ContactCardViewModel"
        /** Debounce for the free-text fields (alias, note) — one write per pause. */
        private const val SAVE_DEBOUNCE_MS = 500L
    }

    private val authRepository = AuthRepository()
    private val chatRepository = ChatRepository()
    private val hiddenDao = AppDatabase.getInstance(application).chatHiddenDao()

    private val _profile = MutableStateFlow<PublicProfile?>(null)
    val profile: StateFlow<PublicProfile?> = _profile.asStateFlow()

    private val _settings = MutableStateFlow(ContactSettings.EMPTY)
    val settings: StateFlow<ContactSettings> = _settings.asStateFlow()

    /** The profile-level default, shown next to the "Auto" option. */
    private val _defaultLang = MutableStateFlow(LangCode.PL)
    val defaultLang: StateFlow<LangCode> = _defaultLang.asStateFlow()

    private val _isFriend = MutableStateFlow(false)
    val isFriend: StateFlow<Boolean> = _isFriend.asStateFlow()

    private val _isHidden = MutableStateFlow(false)
    val isHidden: StateFlow<Boolean> = _isHidden.asStateFlow()

    /** True while our own write is outstanding — listener updates are ignored then. */
    private var pendingWrite = false

    private var otherUid: String = ""
    private var saveJob: Job? = null

    val currentUid: String
        get() = authRepository.currentUser?.uid ?: ""

    /** Called once per card; repeated calls for the same uid are ignored. */
    fun open(uid: String) {
        if (uid.isBlank() || uid == otherUid) return
        val me = currentUid
        if (me.isBlank()) return
        otherUid = uid

        viewModelScope.launch {
            authRepository.getPublicProfile(uid)?.let { _profile.value = it }
        }
        viewModelScope.launch {
            chatRepository.watchContactSettings(me).collect { map ->
                if (!pendingWrite) _settings.value = map[uid] ?: ContactSettings.EMPTY
            }
        }
        // Cheap: the friendships listener is already paid for by the rest of the app,
        // and it tells the card whether there is a friendship to show at all.
        viewModelScope.launch {
            chatRepository.watchAccepted(me).collect { list ->
                _isFriend.value = list.any { it.otherUid(me) == uid }
            }
        }
        viewModelScope.launch {
            hiddenDao.watchAll().collect { rows ->
                _isHidden.value = rows.any { it.chatId == chatRepository.getChatId(me, uid) }
            }
        }
        viewModelScope.launch {
            authRepository.watchProfile(me).collect { profile ->
                profile?.let { _defaultLang.value = LangCode.fromCode(it.speakLangSource) }
            }
        }
    }

    // ------------------------------------------------------------------ editing

    /**
     * Every setter follows the same shape: update the local state straight away so
     * the UI never lags behind the keyboard, then persist.
     */
    fun setAlias(value: String) = update { copy(alias = value) }

    fun setNote(value: String) = update { copy(note = value) }

    fun setMuted(value: Boolean) = updateNow { copy(muted = value) }

    fun setPinned(value: Boolean) = updateNow { copy(pinned = value) }

    fun setBlocked(value: Boolean) = updateNow { copy(blocked = value) }

    /** Blank means "use my profile language" — see [ContactSettings.langOverride]. */
    fun setLangOverride(code: String) = updateNow { copy(langOverride = code) }

    /** Debounced: one write per typing pause instead of one per keystroke. */
    private fun update(mutate: ContactSettings.() -> ContactSettings) {
        _settings.value = _settings.value.mutate()
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            persist()
        }
    }

    /** Immediate: toggles are single, deliberate taps. */
    private fun updateNow(mutate: ContactSettings.() -> ContactSettings) {
        _settings.value = _settings.value.mutate()
        saveJob?.cancel()
        viewModelScope.launch { persist() }
    }

    private suspend fun persist() {
        val me = currentUid
        if (me.isBlank() || otherUid.isBlank()) return
        pendingWrite = true
        try {
            chatRepository.saveContactSettings(me, otherUid, _settings.value)
        } finally {
            pendingWrite = false
        }
    }

    // ------------------------------------------------------------- conversation

    /**
     * Hides the conversation from my inbox.
     *
     * Firestore messages are append-only and no Cloud Function prunes them yet, so
     * this is a local hide — the other person keeps the thread. The confirmation
     * dialog in the UI says exactly that; do not soften the wording.
     */
    fun hideConversation() {
        val me = currentUid
        if (me.isBlank() || otherUid.isBlank()) return
        viewModelScope.launch {
            try {
                hiddenDao.hide(ChatHiddenEntity(chatId = chatRepository.getChatId(me, otherUid)))
                _isHidden.value = true
            } catch (e: Exception) {
                Log.w(TAG, "Could not hide conversation", e)
            }
        }
    }

    /** Undo for [hideConversation] — a new message in the thread re-creates the chat doc. */
    fun restoreConversation() {
        val me = currentUid
        if (me.isBlank() || otherUid.isBlank()) return
        viewModelScope.launch {
            hiddenDao.unhide(chatRepository.getChatId(me, otherUid))
            _isHidden.value = false
        }
    }
}
