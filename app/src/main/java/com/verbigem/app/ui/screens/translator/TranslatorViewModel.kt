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

    val historyList: StateFlow<List<TranslationHistory>> = historyRepository.allHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isSpeakingPro = MutableStateFlow(false)
    val isSpeakingPro: StateFlow<Boolean> = _isSpeakingPro.asStateFlow()

    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

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
            _isSpeaking.value = speaking
        }
        proTtsEngine.onSpeakingStateChanged = { speaking ->
            _isSpeakingPro.value = speaking
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
                historyRepository.addHistory(text, text, _sourceLang.value.code, _targetLang.value.code)
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
                        val result = hyMt2Engine.translate(text, _sourceLang.value, _targetLang.value, isAccurate = false) { partial ->
                            _primaryResult.value = partial
                        }
                        _primaryResult.value = result
                        historyRepository.addHistory(text, result, _sourceLang.value.code, _targetLang.value.code)
                    }
                    EngineChoice.LOCAL_ACCURATE -> {
                        val result = hyMt2Engine.translate(text, _sourceLang.value, _targetLang.value, isAccurate = true) { partial ->
                            _primaryResult.value = partial
                        }
                        _primaryResult.value = result
                        historyRepository.addHistory(text, result, _sourceLang.value.code, _targetLang.value.code)
                    }
                    EngineChoice.BOTH -> {
                        val resFast = hyMt2Engine.translate(text, _sourceLang.value, _targetLang.value, isAccurate = false) { partial ->
                            _primaryResult.value = partial
                        }
                        val resAcc = hyMt2Engine.translate(text, _sourceLang.value, _targetLang.value, isAccurate = true) { partial ->
                            _secondaryResult.value = partial
                        }
                        _primaryResult.value = resFast
                        _secondaryResult.value = resAcc
                        historyRepository.addHistory(text, resFast, _sourceLang.value.code, _targetLang.value.code)
                    }
                    EngineChoice.ONLINE -> {
                        val result = onlineEngine.translate(text, _sourceLang.value, _targetLang.value)
                        _primaryResult.value = result
                        historyRepository.addHistory(text, result, _sourceLang.value.code, _targetLang.value.code)
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Wystąpił błąd podczas tłumaczenia"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun speak(text: String, lang: LangCode) {
        _isSpeaking.value = true
        speechManager.speak(text, lang)
    }

    /** Paid "Read Pro" via OpenRouter TTS. No-op if the config is not set or user is not Pro. */
    fun speakPro(text: String, lang: LangCode) {
        if (!_isPro.value || text.isBlank()) return
        if (!ttsConfig.isConfigured) {
            _errorMessage.value = "Read Pro is not configured"
            return
        }
        viewModelScope.launch {
            try {
                proTtsEngine.speak(text, lang, ttsConfig)
            } catch (e: Exception) {
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
            try {
                syncManager.syncNow()
            } catch (_: Exception) {
                // Offline or transient failure: the tombstone stays queued in pending_deletes
                // and will be pushed on the next successful sync.
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
