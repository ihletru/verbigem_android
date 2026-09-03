package com.verbigem.app.engine

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.verbigem.app.data.model.LangCode
import com.verbigem.app.jni.LlamaNativeBridge
import com.verbigem.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class HyMt2NativeEngine(private val context: Context) {

    private var nativeHandle: Long = 0
    private var loadedModelPath: String? = null

    companion object {
        private const val TAG = "HyMt2NativeEngine"
        const val MODEL_FILENAME_FAST = "Hy-MT2-1.8B-1.25Bit.gguf"
        const val MODEL_FILENAME_ACCURATE = "Hy-MT2-1.8B-Q4_K_M.gguf"

        fun getModelFile(context: Context, isAccurate: Boolean): File {
            val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
            val filename = if (isAccurate) MODEL_FILENAME_ACCURATE else MODEL_FILENAME_FAST
            return File(modelsDir, filename)
        }

        fun isModelDownloaded(context: Context, isAccurate: Boolean): Boolean {
            val file = getModelFile(context, isAccurate)
            return file.exists() && file.length() > 1024 * 1024 * 50 // Minimum 50 MB
        }
    }

    suspend fun ensureModelLoaded(isAccurate: Boolean): Boolean = withContext(Dispatchers.IO) {
        val modelFile = getModelFile(context, isAccurate)
        Log.i(TAG, "ensureModelLoaded: path=${modelFile.absolutePath}, exists=${modelFile.exists()}, size=${modelFile.length()}")
        if (!modelFile.exists()) {
            Log.w(TAG, "Model file missing at: ${modelFile.absolutePath}")
            return@withContext false
        }

        if (nativeHandle != 0L && loadedModelPath == modelFile.absolutePath) {
            return@withContext true
        }

        if (nativeHandle != 0L) {
            LlamaNativeBridge.freeModelNative(nativeHandle)
            nativeHandle = 0L
        }

        val cores = Runtime.getRuntime().availableProcessors()
        val threads = (cores - 1).coerceAtLeast(2).coerceAtMost(6)

        // Detect Vulkan support and offload layers to GPU if available.
        // STQ1_0 Vulkan path is supported in llama.cpp master (post-PR #22836 merge).
        // NOTE: Vulkan build requires MSVC/LLVM + Vulkan SDK on the build host.
        // Currently forcing CPU-only (gpu_layers=0) until Vulkan SDK is installed.
        // To enable GPU: install VulkanSDK + BuildTools, then set GGML_VULKAN=ON in CMakeLists.txt.
        val gpuLayers = 0  // TODO: replace with hasVulkan() after Vulkan build is ready

        nativeHandle = LlamaNativeBridge.loadModelNative(
            modelPath = modelFile.absolutePath,
            nThreads = threads,
            nGpuLayers = gpuLayers
        )

        loadedModelPath = modelFile.absolutePath
        Log.i(TAG, "Loaded Hy-MT2 native model, handle: $nativeHandle")
        nativeHandle != 0L
    }

    suspend fun translate(text: String, from: LangCode, to: LangCode, isAccurate: Boolean = false, onPartial: (String) -> Unit = {}): String = withContext(Dispatchers.Default) {
        if (text.isBlank()) return@withContext ""

        val isReady = ensureModelLoaded(isAccurate)
        if (!isReady || nativeHandle == 0L) {
            throw IllegalStateException(context.getString(R.string.model_not_loaded))
        }

        val prompt = buildPrompt(text.trim(), from, to)
        val maxTokens = (text.length * 3 + 64).coerceAtMost(512)

        val accumulated = StringBuilder()
        LlamaNativeBridge.generateNativeStreaming(
            handle = nativeHandle,
            prompt = prompt,
            maxTokens = maxTokens,
            callback = object : LlamaNativeBridge.TokenStreamCallback {
                override fun onToken(piece: String) {
                    // JNI streams one token's text at a time; append to rebuild the full output.
                    accumulated.append(piece)
                    onPartial(accumulated.toString())
                }
            }
        )
        sanitizeTranslation(accumulated.toString())
    }

    fun buildPrompt(text: String, from: LangCode, to: LangCode): String {
        // Format zgodny z oficjalnym repo Tencent Hy-MT2 (llama-completion -p):
        // "Translate the following segment into <TARGET>, without additional explanation：<TEXT>"
        // Używamy pełnych nazw języków (English names), bo model tak wymaga.
        return "Translate the following segment into ${to.englishName}, without additional explanation：$text"
    }

    fun sanitizeTranslation(raw: String): String {
        var s = raw.replace(Regex("(?i)<think>[\\s\\S]*?</think>"), "")
        // Strip a single pair of surrounding quotes / whitespace.
        s = s.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        // Drop a leading "Translation:" label the model sometimes prepends.
        s = s.replace(Regex("(?i)^\\s*(translation|here(?:'| i)?s? the translation|tłumaczenie|oto tłumaczenie|wynik)\\s*[:\\-]\\s*"), "")
        // NOTE: keep the whole text — do NOT truncate to the first paragraph/line.
        // Hy-MT2 is a per-segment translator; multi-segment input is handled by
        // translateSegmented(), which translates each segment separately and joins them.
        return s.trim()
    }

    /**
     * Translates a (possibly long, multi-sentence) text by splitting it into
     * segments at sentence boundaries and translating each segment separately.
     *
     * Hy-MT2 is a *segment* model: given a whole paragraph it emits the translation
     * of only the first sentence, then stops. Feeding it one segment at a time makes
     * it translate the entire input. [onPartial] receives the running combined
     * translation so the UI still streams word-by-word (at segment granularity).
     */
    suspend fun translateSegmented(
        text: String,
        from: LangCode,
        to: LangCode,
        isAccurate: Boolean = false,
        onPartial: (String) -> Unit = {}
    ): String = withContext(Dispatchers.Default) {
        if (text.isBlank()) return@withContext ""

        val segments = splitIntoSegments(text.trim())
        // Single segment: behave exactly like translate() (keeps the streaming contract).
        if (segments.size <= 1) {
            return@withContext translate(text, from, to, isAccurate, onPartial)
        }

        val sb = StringBuilder()
        segments.forEachIndexed { i, seg ->
            // Translate each segment WITHOUT its own partial (avoid mid-flight flashes);
            // we emit the cumulative combined result after every segment completes.
            val part = translate(seg, from, to, isAccurate)
            sb.append(part)
            if (i < segments.lastIndex) sb.append("\n\n")
            onPartial(sb.toString())
        }
        sb.toString()
    }

    /**
     * Splits [text] into translation-sized segments (~400 chars) at sentence boundaries
     * (., !, ? or newline). Over-long single sentences are broken further by words so no
     * single model call is asked to render an enormous chunk.
     */
    private fun splitIntoSegments(text: String): List<String> {
        val out = mutableListOf<String>()
        val max = 400
        val rough = text.split(Regex("(?<=[.!?\\n])\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val sb = StringBuilder()
        fun flush() {
            if (sb.isNotEmpty()) {
                out.add(sb.toString().trim())
                sb.setLength(0)
            }
        }

        for (piece in rough) {
            if (piece.length > max) {
                flush()
                val words = piece.split(Regex("\\s+"))
                val wsb = StringBuilder()
                for (w in words) {
                    if (wsb.isEmpty()) {
                        wsb.append(w)
                    } else if (wsb.length + 1 + w.length <= max) {
                        wsb.append(' ').append(w)
                    } else {
                        out.add(wsb.toString())
                        wsb.setLength(0)
                        wsb.append(w)
                    }
                }
                if (wsb.isNotEmpty()) out.add(wsb.toString())
                continue
            }
            if (sb.isEmpty()) {
                sb.append(piece)
            } else if (sb.length + 1 + piece.length <= max) {
                sb.append(' ').append(piece)
            } else {
                flush()
                sb.append(piece)
            }
        }
        flush()
        return if (out.isEmpty()) listOf(text.trim()) else out
    }

    /**
     * Returns true if the device supports Vulkan (android.hardware.vulkan.level).
     * Used to decide whether to offload model layers to GPU.
     */
    private fun hasVulkan(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
    }

    fun release() {
        if (nativeHandle != 0L) {
            LlamaNativeBridge.freeModelNative(nativeHandle)
            nativeHandle = 0L
            loadedModelPath = null
        }
    }
}
