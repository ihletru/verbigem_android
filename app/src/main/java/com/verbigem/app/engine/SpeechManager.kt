package com.verbigem.app.engine

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import com.verbigem.app.data.model.LangCode
import java.util.Locale

class SpeechManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false

    // Callback reporting TTS playback state so the UI can show a "speaking" animation.
    var onSpeakingStateChanged: ((Boolean) -> Unit)? = null

    companion object {
        private const val TAG = "SpeechManager"
    }

    init {
        textToSpeech = TextToSpeech(context.applicationContext, this).apply {
            setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    onSpeakingStateChanged?.invoke(true)
                }
                override fun onDone(utteranceId: String?) {
                    onSpeakingStateChanged?.invoke(false)
                }
                override fun onError(utteranceId: String?) {
                    onSpeakingStateChanged?.invoke(false)
                }
            })
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true
            Log.i(TAG, "TextToSpeech initialized successfully")
        } else {
            Log.e(TAG, "Failed to initialize TextToSpeech")
        }
    }

    fun isSttAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(
        lang: LangCode,
        onInterim: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        stopListening()

        if (!isSttAvailable()) {
            onError("Rozpoznawanie mowy nie jest dostępne na tym urządzeniu")
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    val message = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Błąd nagrywania dźwięku"
                        SpeechRecognizer.ERROR_CLIENT -> "Błąd aplikacji podczas rozpoznawania"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Brak uprawnień do mikrofonu"
                        SpeechRecognizer.ERROR_NETWORK -> "Błąd sieci"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Przekroczono limit czasu sieci"
                        SpeechRecognizer.ERROR_NO_MATCH -> "Nie rozpoznano mowy — spróbuj ponownie"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Rozpoznawanie mowy jest zajęte"
                        SpeechRecognizer.ERROR_SERVER -> "Błąd serwera rozpoznawania"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Brak dźwięku mowy"
                        else -> "Błąd mikrofonu ($error)"
                    }
                    onError(message)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    if (text.isNotBlank()) {
                        onFinal(text.trim())
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    if (text.isNotBlank()) {
                        onInterim(text.trim())
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang.bcp47)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang.bcp47)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    fun speak(text: String, lang: LangCode) {
        if (!isTtsInitialized || text.isBlank()) {
            onSpeakingStateChanged?.invoke(false)
            return
        }

        val locale = when (lang) {
            LangCode.PL -> Locale("pl", "PL")
            LangCode.EN -> Locale.ENGLISH
            LangCode.ES -> Locale("es", "ES")
            LangCode.ZH -> Locale.CHINESE
            LangCode.DE -> Locale.GERMAN
            LangCode.TR -> Locale("tr", "TR")
        }

        textToSpeech?.language = locale
        textToSpeech?.stop()
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "VerbigemUtterance_${System.currentTimeMillis()}")
    }

    fun release() {
        stopListening()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }
}
