package com.verbigem.app.ui.screens.chat

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.verbigem.app.data.ConnectivityObserver
import com.verbigem.app.data.local.AppDatabase
import com.verbigem.app.data.local.ChatDeletedEntity
import com.verbigem.app.data.local.ChatOutboxEntity
import com.verbigem.app.data.local.ChatReadEntity
import com.verbigem.app.data.local.ChatTranslationEntity
import com.verbigem.app.data.model.ChatMessage
import com.verbigem.app.data.model.ContactSettings
import com.verbigem.app.data.model.LangCode
import com.verbigem.app.data.model.PublicProfile
import com.verbigem.app.data.repository.AuthRepository
import com.verbigem.app.data.repository.ChatRepository
import com.verbigem.app.data.repository.ProTtsRepository
import com.verbigem.app.engine.HyMt2NativeEngine
import com.verbigem.app.engine.ProTtsEngine
import com.verbigem.app.engine.SpeechManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

enum class BubbleStatus { SENT, SENDING, FAILED }

/**
 * One message as the thread renders it.
 *
 * Remote messages and not-yet-sent outbox rows are flattened into the same shape so
 * the UI has a single list to lay out: a message you just sent shows up instantly
 * (status SENDING) and simply changes status when the flush succeeds.
 */
data class ChatBubble(
    val id: String,
    val text: String,
    val sourceLang: String,
    val isMine: Boolean,
    val createdAt: Long,
    val status: BubbleStatus,
    val hintText: String = "",
    val hintLang: String = ""
)

class ChatThreadViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ChatThreadViewModel"
        /** Minimum gap between two "I am typing" writes to Firestore. */
        private const val TYPING_REFRESH_MS = 4_000L
    }

    private val authRepository = AuthRepository()
    private val chatRepository = ChatRepository()
    private val hyMt2Engine = HyMt2NativeEngine(application)
    private val speechManager = SpeechManager(application)
    private val proTtsEngine = ProTtsEngine(application)
    private val proTtsRepository = ProTtsRepository(application)
    private val connectivity = ConnectivityObserver(application)

    private val db = AppDatabase.getInstance(application)
    private val translationDao = db.chatTranslationDao()
    private val outboxDao = db.chatOutboxDao()
    private val readDao = db.chatReadDao()
    private val deletedDao = db.chatDeletedDao()

    /** The engine owns one native model handle; two concurrent loads would double the RAM. */
    private val translationMutex = Mutex()

    private var chatId: String? = null
    private val requested = mutableSetOf<String>()
    private var lastReadWritten = 0L
    private var lastTypingWrite = 0L
    private var typingStopJob: Job? = null
    private var flushing = false
    private var olderExhausted = false

    /**
     * Auto-translation is switched off by the FIRST failure. In practice a failure
     * means the model is not downloaded, and re-attempting it for every message in a
     * long thread would just drain the battery — the per-bubble retry button stays
     * available either way.
     */
    private var autoTranslate = true

    private val _otherUid = MutableStateFlow<String?>(null)
    val otherUid: StateFlow<String?> = _otherUid.asStateFlow()

    private val _otherProfile = MutableStateFlow<PublicProfile?>(null)
    val otherProfile: StateFlow<PublicProfile?> = _otherProfile.asStateFlow()

    private val _myLang = MutableStateFlow(LangCode.PL)
    val myLang: StateFlow<LangCode> = _myLang.asStateFlow()

    /** Language of the other side — the target for our outgoing sender-hint. */
    private val _otherLang = MutableStateFlow(LangCode.EN)
    val otherLang: StateFlow<LangCode> = _otherLang.asStateFlow()

    /**
     * Per-contact settings, including the alias shown in the header and the
     * language override. See [translationLang] for how the override is applied.
     */
    private val _contactSettings = MutableStateFlow(ContactSettings.EMPTY)
    val contactSettings: StateFlow<ContactSettings> = _contactSettings.asStateFlow()

    /**
     * The language incoming messages are translated INTO: the per-contact override
     * if one is set, otherwise my profile language.
     *
     * This is deliberately NOT the language my outgoing messages are tagged with.
     * That one has to describe what I actually typed, or the receiver would
     * translate from the wrong source language.
     *
     * Started EAGERLY, not WhileSubscribed: [enqueueTranslations] and [retranslate]
     * read `.value` from inside the ViewModel. A lazily-started StateFlow that has
     * no subscriber yet silently hands back its initial value, which would make the
     * thread translate into Polish regardless of the profile or the override.
     */
    val translationLang: StateFlow<LangCode> =
        combine(_myLang, _contactSettings) { mine, settings ->
            if (settings.langOverride.isBlank()) mine else LangCode.fromCode(settings.langOverride)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, LangCode.PL)

    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _translations = MutableStateFlow<Map<String, String>>(emptyMap())
    val translations: StateFlow<Map<String, String>> = _translations.asStateFlow()

    private val _translating = MutableStateFlow<Set<String>>(emptySet())
    val translating: StateFlow<Set<String>> = _translating.asStateFlow()

    private val _failed = MutableStateFlow<Set<String>>(emptySet())
    val failed: StateFlow<Set<String>> = _failed.asStateFlow()

    private val _showOriginal = MutableStateFlow<Set<String>>(emptySet())
    val showOriginal: StateFlow<Set<String>> = _showOriginal.asStateFlow()

    private val _readReceipts = MutableStateFlow<Map<String, Long>>(emptyMap())
    val readReceipts: StateFlow<Map<String, Long>> = _readReceipts.asStateFlow()

    private val _bubbles = MutableStateFlow<List<ChatBubble>>(emptyList())
    val bubbles: StateFlow<List<ChatBubble>> = _bubbles.asStateFlow()

    private val _canLoadMore = MutableStateFlow(false)
    val canLoadMore: StateFlow<Boolean> = _canLoadMore.asStateFlow()

    private val _loadingOlder = MutableStateFlow(false)
    val loadingOlder: StateFlow<Boolean> = _loadingOlder.asStateFlow()

    private val _tick = MutableStateFlow(0L)
    private val _typingUntil = MutableStateFlow<Map<String, Long>>(emptyMap())

    // Raw inputs, merged by recompute() into the single [bubbles] list the UI reads.
    private val _remoteMsg = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val _olderMsg = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val _pendingRows = MutableStateFlow<List<ChatOutboxEntity>>(emptyList())
    private var _deletedIds: Set<String> = emptySet()

    /** True while the other person's typing flag has not expired yet. */
    val otherTyping: StateFlow<Boolean> =
        combine(_typingUntil, _otherUid, _tick) { typing, other, now ->
            other != null && (typing[other] ?: 0L) > now
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val currentUid: String
        get() = authRepository.currentUser?.uid ?: ""

    init {
        val uid = currentUid
        if (uid.isNotBlank()) {
            viewModelScope.launch {
                authRepository.watchProfile(uid).collect { profile ->
                    profile?.let {
                        val lang = LangCode.fromCode(it.speakLangSource)
                        if (lang != _myLang.value) {
                            _myLang.value = lang
                            // Different target language: the in-memory map is no longer
                            // valid (Room rows are keyed by language, so they survive).
                            _translations.value = emptyMap()
                            autoTranslate = true
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            deletedDao.watchAll().collect { rows ->
                _deletedIds = rows.map { it.msgId }.toSet()
                recompute()
            }
        }
        // The network coming back is the moment to drain the outbox — this is what
        // makes a message written offline actually leave the phone.
        viewModelScope.launch {
            connectivity.isOnline.collect { if (it) flushOutbox() }
        }
        // Heartbeat so an expired "typing" flag disappears without a Firestore write.
        viewModelScope.launch {
            while (true) {
                _tick.value = System.currentTimeMillis()
                delay(1_500)
            }
        }
    }

    fun setPro(isPro: Boolean) {
        _isPro.value = isPro
    }

    /** Called once per thread; repeated calls with the same uid are ignored. */
    fun openThread(otherUid: String) {
        if (otherUid.isBlank() || _otherUid.value == otherUid) return
        val me = currentUid
        if (me.isBlank()) return

        _otherUid.value = otherUid
        _otherProfile.value = null
        _contactSettings.value = ContactSettings.EMPTY
        _showOriginal.value = emptySet()
        _remoteMsg.value = emptyList()
        _olderMsg.value = emptyList()
        _bubbles.value = emptyList()
        olderExhausted = false
        lastReadWritten = 0L

        val id = chatRepository.getChatId(me, otherUid)
        chatId = id

        viewModelScope.launch {
            chatRepository.watchLatestMessages(id).collect { _remoteMsg.value = it; recompute() }
        }
        viewModelScope.launch {
            outboxDao.watch(id).collect { _pendingRows.value = it; recompute() }
        }
        viewModelScope.launch {
            chatRepository.watchReadReceipts(id).collect { _readReceipts.value = it }
        }
        viewModelScope.launch {
            chatRepository.watchTyping(id).collect { _typingUntil.value = it }
        }
        viewModelScope.launch {
            authRepository.getPublicProfile(otherUid)?.let { public ->
                _otherProfile.value = public
                // Outgoing hints are translated into THEIR language (decision D1).
                _otherLang.value = LangCode.fromCode(public.speakLangSource)
            }
        }
        // Alias + per-contact translation language. Changing the language invalidates
        // the in-memory map exactly like a profile language change does (the Room
        // cache is keyed by language, so old rows survive untouched).
        viewModelScope.launch {
            chatRepository.watchContactSettings(me).collect { map ->
                val settings = map[otherUid] ?: ContactSettings.EMPTY
                val previousLang = _contactSettings.value.langOverride
                _contactSettings.value = settings
                if (settings.langOverride != previousLang) {
                    _translations.value = emptyMap()
                    requested.clear()
                    autoTranslate = true
                    recompute()
                }
            }
        }
        flushOutbox()
    }

    // ---------------------------------------------------------------- sending

    fun onInputChanged(text: String) {
        _inputText.value = text
        val id = chatId ?: return
        if (text.isBlank()) {
            viewModelScope.launch { stopTyping() }
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastTypingWrite > TYPING_REFRESH_MS) {
            lastTypingWrite = now
            viewModelScope.launch { chatRepository.setTyping(id, currentUid, true) }
        }
        typingStopJob?.cancel()
        typingStopJob = viewModelScope.launch {
            delay(ChatRepository.TYPING_TTL_MS - 1_000)
            stopTyping()
        }
    }

    /**
     * Queues the message locally and returns immediately — the network call (and the
     * Hy-MT2 hint translation, which needs the model) happens in [flushOutbox].
     */
    fun sendMessage() {
        val text = _inputText.value.trim()
        val id = chatId ?: return
        if (text.isBlank() || currentUid.isBlank()) return

        _inputText.value = ""
        val clientMsgId = UUID.randomUUID().toString()
        viewModelScope.launch {
            outboxDao.insert(
                ChatOutboxEntity(
                    clientMsgId = clientMsgId,
                    chatId = id,
                    text = text,
                    sourceLang = _myLang.value.code
                )
            )
            stopTyping()
            flushOutbox()
        }
    }

    /** Retries rows that failed before (attempts > 0). */
    fun retryFailed() {
        viewModelScope.launch {
            val id = chatId ?: return@launch
            outboxDao.all().filter { it.chatId == id && it.attempts > 0 }
                .forEach { outboxDao.updateStatus(it.clientMsgId, "pending", 0) }
            autoTranslate = true
            flushOutbox()
        }
    }

    /**
     * Drains the local outbox. Uses `set()` on a client-generated document id, so
     * running it twice (reconnect while the thread is open) cannot duplicate anything.
     */
    fun flushOutbox() {
        val id = chatId ?: return
        if (flushing) return
        flushing = true
        viewModelScope.launch {
            try {
                val rows = outboxDao.all().filter { it.chatId == id }
                for (row in rows) {
                    try {
                        val source = LangCode.fromCode(row.sourceLang)
                        val target = _otherLang.value
                        val hint = if (source == target) {
                            row.text
                        } else {
                            try {
                                translationMutex.withLock {
                                    hyMt2Engine.translateSegmented(row.text, source, target)
                                }
                            } catch (e: Exception) {
                                // No model (or it failed): send the original alone.
                                // The receiver translates it on their own device anyway.
                                Log.w(TAG, "Sender hint translation failed", e)
                                ""
                            }
                        }
                        chatRepository.sendMessage(
                            chatId = id,
                            authorId = currentUid,
                            text = row.text,
                            sourceLang = row.sourceLang,
                            hintLang = target.code,
                            hintText = hint,
                            clientMsgId = row.clientMsgId
                        )
                        outboxDao.delete(row.clientMsgId)
                    } catch (e: Exception) {
                        Log.w(TAG, "Send failed for ${row.clientMsgId}", e)
                        outboxDao.updateStatus(row.clientMsgId, "failed", row.attempts + 1)
                    }
                }
            } finally {
                flushing = false
            }
        }
    }

    // -------------------------------------------------------------- pagination

    fun loadOlder() {
        val id = chatId ?: return
        if (_loadingOlder.value || !_canLoadMore.value) return
        val oldest = (_olderMsg.value + _remoteMsg.value)
            .mapNotNull { it.createdAt }
            .minByOrNull { it.seconds } ?: return

        _loadingOlder.value = true
        viewModelScope.launch {
            try {
                val page = chatRepository.loadOlderMessages(id, oldest)
                if (page.size < chatRepository.pageSize) olderExhausted = true
                _olderMsg.value = (_olderMsg.value + page).distinctBy { it.id }
                recompute()
            } finally {
                _loadingOlder.value = false
            }
        }
    }

    // ------------------------------------------------- translation on the client

    /**
     * Decision D1: the RECEIVER translates, into their own language, on their own
     * device. The sender's `senderTranslation` is only the fallback shown while the
     * model runs (or when the receiver has no model at all).
     */
    private fun enqueueTranslations() {
        if (!autoTranslate) return
        val target = translationLang.value
        _bubbles.value.forEach { bubble ->
            if (bubble.isMine || bubble.status != BubbleStatus.SENT) return@forEach
            if (LangCode.fromCode(bubble.sourceLang) == target) return@forEach
            if (_translations.value.containsKey(bubble.id) || bubble.id in requested) return@forEach
            translateInto(bubble, target)
        }
    }

    private fun translateInto(bubble: ChatBubble, target: LangCode) {
        if (!requested.add(bubble.id)) return
        _translating.value = _translating.value + bubble.id
        _failed.value = _failed.value - bubble.id
        viewModelScope.launch {
            try {
                val cached = translationDao.get(bubble.id, target.code)
                if (cached != null) {
                    _translations.value = _translations.value + (bubble.id to cached.translatedText)
                } else {
                    val out = translationMutex.withLock {
                        hyMt2Engine.translateSegmented(
                            bubble.text,
                            LangCode.fromCode(bubble.sourceLang),
                            target
                        )
                    }
                    if (out.isNotBlank()) {
                        translationDao.upsert(
                            ChatTranslationEntity(
                                msgId = bubble.id,
                                targetLang = target.code,
                                chatId = chatId.orEmpty(),
                                translatedText = out
                            )
                        )
                        _translations.value = _translations.value + (bubble.id to out)
                    } else {
                        _failed.value = _failed.value + bubble.id
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Translation of ${bubble.id} failed", e)
                _failed.value = _failed.value + bubble.id
                autoTranslate = false
            } finally {
                requested.remove(bubble.id)
                _translating.value = _translating.value - bubble.id
            }
        }
    }

    /** Re-translates one message (menu / button). Clears the cached row first. */
    fun retranslate(msgId: String) {
        val bubble = _bubbles.value.firstOrNull { it.id == msgId } ?: return
        _translations.value = _translations.value - msgId
        _failed.value = _failed.value - msgId
        requested.remove(msgId)
        autoTranslate = true
        viewModelScope.launch {
            translationDao.deleteForMessage(msgId)
            translateInto(bubble, translationLang.value)
        }
    }

    // ------------------------------------------------------------- bubble actions

    fun toggleOriginal(msgId: String) {
        _showOriginal.value = if (msgId in _showOriginal.value) {
            _showOriginal.value - msgId
        } else {
            _showOriginal.value + msgId
        }
    }

    /** "Delete for me": a local tombstone. Other devices keep the message. */
    fun deleteForMe(msgId: String) {
        viewModelScope.launch {
            deletedDao.insert(ChatDeletedEntity(msgId = msgId))
            _translations.value = _translations.value - msgId
            translationDao.deleteForMessage(msgId)
            _showOriginal.value = _showOriginal.value - msgId
        }
    }

    fun quote(msgId: String) {
        val bubble = _bubbles.value.firstOrNull { it.id == msgId } ?: return
        _inputText.value = "„${bubble.text}” "
    }

    /** Reads the message in the language it is currently DISPLAYED in. */
    fun speak(text: String, langCode: String) {
        speechManager.speak(text, LangCode.fromCode(langCode))
    }

    fun speakPro(text: String, langCode: String) {
        if (!_isPro.value || text.isBlank()) return
        viewModelScope.launch {
            try {
                val config = proTtsRepository.getConfig()
                proTtsEngine.speak(text, LangCode.fromCode(langCode), config)
            } catch (e: Exception) {
                Log.w(TAG, "Pro TTS failed", e)
            }
        }
    }

    // ------------------------------------------------------------------ internal

    private fun recompute() {
        val me = currentUid
        val merged = LinkedHashMap<String, ChatMessage>()
        (_olderMsg.value + _remoteMsg.value).forEach { msg ->
            if (msg.id !in _deletedIds) merged[msg.id] = msg
        }
        val remoteBubbles = merged.values.map { msg ->
            ChatBubble(
                id = msg.id,
                text = msg.text,
                sourceLang = msg.sourceLang,
                isMine = msg.authorId == me,
                createdAt = msg.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
                status = BubbleStatus.SENT,
                hintText = msg.hintText(),
                hintLang = msg.hintLang()
            )
        }
        val pendingBubbles = _pendingRows.value.map { row ->
            ChatBubble(
                id = row.clientMsgId,
                text = row.text,
                sourceLang = row.sourceLang,
                isMine = true,
                createdAt = row.createdAt,
                status = if (row.attempts > 0) BubbleStatus.FAILED else BubbleStatus.SENDING
            )
        }
        _bubbles.value = (remoteBubbles + pendingBubbles).sortedBy { it.createdAt }
        _canLoadMore.value = _remoteMsg.value.size >= chatRepository.pageSize && !olderExhausted
        enqueueTranslations()
        maybeMarkRead()
    }

    /**
     * Writes the read watermark twice: into Room (drives the inbox unread dot, costs
     * nothing) and into Firestore only when it actually moved forward, so the other
     * side can show the second tick.
     */
    private fun maybeMarkRead() {
        val id = chatId ?: return
        val me = currentUid
        if (me.isBlank()) return
        val newestIncoming = _bubbles.value
            .filter { !it.isMine && it.status == BubbleStatus.SENT }
            .maxOfOrNull { it.createdAt } ?: return
        if (newestIncoming <= lastReadWritten) return
        lastReadWritten = newestIncoming
        viewModelScope.launch {
            readDao.upsert(ChatReadEntity(chatId = id, lastReadAt = newestIncoming))
            if (newestIncoming > (_readReceipts.value[me] ?: 0L)) {
                chatRepository.markRead(id, me, newestIncoming)
            }
        }
    }

    private suspend fun stopTyping() {
        typingStopJob?.cancel()
        typingStopJob = null
        lastTypingWrite = 0L
        val id = chatId ?: return
        chatRepository.setTyping(id, currentUid, false)
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch { stopTyping() }
        hyMt2Engine.release()
        speechManager.release()
        proTtsEngine.release()
    }
}
