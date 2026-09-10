package com.verbigem.app.data.model

import android.content.Context
import android.util.Log
import com.verbigem.app.R
import com.verbigem.app.engine.ModelDownloader

private const val TAG = "EngineChoice"

enum class EngineChoice(
    val id: String,
    val icon: String,
    val labelResId: Int,
    /**
     * Krótka nazwa silnika do wstawienia W ŚRODEK zdania („Tłumacz (Szybki)",
     * „TŁUMACZENIE (Szybki):"). Różni się od [labelResId], który jest pełnym
     * opisem z rozmiarem („Wolny ale dokładny ~1.1 GB") i od [captionResId],
     * który jest pisany małymi literami pod ikoną.
     */
    val shortNameResId: Int,
    val descriptionResId: Int,
    /** Krótki podpis pod ikoną (11.sp, jak w menu dolnym). */
    val captionResId: Int,
    /** Tytuł okna pomocy po długim naciśnięciu ikony. */
    val helpTitleResId: Int,
    /** Treść okna pomocy po długim naciśnięciu ikony. */
    val helpTextResId: Int,
    val isProOnly: Boolean,
    /**
     * Które wagi GGUF ten silnik ładuje — `null` dla silników bez modelu
     * lokalnego (BOTH używa dwóch, ONLINE żadnego).
     */
    val modelTier: ModelTier? = null
) {
    LOCAL_FAST(
        "localFast", "⚡",
        R.string.engine_fast_label, R.string.engine_name_fast, R.string.engine_fast_desc,
        R.string.engine_caption_fast,
        R.string.help_engine_fast_title, R.string.help_engine_fast,
        false,
        ModelTier.FAST
    ),
    LOCAL_ACCURATE(
        "localAccurate", "🎯",
        R.string.engine_accurate_label, R.string.engine_name_accurate, R.string.engine_accurate_desc,
        R.string.engine_caption_accurate,
        R.string.help_engine_accurate_title, R.string.help_engine_accurate,
        true,
        ModelTier.ACCURATE
    ),
    LOCAL_PRO_7B(
        "localPro7B", "🧠",
        R.string.engine_pro7b_label, R.string.engine_name_pro7b, R.string.engine_pro7b_desc,
        R.string.engine_caption_pro7b,
        R.string.help_engine_pro7b_title, R.string.help_engine_pro7b,
        true,
        ModelTier.PRO_7B
    ),
    BOTH(
        "both", "⚖️",
        R.string.engine_both_label, R.string.engine_name_both, R.string.engine_both_desc,
        R.string.engine_caption_both,
        R.string.help_engine_both_title, R.string.help_engine_both,
        true
    ),
    ONLINE(
        "online", "☁️",
        R.string.engine_online_label, R.string.engine_name_online, R.string.engine_online_desc,
        R.string.engine_caption_online,
        R.string.help_engine_online_title, R.string.help_engine_online,
        true
    );

    /** Silniki, które ładują dokładnie jeden plik GGUF. */
    val isSingleModel: Boolean get() = modelTier != null

    companion object {
        fun fromId(id: String): EngineChoice {
            return entries.find { it.id == id } ?: LOCAL_FAST
        }
    }
}

/**
 * Silniki, dla których to urządzenie spełnia wymagania RAM-u, miejsca na dysku
 * i (dla tierów GPU) ma działający backend.
 *
 * Wspólna dla Tłumacza i OCR — jeden werdykt zamiast dwóch rozjeżdżających się
 * kopii. Bez tego użytkownik zobaczyłby ikonę, pobrał 1.1 GB, a potem dostał
 * cichego OOM-a przy ładowaniu.
 *
 * Logujemy wynik — to jedyny sposób, żeby na żywym telefonie sprawdzić,
 * dlaczego dany silnik jest widoczny albo nie, bez zgadywania.
 */
fun availableEngines(context: Context): List<EngineChoice> =
    EngineChoice.entries.filter { engine ->
        // Pro 7B wyłączony (2026-09): na buildzie CPU-only dekoduje ~1.6 tok/s,
        // czyli jest bezużyteczny. Gałąź w enumie i dyspozytorni zostaje, żeby
        // dało się go kiedyś włączyć, ale nie oferujemy go w UI.
        if (engine == EngineChoice.LOCAL_PRO_7B) {
            Log.i(TAG, "engine ${engine.id} -> DISABLED (Pro 7B paused)")
            return@filter false
        }
        val tier = engine.modelTier
        if (tier == null) return@filter true
        val reason = ModelDownloader.blockReason(context, tier)
        Log.i(TAG, "engine ${engine.id} (tier ${tier.id}) -> $reason")
        reason == ModelTierBlockReason.NONE
    }
