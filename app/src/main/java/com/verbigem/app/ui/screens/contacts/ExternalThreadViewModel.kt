package com.verbigem.app.ui.screens.contacts

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.verbigem.app.data.OutboundChannel
import com.verbigem.app.data.local.ExternalContactEntity
import com.verbigem.app.data.local.ExternalOutboxEntity
import com.verbigem.app.data.model.LangCode
import com.verbigem.app.data.repository.AuthRepository
import com.verbigem.app.data.repository.ExternalThreadRepository
import com.verbigem.app.engine.HyMt2NativeEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One-way thread with somebody who does not have Verbigem (3.6).
 *
 * The shape of the flow is deliberately the same as a real chat — write, translate,
 * send — but it ends in a hand-off instead of a message, and the screen says so out
 * loud. There is no incoming side: nothing here can ever come back.
 */
class ExternalThreadViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ExternalThreadViewModel"
    }

    private val repository = ExternalThreadRepository(application)
    private val authRepository = AuthRepository()
    private val hyMt2Engine = HyMt2NativeEngine(application)

    private val _contact = MutableStateFlow<ExternalContactEntity?>(null)
    val contact: StateFlow<ExternalContactEntity?> = _contact.asStateFlow()

    private val _history = MutableStateFlow<List<ExternalOutboxEntity>>(emptyList())
    val history: StateFlow<List<ExternalOutboxEntity>> = _history.asStateFlow()

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    /** Empty until translated. Shown above the channel buttons. */
    private val _translation = MutableStateFlow("")
    val translation: StateFlow<String> = _translation.asStateFlow()

    private val _isTranslating = MutableStateFlow(false)
    val isTranslating: StateFlow<Boolean> = _isTranslating.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Language I speak — the source of the translation. */
    private val _sourceLang = MutableStateFlow(LangCode.PL)
    val sourceLang: StateFlow<LangCode> = _sourceLang.asStateFlow()

    private var loadJob: Job? = null

    /**
     * Loads one thread. Safe to call again: the previous load is cancelled first, so
     * a second `phone` cannot leave the old history collector writing into state
     * that now belongs to somebody else.
     */
    fun load(phone: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            launch {
                // Porządek przy wejściu, nie w tle: czyszczenie jest tanie, a nikt
                // nie pamięta, żeby robić to ręcznie.
                repository.pruneHistory()
                _contact.value = repository.contact(phone)
            }
            launch {
                repository.watchHistory(phone).collect { _history.value = it }
            }
            launch {
                val uid = authRepository.currentUser?.uid
                if (uid.isNullOrBlank()) return@launch
                authRepository.watchProfile(uid).collect { profile ->
                    // `speakLangSource` is what I speak; the target is per contact and
                    // chosen in the thread, because an external contact has no profile
                    // to read it from.
                    _sourceLang.value = LangCode.fromCode(profile?.speakLangSource.orEmpty())
                }
            }
        }
    }

    fun onDraftChanged(value: String) {
        _draft.value = value
        // A changed draft invalidates the translation — otherwise the user hands
        // over text that no longer matches what they typed.
        if (_translation.value.isNotBlank()) _translation.value = ""
        _error.value = null
    }

    fun setTargetLang(lang: LangCode) {
        val phone = _contact.value?.phone ?: return
        viewModelScope.launch {
            repository.setLang(phone, lang.code)
            _contact.value = repository.contact(phone)
            _translation.value = ""
        }
    }

    /**
     * Translates the draft into the contact's language.
     *
     * Separate from sending on purpose: the user must SEE what is about to leave the
     * phone in a language they may not read. Handing off an untranslated message
     * because a button was tapped too early is the one mistake this screen cannot
     * undo — there is no recall on an SMS.
     */
    fun translate() {
        val text = _draft.value.trim()
        val target = targetLang()
        if (text.isBlank() || target == null) return

        _isTranslating.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                _translation.value = hyMt2Engine.translate(text, _sourceLang.value, target)
            } catch (e: Exception) {
                Log.w(TAG, "Translation failed", e)
                _error.value = e.message
                _translation.value = ""
            } finally {
                _isTranslating.value = false
            }
        }
    }

    /**
     * Hands the translation to [channel] and records it — in that order.
     *
     * @return the text that left the phone, or null when nothing took it.
     */
    suspend fun handOff(channel: OutboundChannel): String? {
        val entity = _contact.value ?: return null
        val text = _translation.value.ifBlank { _draft.value.trim() }
        if (text.isBlank()) return null

        val context = getApplication<Application>()
        val ok = channel.handOff(context, repository.targetFor(entity), text, "")
        if (!ok) {
            _error.value = context.getString(com.verbigem.app.R.string.channel_none_available)
            return null
        }

        repository.recordHandOff(
            phone = entity.phone,
            channelId = channel.id,
            originalText = _draft.value.trim(),
            translatedText = text,
            lang = targetLang()?.code.orEmpty()
        )
        _draft.value = ""
        _translation.value = ""
        return text
    }

    /**
     * Null until the user picks a language — we cannot guess it for someone with no
     * profile. `fromCode` would silently fall back to Polish, which is worse than
     * asking: it would translate into the wrong language and hand it off.
     */
    private fun targetLang(): LangCode? {
        val code = _contact.value?.lang
        if (code.isNullOrBlank()) return null
        return LangCode.fromCode(code)
    }

    override fun onCleared() {
        hyMt2Engine.release()
        super.onCleared()
    }
}
