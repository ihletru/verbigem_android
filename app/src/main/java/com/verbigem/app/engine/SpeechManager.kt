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
import com.verbigem.app.R
import com.verbigem.app.util.uiString

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
            onError(context.uiString(R.string.voice_not_available))
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
                    // Teksty z zasobów i w języku INTERFEJSU. SpeechManager powstaje
                    // z Application, więc getString() dałby język systemu.
                    val message = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> context.uiString(R.string.voice_error_audio)
                        SpeechRecognizer.ERROR_CLIENT -> context.uiString(R.string.voice_error_client)
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> context.uiString(R.string.voice_error_permissions)
                        SpeechRecognizer.ERROR_NETWORK -> context.uiString(R.string.voice_error_network)
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> context.uiString(R.string.voice_error_network_timeout)
                        SpeechRecognizer.ERROR_NO_MATCH -> context.uiString(R.string.voice_error_no_match)
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> context.uiString(R.string.voice_error_busy)
                        SpeechRecognizer.ERROR_SERVER -> context.uiString(R.string.voice_error_server)
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> context.uiString(R.string.voice_error_speech_timeout)
                        else -> context.uiString(R.string.voice_error_unknown, error)
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
