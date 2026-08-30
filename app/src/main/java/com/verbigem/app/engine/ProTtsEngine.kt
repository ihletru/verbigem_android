package com.verbigem.app.engine

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.verbigem.app.data.model.LangCode
import com.verbigem.app.data.model.TtsConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Paid "Read Pro" TTS via OpenRouter's /audio/speech endpoint.
 *  - Default model: google/gemini-3.1-flash-tts-preview (70+ langs, best price/quality).
 *  - Chinese gets its own model (fish-audio/s2.1-pro) per product spec.
 * The config (apiKey + model ids) is stored locally and synced from Firestore,
 * so it can be managed by the future admin webapp without a client release.
 */
class ProTtsEngine(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private var mediaPlayer: MediaPlayer? = null

    var onSpeakingStateChanged: ((Boolean) -> Unit)? = null

    private val openRouterUrl = "https://openrouter.ai/api/v1/audio/speech"

    /**
     * Fetches speech audio for [text] in [lang] using the supplied [config] and plays it
     * through the device speaker. Resolves when playback finishes or fails.
     */
    suspend fun speak(text: String, lang: LangCode, config: TtsConfig): Unit = withContext(Dispatchers.IO) {
        if (text.isBlank() || !config.isConfigured) {
            onSpeakingStateChanged?.invoke(false)
            return@withContext
        }

        val modelId = config.modelIdFor(lang)
        val voice = config.voiceFor(lang)

        val payload = """
            {
              "model": "$modelId",
              "input": ${quoteJson(text)},
              "voice": "$voice",
              "response_format": "mp3"
            }
        """.trimIndent()

        val request = Request.Builder()
            .url(openRouterUrl)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(payload.toRequestBody(jsonMediaType))
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            val err = response.body?.string() ?: ""
            Log.e(TAG, "OpenRouter TTS error ${response.code}: $err")
            onSpeakingStateChanged?.invoke(false)
            throw IllegalStateException("TTS Pro error: HTTP ${response.code}")
        }

        val bytes = response.body?.bytes() ?: byteArrayOf()
        response.close()
        if (bytes.isEmpty()) {
            onSpeakingStateChanged?.invoke(false)
            return@withContext
        }

        val tmpFile = File(context.cacheDir, "pro_tts_${System.currentTimeMillis()}.mp3")
        tmpFile.writeBytes(bytes)

        playFile(tmpFile)
    }

    private suspend fun playFile(file: File) = suspendCancellableCoroutine { cont ->
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnPreparedListener {
                    onSpeakingStateChanged?.invoke(true)
                    start()
                }
                setOnCompletionListener {
                    onSpeakingStateChanged?.invoke(false)
                    release()
                    mediaPlayer = null
                    if (file.exists()) file.delete()
                    if (cont.isActive) cont.resume(Unit)
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
                    onSpeakingStateChanged?.invoke(false)
                    release()
                    mediaPlayer = null
                    if (file.exists()) file.delete()
                    if (cont.isActive) cont.resume(Unit)
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "playFile failed", e)
            onSpeakingStateChanged?.invoke(false)
            if (file.exists()) file.delete()
            if (cont.isActive) cont.resume(Unit)
        }
    }

    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    companion object {
        private const val TAG = "ProTtsEngine"

        // Minimal JSON string escaper (we only ship plain user text; escape the essentials).
        private fun quoteJson(s: String): String {
            val escaped = s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
            return "\"$escaped\""
        }
    }
}
