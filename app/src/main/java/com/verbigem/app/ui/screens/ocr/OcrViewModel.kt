package com.verbigem.app.ui.screens.ocr

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.verbigem.app.R
import com.verbigem.app.data.local.AppDatabase
import com.verbigem.app.data.model.LangCode
import com.verbigem.app.data.model.TranslationHistory
import com.verbigem.app.data.repository.HistoryRepository
import com.verbigem.app.data.repository.SyncManager
import com.verbigem.app.engine.HyMt2NativeEngine
import com.verbigem.app.engine.OcrManager
import com.verbigem.app.engine.SpeechManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OcrViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application
    private val ocrManager = OcrManager(application)
    private val hyMt2Engine = HyMt2NativeEngine(application)
    private val speechManager = SpeechManager(application)
    private val historyRepository = HistoryRepository(
        AppDatabase.getInstance(appContext).historyDao(),
        AppDatabase.getInstance(appContext).pendingDeleteDao()
    )

    private val _historyList = MutableStateFlow<List<TranslationHistory>>(emptyList())
    val historyList: StateFlow<List<TranslationHistory>> = _historyList.asStateFlow()

    init {
        speechManager.onSpeakingStateChanged = { speaking ->
            _isSpeaking.value = speaking
        }
        viewModelScope.launch {
            historyRepository.allHistory.collect { _historyList.value = it }
        }
    }

    // Holds the FULL original bitmap (used for cropping + OCR, never the preview-scaled one)
    private var _originalBitmap: Bitmap? = null

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

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    // Crop rectangle in image-relative coordinates (0f..1f)
    private val _cropRect = MutableStateFlow<RectF?>(null)
    val cropRectFlow: StateFlow<RectF?> = _cropRect.asStateFlow()

    fun processImageUri(uri: Uri, crop: RectF? = null) {
        _selectedImageUri.value = uri
        _selectedBitmap.value = null
        _originalBitmap = null
        _cropRect.value = null
        _recognizedText.value = ""
        _translatedText.value = null
        _errorMessage.value = null
        // Load the bitmap so the crop frame + "Read selected area" OCR work on gallery images too.
        // OCR is NOT run automatically — the user triggers it with the button.
        val bmp = loadBitmap(uri)
        if (bmp != null) {
            _originalBitmap = bmp
            _selectedBitmap.value = bmp
            _cropRect.value = defaultCropRect()
        } else {
            _errorMessage.value = appContext.getString(R.string.ocr_error_no_text)
        }
    }

    fun processBitmap(bitmap: Bitmap, crop: RectF? = null) {
        _originalBitmap = bitmap
        _selectedBitmap.value = bitmap
        _selectedImageUri.value = null
        _cropRect.value = defaultCropRect()
        _recognizedText.value = ""
        _translatedText.value = null
        _errorMessage.value = null
        // OCR is NOT run automatically — the user triggers it with the button.
    }

    private fun defaultCropRect(): RectF = RectF(0.1f, 0.1f, 0.9f, 0.9f)

    private fun runOcr(ocrBlock: suspend () -> String) {
        _isProcessing.value = true
        _errorMessage.value = null
        _recognizedText.value = ""
        _translatedText.value = null

        viewModelScope.launch {
            try {
                val extracted = ocrBlock()
                if (extracted.isBlank()) {
                    _errorMessage.value = appContext.getString(R.string.ocr_error_no_text)
                    return@launch
                }
                _recognizedText.value = extracted
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "OCR recognition error"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    // Editable OCR text — user can fix mistakes before translating
    fun updateRecognizedText(text: String) {
        _recognizedText.value = text
    }

    // Translate invoked by button (not automatically after OCR)
    fun translateText() {
        val text = _recognizedText.value.trim()
        if (text.isBlank()) {
            _errorMessage.value = appContext.getString(R.string.ocr_no_text)
            return
        }
        _isProcessing.value = true
        _errorMessage.value = null
        _translatedText.value = null

        viewModelScope.launch {
            try {
                val translation = hyMt2Engine.translate(text, LangCode.EN, LangCode.PL)
                _translatedText.value = translation
                addHistory(text, translation)
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Translation error"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private fun addHistory(sourceText: String, translatedText: String) {
        viewModelScope.launch {
            historyRepository.addHistory(sourceText, translatedText, "EN", "PL")
            try {
                SyncManager(appContext).syncNow()
            } catch (_: Exception) {
                // Offline: pushed on next connectivity-driven sync.
            }
        }
    }

    fun speak(text: String) {
        _isSpeaking.value = true
        speechManager.speak(text, LangCode.PL)
    }

    fun clear() {
        _selectedImageUri.value = null
        _selectedBitmap.value = null
        _originalBitmap = null
        _recognizedText.value = ""
        _translatedText.value = null
        _errorMessage.value = null
        _cropRect.value = null
        _isSpeaking.value = false
    }

    fun setError(message: String) {
        _errorMessage.value = message
    }

    // Crop state — image-relative coordinates (0f..1f)
    fun setCropStart(offset: Offset, size: androidx.compose.ui.unit.IntSize) {
        val nx = (offset.x / size.width).coerceIn(0f, 1f)
        val ny = (offset.y / size.height).coerceIn(0f, 1f)
        val half = 0.15f
        _cropRect.value = RectF(
            (nx - half).coerceIn(0f, 1f),
            (ny - half).coerceIn(0f, 1f),
            (nx + half).coerceIn(0f, 1f),
            (ny + half).coerceIn(0f, 1f)
        )
    }

    fun updateCropRect(rect: RectF) {
        _cropRect.value = rect
    }

    fun clearCrop() {
        _cropRect.value = null
    }

    fun deleteHistory(item: TranslationHistory) {
        viewModelScope.launch {
            historyRepository.deleteHistory(item)
            try {
                SyncManager(appContext).syncNow()
            } catch (_: Exception) {
                // Offline: tombstone stays queued in pending_deletes.
            }
        }
    }

    /** Run OCR for the FIRST time, on the already-cropped bitmap returned by the crop library. */
    fun runOcrWithBitmap(bitmap: Bitmap) {
        runOcr { ocrManager.recognizeText(bitmap) }
    }

    /** Run OCR for the FIRST time, on the cropped region of the selected image. */
    fun runOcrFromCrop() {
        val src = _originalBitmap ?: _selectedBitmap.value ?: return
        val crop = _cropRect.value ?: return
        runOcr { ocrManager.recognizeText(cropBitmap(src, crop)) }
    }

    private fun loadBitmap(uri: Uri): Bitmap? {
        return try {
            val stream = appContext.contentResolver.openInputStream(uri) ?: return null
            BitmapFactory.decodeStream(stream)
        } catch (e: Exception) {
            null
        }
    }

    private fun cropBitmap(src: Bitmap, crop: RectF): Bitmap {
        val x = (crop.left * src.width).toInt().coerceAtLeast(0)
        val y = (crop.top * src.height).toInt().coerceAtLeast(0)
        val w = ((crop.right - crop.left) * src.width).toInt().coerceAtLeast(1)
        val h = ((crop.bottom - crop.top) * src.height).toInt().coerceAtLeast(1)
        val safeW = minOf(w, src.width - x)
        val safeH = minOf(h, src.height - y)
        return Bitmap.createBitmap(src, x, y, safeW, safeH)
    }

    override fun onCleared() {
        super.onCleared()
        hyMt2Engine.release()
        speechManager.release()
    }
}
