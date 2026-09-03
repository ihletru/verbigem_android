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
import com.verbigem.app.data.local.PendingDeleteEntity
import com.verbigem.app.data.local.PreferencesManager
import com.verbigem.app.data.model.LangCode
import com.verbigem.app.data.model.TtsConfig
import com.verbigem.app.data.model.TranslationHistory
import com.verbigem.app.data.repository.OcrHistoryRepository
import com.verbigem.app.data.repository.ProTtsRepository
import com.verbigem.app.data.repository.SyncManager
import com.verbigem.app.engine.HyMt2NativeEngine
import com.verbigem.app.engine.OcrManager
import com.verbigem.app.engine.ProTtsEngine
import com.verbigem.app.engine.SpeechManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OcrViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application
    private val ocrManager = OcrManager(application)
    private val hyMt2Engine = HyMt2NativeEngine(application)
    private val speechManager = SpeechManager(application)
    private val proTtsEngine = ProTtsEngine(application)
    private val ocrHistoryRepository = OcrHistoryRepository(
        AppDatabase.getInstance(appContext).ocrHistoryDao(),
        AppDatabase.getInstance(appContext).pendingDeleteDao()
    )
    private val proTtsRepository = ProTtsRepository(application)
    private val preferencesManager = PreferencesManager(application)

    // Infinite-scroll history (newest-first, offset-paged from Room so we never
    // load the whole table). loadMoreHistory() appends the next page.
    private val _historyItems = MutableStateFlow<List<TranslationHistory>>(emptyList())
    val historyItems: StateFlow<List<TranslationHistory>> = _historyItems.asStateFlow()
    private var historyLoadedCount = 0
    private var historyExhausted = false

    fun loadMoreHistory() {
        if (historyExhausted) return
        viewModelScope.launch {
            val page = ocrHistoryRepository.getPage(historyLoadedCount, HISTORY_PAGE_SIZE)
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

    // Source/target languages inherited from the Translator's selection (DataStore),
    // so OCR translates the same direction the user last chose instead of a fixed EN→PL.
    private val _sourceLang = MutableStateFlow(LangCode.PL)
    val sourceLang: StateFlow<LangCode> = _sourceLang.asStateFlow()
    private val _targetLang = MutableStateFlow(LangCode.EN)
    val targetLang: StateFlow<LangCode> = _targetLang.asStateFlow()

    private var ttsConfig: TtsConfig = TtsConfig()

    init {
        speechManager.onSpeakingStateChanged = { speaking ->
            _resultSpeaking.value = speaking
            if (!speaking) _speakingSyncId.value = null
        }
        proTtsEngine.onSpeakingStateChanged = { speaking ->
            _resultSpeakingPro.value = speaking
            if (!speaking) _speakingProSyncId.value = null
        }
        viewModelScope.launch {
            ttsConfig = proTtsRepository.getConfig()
            resetHistory()
        }
        // Inherit the Translator's last-used language pair.
        viewModelScope.launch {
            preferencesManager.srcLangFlow.collect { _sourceLang.value = LangCode.fromCode(it) }
        }
        viewModelScope.launch {
            preferencesManager.dstLangFlow.collect { _targetLang.value = LangCode.fromCode(it) }
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

    // Per-item speaking state: tracks WHICH history row is being read, so only that card
    // shows the animation (not every card when any row is read).
    private val _speakingSyncId = MutableStateFlow<String?>(null)
    val speakingSyncId: StateFlow<String?> = _speakingSyncId.asStateFlow()

    private val _speakingProSyncId = MutableStateFlow<String?>(null)
    val speakingProSyncId: StateFlow<String?> = _speakingProSyncId.asStateFlow()

    // Result (single, non-history) speaking flags for the OCR translation card.
    private val _resultSpeaking = MutableStateFlow(false)
    val resultSpeaking: StateFlow<Boolean> = _resultSpeaking.asStateFlow()
    private val _resultSpeakingPro = MutableStateFlow(false)
    val resultSpeakingPro: StateFlow<Boolean> = _resultSpeakingPro.asStateFlow()

    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    // Crop rectangle in image-relative coordinates (0f..1f)
    private val _cropRect = MutableStateFlow<RectF?>(null)
    val cropRectFlow: StateFlow<RectF?> = _cropRect.asStateFlow()

    /** Called by the navigation layer once the signed-in user's profile is known. */
    fun setPro(isPro: Boolean) {
        _isPro.value = isPro
    }

    companion object {
        private const val HISTORY_PAGE_SIZE = 20
    }

    /** Reload the cached TTS config after a Firestore sync completed. */
    fun refreshTtsConfig() {
        viewModelScope.launch {
            ttsConfig = proTtsRepository.getConfig()
        }
    }

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

    // Translate invoked by button (not automatically after OCR). Streams partial
    // results word-by-word (like the Translator) instead of waiting for the full text.
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
                val result = hyMt2Engine.translateSegmented(text, _sourceLang.value, _targetLang.value, isAccurate = false) { partial ->
                    // Streaming: show each completed segment as it arrives.
                    _translatedText.value = partial
                }
                _translatedText.value = result
                addHistory(text, result)
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Translation error"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private fun addHistory(sourceText: String, translatedText: String) {
        viewModelScope.launch {
            // Persist locally (with a fresh syncId for Firestore) then push to the cloud
            // via the reactive sync so the OCR translation propagates without waiting for restart.
            ocrHistoryRepository.addHistory(
                sourceText,
                translatedText,
                _sourceLang.value.code,
                _targetLang.value.code
            )
            try {
                SyncManager(appContext).syncNow()
            } catch (_: Exception) {
                // Offline: pushed on next connectivity-driven sync.
            }
        }
    }

    // Free local TTS (offline). Reads the given [text] in [lang] (caller passes the right lang).
    fun speak(text: String, lang: LangCode) {
        _resultSpeaking.value = true
        speechManager.onSpeakingStateChanged = { speaking ->
            _resultSpeaking.value = speaking
            if (!speaking) _speakingSyncId.value = null
        }
        speechManager.speak(text, lang)
    }

    /** Paid "Read Pro" TTS via OpenRouter for the OCR result card. */
    fun speakPro(text: String) {
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
                proTtsEngine.speak(text, _targetLang.value, ttsConfig)
            } catch (e: Exception) {
                _resultSpeakingPro.value = false
                _errorMessage.value = e.localizedMessage ?: "Read Pro failed"
            }
        }
    }

    /** Read an OCR history row in ITS OWN target language (not the current UI target). */
    fun speakHistory(item: TranslationHistory) {
        _speakingSyncId.value = item.syncId
        speechManager.onSpeakingStateChanged = { speaking ->
            if (!speaking) _speakingSyncId.value = null
        }
        speechManager.speak(item.translatedText, LangCode.fromCode(item.targetLang))
    }

    /** Paid "Read Pro" for an OCR history row in ITS OWN target language. */
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

    fun clear() {
        _selectedImageUri.value = null
        _selectedBitmap.value = null
        _originalBitmap = null
        _recognizedText.value = ""
        _translatedText.value = null
        _errorMessage.value = null
        _cropRect.value = null
        _resultSpeaking.value = false
        _resultSpeakingPro.value = false
        _speakingSyncId.value = null
        _speakingProSyncId.value = null
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
            // Physical local delete + queue a Firestore tombstone (tagged "ocr_history") so the
            // deletion propagates to other devices, just like the Translator's history.
            val syncId = ocrHistoryRepository.deleteHistory(item)
            if (syncId.isNotBlank()) {
                AppDatabase.getInstance(appContext)
                    .pendingDeleteDao()
                    .insert(PendingDeleteEntity(syncId = syncId, collection = "ocr_history", updatedAt = System.currentTimeMillis()))
            }
            try {
                SyncManager(appContext).syncNow()
            } catch (_: Exception) {
                // Offline or transient: tombstone stays queued and goes out on the next sync.
            }
            resetHistory()
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
            val exif = try {
                val fs = appContext.contentResolver.openInputStream(uri) ?: return null
                android.media.ExifInterface(fs).getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_NORMAL
                )
            } catch (_: Exception) {
                android.media.ExifInterface.ORIENTATION_NORMAL
            }
            val full = appContext.contentResolver.openInputStream(uri) ?: return null
            val bmp = BitmapFactory.decodeStream(full) ?: return null
            when (exif) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bmp, 90f)
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bmp, 180f)
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bmp, 270f)
                else -> bmp
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun rotateBitmap(src: Bitmap, degrees: Float): Bitmap {
        val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
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
        proTtsEngine.release()
    }
}
