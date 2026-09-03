package com.verbigem.app.ui.screens.conversation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.verbigem.app.data.local.PreferencesManager
import com.verbigem.app.data.model.LangCode
import com.verbigem.app.engine.HyMt2NativeEngine
import com.verbigem.app.engine.SpeechManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ConvSide { SIDE_A, SIDE_B }

class ConversationViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesManager = PreferencesManager(application)
    val hyMt2Engine = HyMt2NativeEngine(application)
    val speechManager = SpeechManager(application)

    private val _langA = MutableStateFlow(LangCode.PL)
    val langA: StateFlow<LangCode> = _langA.asStateFlow()

    private val _langB = MutableStateFlow(LangCode.EN)
    val langB: StateFlow<LangCode> = _langB.asStateFlow()

    private val _currentSide = MutableStateFlow(ConvSide.SIDE_A)
    val currentSide: StateFlow<ConvSide> = _currentSide.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _interimSpeech = MutableStateFlow("")
    val interimSpeech: StateFlow<String> = _interimSpeech.asStateFlow()

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

    private val _translatedResult = MutableStateFlow<String?>(null)
    val translatedResult: StateFlow<String?> = _translatedResult.asStateFlow()

    private val _resultLang = MutableStateFlow(LangCode.EN)
    val resultLang: StateFlow<LangCode> = _resultLang.asStateFlow()

    private val _isTranslating = MutableStateFlow(false)
    val isTranslating: StateFlow<Boolean> = _isTranslating.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _textInput = MutableStateFlow("")
    val textInput: StateFlow<String> = _textInput.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesManager.srcLangFlow.collect { _langA.value = LangCode.fromCode(it) }
        }
        viewModelScope.launch {
            preferencesManager.dstLangFlow.collect { _langB.value = LangCode.fromCode(it) }
        }
    }

    fun setLangA(lang: LangCode) {
        _langA.value = lang
    }

    fun setLangB(lang: LangCode) {
        _langB.value = lang
    }

    fun setSide(side: ConvSide) {
        _currentSide.value = side
    }

    fun onTextInputChanged(text: String) {
        _textInput.value = text
    }

    fun toggleSpeechRecognition() {
        if (_isListening.value) {
            speechManager.stopListening()
            _isListening.value = false
            _interimSpeech.value = ""
        } else {
            val speakingLang = if (_currentSide.value == ConvSide.SIDE_A) _langA.value else _langB.value
            _errorMessage.value = null
            _interimSpeech.value = ""
            _isListening.value = true

            speechManager.startListening(
                lang = speakingLang,
                onInterim = { text -> _interimSpeech.value = text },
                onFinal = { text ->
                    _isListening.value = false
                    _interimSpeech.value = ""
                    _recognizedText.value = text
                    // Append rozpoznanego tekstu do pola tekstowego (jak w translatorze).
                    if (text.isNotBlank()) {
                        val current = _textInput.value
                        val separator = if (current.isNotBlank() && !current.endsWith(" ")) " " else ""
                        _textInput.value = current + separator + text
                    }
                    translateAndSpeak(text)
                },
                onError = { error ->
                    _isListening.value = false
                    _errorMessage.value = error
                }
            )
        }
    }

    fun sendTextMessage() {
        val text = _textInput.value.trim()
        if (text.isBlank()) return
        _textInput.value = ""
        translateAndSpeak(text)
    }

    private fun translateAndSpeak(text: String) {
        val fromLang = if (_currentSide.value == ConvSide.SIDE_A) _langA.value else _langB.value
        val toLang = if (_currentSide.value == ConvSide.SIDE_A) _langB.value else _langA.value

        _isTranslating.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val translation = hyMt2Engine.translate(text, fromLang, toLang)
                _translatedResult.value = translation
                _resultLang.value = toLang

                // Auto-read via TTS in recipient's language
                speechManager.speak(translation, toLang)

                // Flip side
                _currentSide.value = if (_currentSide.value == ConvSide.SIDE_A) ConvSide.SIDE_B else ConvSide.SIDE_A
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Błąd tłumaczenia w trybie rozmowy"
            } finally {
                _isTranslating.value = false
            }
        }
    }

    fun speakAgain() {
        val text = _translatedResult.value
        if (!text.isNullOrBlank()) {
            speechManager.speak(text, _resultLang.value)
        }
    }

    override fun onCleared() {
        super.onCleared()
        hyMt2Engine.release()
        speechManager.release()
    }
}
