package com.verbigem.app.ui.screens.translator

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.verbigem.app.data.local.AppDatabase
import com.verbigem.app.data.local.PreferencesManager
import com.verbigem.app.data.model.EngineChoice
import com.verbigem.app.data.model.LangCode
import com.verbigem.app.data.model.ModelDownloadState
import com.verbigem.app.data.model.TtsConfig
import com.verbigem.app.data.model.TranslationHistory
import com.verbigem.app.data.repository.HistoryRepository
import com.verbigem.app.data.repository.ProTtsRepository
import com.verbigem.app.data.repository.SyncManager
import com.verbigem.app.engine.HyMt2NativeEngine
import com.verbigem.app.engine.ModelDownloader
import com.verbigem.app.engine.OnlineApiEngine
import com.verbigem.app.engine.ProTtsEngine
import com.verbigem.app.engine.SpeechManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TranslatorViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesManager = PreferencesManager(application)
    private val historyRepository = HistoryRepository(
        AppDatabase.getInstance(application).historyDao(),
        AppDatabase.getInstance(application).pendingDeleteDao()
    )
    val hyMt2Engine = HyMt2NativeEngine(application)
    val modelDownloader = ModelDownloader(application)
    val speechManager = SpeechManager(application)
    val onlineEngine = OnlineApiEngine()
    val proTtsEngine = ProTtsEngine(application)
    private val proTtsRepository = ProTtsRepository(application)

    // Infinite-scroll history (newest-first, offset-paged from Room so we never
    // load the whole table). loadMoreHistory() appends the next page.
    private val _historyItems = MutableStateFlow<List<TranslationHistory>>(emptyList())
    val historyItems: StateFlow<List<TranslationHistory>> = _historyItems.asStateFlow()
    private var historyLoadedCount = 0
    private var historyExhausted = false

    fun loadMoreHistory() {
        if (historyExhausted) return
        viewModelScope.launch {
            val page = historyRepository.getPage(historyLoadedCount, HISTORY_PAGE_SIZE)
            if (page.isEmpty()) {
                historyExhausted = true
            } else {
                historyLoadedCount += page.size
                _historyItems.value = _historyItems.value + page
            }
        }
    }

    fun resetHistory() {
        historyLoadedCount = 0
        historyExhausted = false
        _historyItems.value = emptyList()
        loadMoreHistory()
    }

    init {
        resetHistory()
    }

    companion object {
        private const val HISTORY_PAGE_SIZE = 20
    }

    val downloadState: StateFlow<ModelDownloadState> = modelDownloader.downloadState

    private val _sourceLang = MutableStateFlow(LangCode.PL)
    val sourceLang: StateFlow<LangCode> = _sourceLang.asStateFlow()

    private val _targetLang = MutableStateFlow(LangCode.EN)
    val targetLang: StateFlow<LangCode> = _targetLang.asStateFlow()

    private val _engineChoice = MutableStateFlow(EngineChoice.LOCAL_FAST)
    val engineChoice: StateFlow<EngineChoice> = _engineChoice.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _primaryResult = MutableStateFlow("")
    val primaryResult: StateFlow<String> = _primaryResult.asStateFlow()

    private val _secondaryResult = MutableStateFlow("")
    val secondaryResult: StateFlow<String> = _secondaryResult.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Per-item speaking state: tracks WHICH history row is currently being read, so only
    // that card shows the animation (not every card when any row is read).
    private val _speakingSyncId = MutableStateFlow<String?>(null)
    val speakingSyncId: StateFlow<String?> = _speakingSyncId.asStateFlow()

    private val _speakingProSyncId = MutableStateFlow<String?>(null)
    val speakingProSyncId: StateFlow<String?> = _speakingProSyncId.asStateFlow()

    // Result-card (non-history) speaking state, keyed by nothing (single active result).
    private val _resultSpeaking = MutableStateFlow(false)
    val resultSpeaking: StateFlow<Boolean> = _resultSpeaking.asStateFlow()
    private val _resultSpeakingPro = MutableStateFlow(false)
    val resultSpeakingPro: StateFlow<Boolean> = _resultSpeakingPro.asStateFlow()

    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Speech-to-text (push-to-talk in the translator input bar).
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _listeningText = MutableStateFlow("")
    val listeningText: StateFlow<String> = _listeningText.asStateFlow()

    private val _showDownloadDialog = MutableStateFlow(false)
    val showDownloadDialog: StateFlow<Boolean> = _showDownloadDialog.asStateFlow()

    private val syncManager = SyncManager(getApplication())

    // Cached paid TTS config (OpenRouter). Loaded from Room; refreshed from Firestore on startup.
    private var ttsConfig: TtsConfig = TtsConfig()

    init {
        viewModelScope.launch {
            preferencesManager.srcLangFlow.collect { _sourceLang.value = LangCode.fromCode(it) }
        }
        viewModelScope.launch {
            preferencesManager.dstLangFlow.collect { _targetLang.value = LangCode.fromCode(it) }
        }
        viewModelScope.launch {
            preferencesManager.engineFlow.collect { _engineChoice.value = EngineChoice.fromId(it) }
        }
        speechManager.onSpeakingStateChanged = { speaking ->
            if (!speaking) _speakingSyncId.value = null
        }
        proTtsEngine.onSpeakingStateChanged = { speaking ->
            if (!speaking) _speakingProSyncId.value = null
        }
        viewModelScope.launch {
            ttsConfig = proTtsRepository.getConfig()
        }
    }

    /** Called by the navigation layer once the signed-in user's profile is known. */
    fun setPro(isPro: Boolean) {
        _isPro.value = isPro
    }

    /** Reload the cached TTS config after a Firestore sync completed. */
    fun refreshTtsConfig() {
        viewModelScope.launch {
            ttsConfig = proTtsRepository.getConfig()
        }
    }

    /** Push-to-talk: trzymasz przycisk → nagrywa, puszczasz → STT → append do inputText. */
    fun toggleListening() {
        if (_isListening.value) {
            stopListening()
        } else {
            startListening(_sourceLang.value)
        }
    }

    /** Start speech recognition (must be called after RECORD_AUDIO permission is granted). */
    fun startListening(lang: LangCode = _sourceLang.value) {
        _errorMessage.value = null
        _listeningText.value = ""
        _isListening.value = true

        speechManager.startListening(
            lang = lang,
            onInterim = { text -> _listeningText.value = text },
            onFinal = { text ->
                _isListening.value = false
                _listeningText.value = ""
                if (text.isNotBlank()) {
                    // Append to existing input (don't overwrite).
                    val current = _inputText.value
                    val separator = if (current.isNotBlank() && !current.endsWith(" ")) " " else ""
                    onInputChanged(current + separator + text)
                }
            },
            onError = { error ->
                _isListening.value = false
                _listeningText.value = ""
                _errorMessage.value = error
            }
        )
    }

    /** Stop speech recognition (called on button release or error). */
    fun stopListening() {
        speechManager.stopListening()
        _isListening.value = false
        _listeningText.value = ""
    }

    fun onInputChanged(text: String) {
        _inputText.value = text
    }

    fun clearInput() {
        _inputText.value = ""
    }

    fun clearResult() {
        _primaryResult.value = ""
        _secondaryResult.value = ""
        _errorMessage.value = null
    }

    fun setSourceLang(lang: LangCode) {
        _sourceLang.value = lang
        viewModelScope.launch { preferencesManager.setPair(lang.code, _targetLang.value.code) }
    }

    fun setTargetLang(lang: LangCode) {
        _targetLang.value = lang
        viewModelScope.launch { preferencesManager.setPair(_sourceLang.value.code, lang.code) }
    }

    fun swapLanguages() {
        val currentSrc = _sourceLang.value
        val currentDst = _targetLang.value
        _sourceLang.value = currentDst
        _targetLang.value = currentSrc
        viewModelScope.launch { preferencesManager.setPair(currentDst.code, currentSrc.code) }
    }

    fun setEngine(choice: EngineChoice) {
        _engineChoice.value = choice
        viewModelScope.launch { preferencesManager.setEngine(choice.id) }
    }

    fun setShowDownloadDialog(show: Boolean) {
        _showDownloadDialog.value = show
    }

    fun startModelDownload(isAccurate: Boolean = false) {
        viewModelScope.launch {
            modelDownloader.downloadModel(isAccurate)
        }
    }

    fun translate() {
        val text = _inputText.value.trim()
        if (text.isBlank() || _isLoading.value) return

        // Jeśli język źródłowy == docelowy, nie ma co tłumaczyć — zwracamy oryginał.
        if (_sourceLang.value == _targetLang.value) {
            _primaryResult.value = text
            viewModelScope.launch {
                addHistoryAndSync(text, text, _sourceLang.value.code, _targetLang.value.code)
            }
            return
        }

        val engine = _engineChoice.value

        // Sprawdzenie obecności modelu offline
        if (engine == EngineChoice.LOCAL_FAST || engine == EngineChoice.LOCAL_ACCURATE || engine == EngineChoice.BOTH) {
            val isAccurate = engine == EngineChoice.LOCAL_ACCURATE
            if (!HyMt2NativeEngine.isModelDownloaded(getApplication(), isAccurate)) {
                _showDownloadDialog.value = true
                return
            }
        }

        _isLoading.value = true
        _errorMessage.value = null
        _primaryResult.value = ""
        _secondaryResult.value = ""

        viewModelScope.launch {
            try {
                when (engine) {
                    EngineChoice.LOCAL_FAST -> {
                        val result = hyMt2Engine.translateSegmented(text, _sourceLang.value, _targetLang.value, isAccurate = false) { partial ->
                            _primaryResult.value = partial
                        }
                        _primaryResult.value = result
                        addHistoryAndSync(text, result, _sourceLang.value.code, _targetLang.value.code)
                    }
                    EngineChoice.LOCAL_ACCURATE -> {
                        val result = hyMt2Engine.translateSegmented(text, _sourceLang.value, _targetLang.value, isAccurate = true) { partial ->
                            _primaryResult.value = partial
                        }
                        _primaryResult.value = result
                        addHistoryAndSync(text, result, _sourceLang.value.code, _targetLang.value.code)
                    }
                    EngineChoice.BOTH -> {
                        val resFast = hyMt2Engine.translateSegmented(text, _sourceLang.value, _targetLang.value, isAccurate = false) { partial ->
                            _primaryResult.value = partial
                        }
                        val resAcc = hyMt2Engine.translateSegmented(text, _sourceLang.value, _targetLang.value, isAccurate = true) { partial ->
                            _secondaryResult.value = partial
                        }
                        _primaryResult.value = resFast
                        _secondaryResult.value = resAcc
                        addHistoryAndSync(text, resFast, _sourceLang.value.code, _targetLang.value.code)
                    }
                    EngineChoice.ONLINE -> {
                        val result = onlineEngine.translate(text, _sourceLang.value, _targetLang.value)
                        _primaryResult.value = result
                        addHistoryAndSync(text, result, _sourceLang.value.code, _targetLang.value.code)
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Wystąpił błąd podczas tłumaczenia"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Read the result card text. Uses the supplied [lang] (the result's target language). */
    fun speak(text: String, lang: LangCode) {
        _resultSpeaking.value = true
        speechManager.onSpeakingStateChanged = { speaking ->
            _resultSpeaking.value = speaking
            if (!speaking) _speakingSyncId.value = null
        }
        speechManager.speak(text, lang)
    }

    /** Paid "Read Pro" via OpenRouter TTS for the result card. */
    fun speakPro(text: String, lang: LangCode) {
        if (!_isPro.value || text.isBlank()) return
        if (!ttsConfig.isConfigured) {
            _errorMessage.value = "Read Pro is not configured"
            return
        }
        _resultSpeakingPro.value = true
        proTtsEngine.onSpeakingStateChanged = { speaking ->
            _resultSpeakingPro.value = speaking
            if (!speaking) _speakingProSyncId.value = null
        }
        viewModelScope.launch {
            try {
                proTtsEngine.speak(text, lang, ttsConfig)
            } catch (e: Exception) {
                _resultSpeakingPro.value = false
                _errorMessage.value = e.localizedMessage ?: "Read Pro failed"
            }
        }
    }

    /** Read a history row in ITS OWN target language (not the current UI target). */
    fun speakHistory(item: TranslationHistory) {
        _speakingSyncId.value = item.syncId
        speechManager.onSpeakingStateChanged = { speaking ->
            if (!speaking) _speakingSyncId.value = null
        }
        speechManager.speak(item.translatedText, LangCode.fromCode(item.targetLang))
    }

    /** Paid "Read Pro" for a history row in ITS OWN target language. */
    fun speakProHistory(item: TranslationHistory) {
        if (!_isPro.value || item.translatedText.isBlank()) return
        if (!ttsConfig.isConfigured) {
            _errorMessage.value = "Read Pro is not configured"
            return
        }
        _speakingProSyncId.value = item.syncId
        proTtsEngine.onSpeakingStateChanged = { speaking ->
            if (!speaking) _speakingProSyncId.value = null
        }
        viewModelScope.launch {
            try {
                proTtsEngine.speak(item.translatedText, LangCode.fromCode(item.targetLang), ttsConfig)
            } catch (e: Exception) {
                _speakingProSyncId.value = null
                _errorMessage.value = e.localizedMessage ?: "Read Pro failed"
            }
        }
    }

    /**
     * Deletes a history row locally (physical delete) and queues a Firestore tombstone so the
     * deletion propagates to other devices. A sync is kicked off immediately when online so the
     * tombstone goes out without waiting for the next app restart.
     */
    fun deleteHistory(item: TranslationHistory) {
        viewModelScope.launch {
            historyRepository.deleteHistory(item)
            resetHistory()
            try {
                syncManager.syncNow()
            } catch (_: Exception) {
                // Offline or transient failure: the tombstone stays queued in pending_deletes
                // and will be pushed on the next successful sync.
            }
        }
    }

    /**
     * Adds a history row locally and triggers a Firestore sync (when online) so the new translation
     * propagates to other devices without waiting for the next app restart.
     */
    private fun addHistoryAndSync(sourceText: String, translatedText: String, sourceLang: String, targetLang: String) {
        viewModelScope.launch {
            historyRepository.addHistory(sourceText, translatedText, sourceLang, targetLang)
            resetHistory()
            try {
                syncManager.syncNow()
            } catch (_: Exception) {
                // Offline: the row will be pushed on the next connectivity-driven sync.
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        hyMt2Engine.release()
        speechManager.release()
        proTtsEngine.release()
    }
}
