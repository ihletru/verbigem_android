package com.verbigem.app.data.model

import com.verbigem.app.R

enum class EngineChoice(
    val id: String,
    val icon: String,
    val labelResId: Int,
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
        R.string.engine_fast_label, R.string.engine_fast_desc,
        R.string.engine_caption_fast,
        R.string.help_engine_fast_title, R.string.help_engine_fast,
        false,
        ModelTier.FAST
    ),
    LOCAL_ACCURATE(
        "localAccurate", "🎯",
        R.string.engine_accurate_label, R.string.engine_accurate_desc,
        R.string.engine_caption_accurate,
        R.string.help_engine_accurate_title, R.string.help_engine_accurate,
        true,
        ModelTier.ACCURATE
    ),
    LOCAL_PRO_7B(
        "localPro7B", "🧠",
        R.string.engine_pro7b_label, R.string.engine_pro7b_desc,
        R.string.engine_caption_pro7b,
        R.string.help_engine_pro7b_title, R.string.help_engine_pro7b,
        true,
        ModelTier.PRO_7B
    ),
    BOTH(
        "both", "⚖️",
        R.string.engine_both_label, R.string.engine_both_desc,
        R.string.engine_caption_both,
        R.string.help_engine_both_title, R.string.help_engine_both,
        true
    ),
    ONLINE(
        "online", "☁️",
        R.string.engine_online_label, R.string.engine_online_desc,
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
