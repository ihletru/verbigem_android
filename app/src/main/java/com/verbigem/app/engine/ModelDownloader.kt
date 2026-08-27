package com.verbigem.app.engine

import android.content.Context
import android.util.Log
import com.verbigem.app.data.model.ModelDownloadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class ModelDownloader(private val context: Context) {

    private val _downloadState = MutableStateFlow<ModelDownloadState>(ModelDownloadState.Idle)
    val downloadState: StateFlow<ModelDownloadState> = _downloadState.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    companion object {
        private const val TAG = "ModelDownloader"
        // HuggingFace direct URLs for Hy-MT2-1.8B GGUF.
        // NOTE: 1.25Bit (Tencent) is NOT supported by stock llama.cpp (custom quant scheme),
        // so we use standard GGUF quants that llama.cpp can load:
        // FAST = Q4_0 (~1 GB, smallest standard quant, from unsloth mirror) — default.
        // ACCURATE = Q4_K_M (~1.1 GB, official Tencent repo).
        const val URL_HYMT2_FAST = "https://huggingface.co/unsloth/Hy-MT2-1.8B-GGUF/resolve/main/Hy-MT2-1.8B-Q4_0.gguf"
        const val URL_HYMT2_ACCURATE = "https://huggingface.co/tencent/Hy-MT2-1.8B-GGUF/resolve/main/Hy-MT2-1.8B-Q4_K_M.gguf"
    }

    suspend fun downloadModel(isAccurate: Boolean): Boolean = withContext(Dispatchers.IO) {
        val targetFile = HyMt2NativeEngine.getModelFile(context, isAccurate)
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        val downloadUrl = if (isAccurate) URL_HYMT2_ACCURATE else URL_HYMT2_FAST

        Log.i(TAG, "Starting download for Hy-MT2 from: $downloadUrl to: ${targetFile.absolutePath}")
        _downloadState.value = ModelDownloadState.Downloading(0, 0, 0)

        try {
            val request = Request.Builder().url(downloadUrl).build()
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorMsg = "Błąd pobierania modelu (HTTP ${response.code})"
                _downloadState.value = ModelDownloadState.Error(errorMsg)
                return@withContext false
            }

            val body = response.body ?: throw IllegalStateException("Empty response body")
            val totalBytes = body.contentLength()
            var downloadedBytes: Long = 0

            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    var lastPercent = 0

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read

                        if (totalBytes > 0) {
                            val percent = ((downloadedBytes * 100) / totalBytes).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                _downloadState.value = ModelDownloadState.Downloading(
                                    progressPercent = percent,
                                    bytesDownloaded = downloadedBytes,
                                    totalBytes = totalBytes
                                )
                            }
                        }
                    }
                    output.flush()
                }
            }

            if (tempFile.exists()) {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
            }

            _downloadState.value = ModelDownloadState.Ready
            Log.i(TAG, "Hy-MT2 model downloaded successfully! Size: ${targetFile.length()} bytes")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading model", e)
            if (tempFile.exists()) tempFile.delete()
            _downloadState.value = ModelDownloadState.Error(e.localizedMessage ?: "Błąd pobierania")
            false
        }
    }
}
