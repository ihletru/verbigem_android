package com.verbigem.app.engine

import android.content.Context
import android.util.Log
import com.verbigem.app.data.model.GlossaryEntry
import com.verbigem.app.data.model.LangCode
import com.verbigem.app.data.model.ModelTier
import com.verbigem.app.data.repository.GlossaryRepository
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
        // Must match ModelTier.PRO_7B.fileName.
        const val MODEL_FILENAME_PRO_7B = "Hy-MT2-7B-UD-Q2_K_XL.gguf"

        /** Minimum plausible size for a "complete" GGUF. Anything smaller is a stub. */
        private const val MIN_MODEL_BYTES = 50L * 1024 * 1024

        @JvmStatic
        fun getModelFile(context: Context, tier: ModelTier): File {
            val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
            return File(modelsDir, tier.fileName)
        }

        fun getModelFile(context: Context, isAccurate: Boolean): File =
            getModelFile(context, ModelTier.fromAccurate(isAccurate))

        fun isModelDownloaded(context: Context, tier: ModelTier): Boolean {
            val file = getModelFile(context, tier)
            return file.exists() && file.length() > MIN_MODEL_BYTES
        }

        fun isModelDownloaded(context: Context, isAccurate: Boolean): Boolean =
            isModelDownloaded(context, ModelTier.fromAccurate(isAccurate))
    }

    suspend fun ensureModelLoaded(tier: ModelTier): Boolean = withContext(Dispatchers.IO) {
        val modelFile = getModelFile(context, tier)
        Log.i(TAG, "ensureModelLoaded[${tier.id}]: path=${modelFile.absolutePath}, exists=${modelFile.exists()}, size=${modelFile.length()}")
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

        // Measured, not guessed: prompt processing gains ~30% from 4->8 threads
        // while decode stays flat, and translations here are prompt-heavy, so
        // more threads win overall. See CpuTopology for the full table.
        val threads = CpuTopology.inferenceThreads()

        // GPU offload zależy od DWÓCH rzeczy naraz: co jest wkompilowane w .so
        // i co to konkretne urządzenie potrafi uruchomić. Na telefonie bez GPU
        // (albo z biblioteką, której system nie udostępnia aplikacjom) wynikiem
        // jest 0 i wszystko leci na CPU — bez wyjątku, bez crasha.
        // Patrz GpuAcceleration oraz docs/SILNIK_PRO_RESEARCH.md §5.
        val gpuLayers = GpuAcceleration.gpuLayers(context)

        nativeHandle = LlamaNativeBridge.loadModelNative(
            modelPath = modelFile.absolutePath,
            nThreads = threads,
            nGpuLayers = gpuLayers
        )

        loadedModelPath = modelFile.absolutePath
        Log.i(TAG, "Loaded Hy-MT2 native model [${tier.id}], handle: $nativeHandle, gpuLayers=$gpuLayers")
        nativeHandle != 0L
    }

    suspend fun ensureModelLoaded(isAccurate: Boolean): Boolean =
        ensureModelLoaded(ModelTier.fromAccurate(isAccurate))

    suspend fun translate(
        text: String,
        from: LangCode,
        to: LangCode,
        tier: ModelTier,
        onPartial: (String) -> Unit = {}
    ): String = withContext(Dispatchers.Default) {
        if (text.isBlank()) return@withContext ""

        val isReady = ensureModelLoaded(tier)
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

    /** Legacy boolean API — delegates to [translate] with the matching tier. */
    suspend fun translate(
        text: String,
        from: LangCode,
        to: LangCode,
        isAccurate: Boolean = false,
        onPartial: (String) -> Unit = {}
    ): String = translate(text, from, to, ModelTier.fromAccurate(isAccurate), onPartial)

    /**
     * @param glossary user terms for this pair; only those present in [text] are
     *                 injected, and only as Tencent's `Reference the following
     *                 translations:` block. Empty means "no change from before
     *                 the glossary existed", which keeps this function honest.
     */
    fun buildPrompt(
        text: String,
        from: LangCode,
        to: LangCode,
        glossary: List<GlossaryEntry> = emptyList()
    ): String {
        // Format zgodny z oficjalnym repo Tencent Hy-MT2 (llama-completion -p):
        // "Translate the following segment into <TARGET>, without additional explanation：<TEXT>"
        // Używamy pełnych nazw języków (English names), bo model tak wymaga.
        val block = GlossaryPrompt.build(glossary, text)
        return block +
            "Translate the following segment into ${to.englishName}, without additional explanation：$text"
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
        tier: ModelTier,
        onPartial: (String) -> Unit = {}
    ): String = withContext(Dispatchers.Default) {
        if (text.isBlank()) return@withContext ""

        val segments = splitIntoSegments(text.trim())
        // Single segment: behave exactly like translate() (keeps the streaming contract).
        if (segments.size <= 1) {
            return@withContext translate(text, from, to, tier, onPartial)
        }

        val sb = StringBuilder()
        segments.forEachIndexed { i, seg ->
            // Translate each segment WITHOUT its own partial (avoid mid-flight flashes);
            // we emit the cumulative combined result after every segment completes.
            val part = translate(seg, from, to, tier)
            sb.append(part)
            if (i < segments.lastIndex) sb.append("\n\n")
            onPartial(sb.toString())
        }
        sb.toString()
    }

    /** Legacy boolean API — delegates to [translateSegmented] with the matching tier. */
    suspend fun translateSegmented(
        text: String,
        from: LangCode,
        to: LangCode,
        isAccurate: Boolean = false,
        onPartial: (String) -> Unit = {}
    ): String = translateSegmented(text, from, to, ModelTier.fromAccurate(isAccurate), onPartial)

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

    fun release() {
        if (nativeHandle != 0L) {
            LlamaNativeBridge.freeModelNative(nativeHandle)
            nativeHandle = 0L
            loadedModelPath = null
        }
    }
}
