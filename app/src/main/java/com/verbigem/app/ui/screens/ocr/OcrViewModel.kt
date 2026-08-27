package com.verbigem.app.ui.screens.ocr

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.verbigem.app.data.model.LangCode
import com.verbigem.app.engine.HyMt2NativeEngine
import com.verbigem.app.engine.OcrManager
import com.verbigem.app.engine.SpeechManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OcrViewModel(application: Application) : AndroidViewModel(application) {

    private val ocrManager = OcrManager(application)
    private val hyMt2Engine = HyMt2NativeEngine(application)
    private val speechManager = SpeechManager(application)

    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri.asStateFlow()

    private val _selectedBitmap = MutableStateFlow<Bitmap?>(null)
    val selectedBitmap: StateFlow<Bitmap?> = _selectedBitmap.asStateFlow()

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

    private val _translatedText = MutableStateFlow<String?>(null)
    val translatedText: StateFlow<String?> = _translatedText.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun processImageUri(uri: Uri) {
        _selectedImageUri.value = uri
        _selectedBitmap.value = null
        runOcrAndTranslate { ocrManager.recognizeText(uri) }
    }

    fun processBitmap(bitmap: Bitmap) {
        _selectedBitmap.value = bitmap
        _selectedImageUri.value = null
        runOcrAndTranslate { ocrManager.recognizeText(bitmap) }
    }

    private fun runOcrAndTranslate(ocrBlock: suspend () -> String) {
        _isProcessing.value = true
        _errorMessage.value = null
        _recognizedText.value = ""
        _translatedText.value = null

        viewModelScope.launch {
            try {
                val extracted = ocrBlock()
                if (extracted.isBlank()) {
                    _errorMessage.value = "Nie wykryto tekstu na zdjęciu"
                    return@launch
                }
                _recognizedText.value = extracted

                // Tłumaczenie na język polski / angielski natywnym modelem Hy-MT2
                val translation = hyMt2Engine.translate(extracted, LangCode.EN, LangCode.PL)
                _translatedText.value = translation
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Błąd podczas rozpoznawania OCR"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun speak(text: String) {
        speechManager.speak(text, LangCode.PL)
    }

    fun clear() {
        _selectedImageUri.value = null
        _selectedBitmap.value = null
        _recognizedText.value = ""
        _translatedText.value = null
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        hyMt2Engine.release()
        speechManager.release()
    }
}
