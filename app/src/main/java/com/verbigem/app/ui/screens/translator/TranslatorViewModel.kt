package com.verbigem.app.ui.screens.translator

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.verbigem.app.data.local.AppDatabase
import com.verbigem.app.data.local.PreferencesManager
import com.verbigem.app.data.model.EngineChoice
import com.verbigem.app.data.model.LangCode
import com.verbigem.app.data.model.ModelDownloadState
import com.verbigem.app.data.model.TranslationHistory
import com.verbigem.app.data.repository.HistoryRepository
import com.verbigem.app.engine.HyMt2NativeEngine
import com.verbigem.app.engine.ModelDownloader
import com.verbigem.app.engine.OnlineApiEngine
import com.verbigem.app.engine.SpeechManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TranslatorViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesManager = PreferencesManager(application)
    private val historyRepository = HistoryRepository(AppDatabase.getInstance(application).historyDao())
    val hyMt2Engine = HyMt2NativeEngine(application)
    val modelDownloader = ModelDownloader(application)
    val speechManager = SpeechManager(application)
    val onlineEngine = OnlineApiEngine()

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

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _showDownloadDialog = MutableStateFlow(false)
    val showDownloadDialog: StateFlow<Boolean> = _showDownloadDialog.asStateFlow()

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
    }

    fun onInputChanged(text: String) {
        _inputText.value = text
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
                        val result = hyMt2Engine.translate(text, _sourceLang.value, _targetLang.value, isAccurate = false)
                        _primaryResult.value = result
                        historyRepository.addHistory(text, result, _sourceLang.value.code, _targetLang.value.code)
                    }
                    EngineChoice.LOCAL_ACCURATE -> {
                        val result = hyMt2Engine.translate(text, _sourceLang.value, _targetLang.value, isAccurate = true)
                        _primaryResult.value = result
                        historyRepository.addHistory(text, result, _sourceLang.value.code, _targetLang.value.code)
                    }
                    EngineChoice.BOTH -> {
                        val resFast = hyMt2Engine.translate(text, _sourceLang.value, _targetLang.value, isAccurate = false)
                        val resAcc = hyMt2Engine.translate(text, _sourceLang.value, _targetLang.value, isAccurate = true)
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
        speechManager.speak(text, lang)
    }

    override fun onCleared() {
        super.onCleared()
        hyMt2Engine.release()
        speechManager.release()
    }
}
