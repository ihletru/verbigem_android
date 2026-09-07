package com.verbigem.app.engine

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import android.util.Log
import com.verbigem.app.R
import com.verbigem.app.data.model.ModelDownloadState
import com.verbigem.app.data.model.ModelTier
import com.verbigem.app.data.model.ModelTierBlockReason
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

    /**
     * Downloads (and resumes) the GGUF weights for [tier].
     *
     * Resumable: a partial file is kept as `<name>.gguf.tmp` and the next call
     * continues from its current length via an HTTP `Range` header. Without this,
     * a dropped connection at 4 GB into the 7B download would throw away
     * everything — which is the single most common failure mode on mobile data.
     */
    suspend fun downloadModel(
        tier: ModelTier,
        allowMetered: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        val blockReason = blockReason(context, tier)
        if (blockReason != ModelTierBlockReason.NONE) {
            val msg = when (blockReason) {
                ModelTierBlockReason.LOW_RAM -> "Za mało pamięci RAM na ten model"
                ModelTierBlockReason.NO_SPACE -> "Za mało miejsca na dysku"
                ModelTierBlockReason.NO_GPU -> "Ten model wymaga obsługi GPU, której to urządzenie nie ma"
                ModelTierBlockReason.NONE -> ""
            }
            Log.w(TAG, "Refusing download of ${tier.id}: $blockReason")
            _downloadState.value = ModelDownloadState.Error(msg)
            return@withContext false
        }

        // Public-app concern: never pull >1 GB over somebody's mobile data plan.
        // This is deliberately NOT part of blockReason() — blockReason decides
        // whether the *engine* is offered at all, and an engine must not vanish
        // from the picker just because the user is currently on LTE. It only
        // blocks the act of downloading.
        if (tier.approxBytes > CELLULAR_SIZE_LIMIT && isMetered(context) && !allowMetered) {
            Log.w(TAG, "Asking for confirmation: ${tier.id} (${tier.sizeLabel}) on metered network")
            _downloadState.value = ModelDownloadState.MeteredWarning(tier)
            return@withContext false
        }

        val targetFile = HyMt2NativeEngine.getModelFile(context, tier)
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        val downloadUrl = urlFor(tier)

        Log.i(TAG, "Starting download [${tier.id}] from $downloadUrl -> ${targetFile.absolutePath}")

        val resumeFrom = if (tempFile.exists()) tempFile.length() else 0L
        _downloadState.value = ModelDownloadState.Downloading(0, resumeFrom, tier.approxBytes)

        try {
            val requestBuilder = Request.Builder().url(downloadUrl)
            var isResume = false
            if (resumeFrom > 0) {
                // Ask the server to continue. HF/CDN return 206 + Content-Range,
                // or 200 (full body) if they ignore Range — both are handled below.
                requestBuilder.header("Range", "bytes=$resumeFrom-")
                isResume = true
            }
            val response = httpClient.newCall(requestBuilder.build()).execute()

            // 416 = the partial we have is already beyond EOF (server shrank / we
            // finished). Treat the temp file as complete and rename it.
            if (response.code == 416) {
                response.close()
                finish(targetFile, tempFile, tier)
                return@withContext true
            }

            // 200 on a resume attempt = Range unsupported -> restart from zero.
            if (isResume && response.code == 200) {
                Log.w(TAG, "Server ignored Range; restarting download from 0")
                tempFile.delete()
            } else if (!response.isSuccessful) {
                response.close()
                val errorMsg = "Błąd pobierania modelu (HTTP ${response.code})"
                _downloadState.value = ModelDownloadState.Error(errorMsg)
                return@withContext false
            }

            val body = response.body ?: throw IllegalStateException("Empty response body")
            val contentLength = body.contentLength()
            // Total = what we already have + what the server will still send.
            val totalBytes = if (response.code == 206) resumeFrom + contentLength else contentLength
            var downloadedBytes = resumeFrom
            var lastPercent = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else -1

            body.byteStream().use { input ->
                // append=true is what makes this resumable.
                FileOutputStream(tempFile, resumeFrom > 0 && response.code == 206).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    var read: Int

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

            finish(targetFile, tempFile, tier)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading model ${tier.id}", e)
            // Deliberately KEEP the .tmp file so the next attempt resumes instead
            // of re-downloading several gigabytes. It is only ever removed on
            // success (rename) or via [cancelPartial].
            _downloadState.value = ModelDownloadState.Error(e.localizedMessage ?: "Błąd pobierania")
            false
        }
    }

    /** Legacy boolean API — delegates to [downloadModel] with the matching tier. */
    suspend fun downloadModel(isAccurate: Boolean, allowMetered: Boolean = false): Boolean =
        downloadModel(ModelTier.fromAccurate(isAccurate), allowMetered)

    /** Throws away a half-finished download for [tier]. */
    fun cancelPartial(tier: ModelTier) {
        val targetFile = HyMt2NativeEngine.getModelFile(context, tier)
        File(targetFile.parentFile, "${targetFile.name}.tmp").delete()
        _downloadState.value = ModelDownloadState.Idle
    }

    /** Bytes already on disk for [tier] (partial + complete), for UI progress. */
    fun partialBytes(tier: ModelTier): Long {
        val targetFile = HyMt2NativeEngine.getModelFile(context, tier)
        val tmp = File(targetFile.parentFile, "${targetFile.name}.tmp")
        val done = if (targetFile.exists()) targetFile.length() else 0L
        return maxOf(done, if (tmp.exists()) tmp.length() else 0L)
    }

    /**
     * Removes the downloaded weights for [tier]. Safe to call even when nothing
     * is on disk — it just returns false then.
     */
    fun deleteModel(tier: ModelTier): Boolean =
        HyMt2NativeEngine.deleteModel(context, tier)

    private fun finish(targetFile: File, tempFile: File, tier: ModelTier) {
        if (tempFile.exists()) {
            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                Log.e(TAG, "renameTo failed: ${tempFile.absolutePath}")
            }
        }
        _downloadState.value = ModelDownloadState.Ready
        Log.i(TAG, "Model ${tier.id} ready: ${targetFile.length()} bytes at ${targetFile.absolutePath}")
    }

    companion object {
        private const val TAG = "ModelDownloader"

        /**
         * Downloads bigger than this require an unmetered (typically Wi-Fi)
         * network. 1 GB keeps ⚡ Szybki (440 MB) downloadable on LTE exactly as
         * before, while 🎯 Dokładny (1.1 GB) and 🧠 Pro 7B (2.9 GB) are gated.
         */
        private const val CELLULAR_SIZE_LIMIT = 1L * 1024 * 1024 * 1024

        /**
         * True when the active network is metered (cellular, or a Wi-Fi network
         * the user flagged as metered).
         *
         * Conservative by design: if we cannot determine the state we assume
         * metered, because the cost of a false "not metered" is a user silently
         * burning 2.9 GB of their data plan.
         */
        private fun isMetered(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return true
            return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }

        // FAST = 1.25bit (~440 MB) — AngelSlim/Hy-MT2-1.8B-1.25Bit-GGUF
        const val URL_HYMT2_FAST = "https://huggingface.co/AngelSlim/Hy-MT2-1.8B-1.25Bit-GGUF/resolve/main/Hy-MT2-1.8B-1.25Bit.gguf"
        const val URL_HYMT2_ACCURATE = "https://huggingface.co/tencent/Hy-MT2-1.8B-GGUF/resolve/main/Hy-MT2-1.8B-Q4_K_M.gguf"
        /**
         * 7B @ UD-Q2_K_XL (3,123,810,080 B = 2.91 GiB) — Pro-only.
         *
         * NOT tencent/Hy-MT2-7B-GGUF (smallest there is Q4_K_M @ 4.62 GB). unsloth
         * is the only source of a sub-3 GB 7B quant. Must match
         * ModelTier.PRO_7B.fileName.
         */
        const val URL_HYMT2_PRO_7B = "https://huggingface.co/unsloth/Hy-MT2-7B-GGUF/resolve/main/Hy-MT2-7B-UD-Q2_K_XL.gguf"

        fun urlFor(tier: ModelTier): String = when (tier) {
            ModelTier.FAST -> URL_HYMT2_FAST
            ModelTier.ACCURATE -> URL_HYMT2_ACCURATE
            ModelTier.PRO_7B -> URL_HYMT2_PRO_7B
        }

        /**
         * Pre-flight check: is this device even able to hold [tier]?
         *
         * Uses [ActivityManager.MemoryInfo.totalMem] (real physical RAM, not the
         * per-app heap limit) and free space on the same filesystem as
         * `filesDir`, i.e. where the weights actually land.
         */
        fun blockReason(context: Context, tier: ModelTier): ModelTierBlockReason {
            // GPU tiers first: this is the auto-detection, so a phone either
            // runs the big model or never sees it. No per-device hardcoding.
            if (tier.requiresGpu && GpuAcceleration.select(context) == null) {
                return ModelTierBlockReason.NO_GPU
            }

            val memInfo = ActivityManager.MemoryInfo()
            (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
                ?.getMemoryInfo(memInfo)
            if (memInfo.totalMem > 0 && memInfo.totalMem < tier.minRamBytes) {
                return ModelTierBlockReason.LOW_RAM
            }

            val dir = context.filesDir
            val stat = StatFs(dir.absolutePath)
            val available = stat.availableBytes
            // Weights + ~20 % headroom for the KV cache and the runtime.
            val needed = (tier.approxBytes * 1.2).toLong()
            if (available < needed) {
                return ModelTierBlockReason.NO_SPACE
            }
            return ModelTierBlockReason.NONE
        }
    }
}
