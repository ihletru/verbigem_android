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
    val isProOnly: Boolean
) {
    LOCAL_FAST(
        "localFast", "⚡",
        R.string.engine_fast_label, R.string.engine_fast_desc,
        R.string.engine_caption_fast,
        R.string.help_engine_fast_title, R.string.help_engine_fast,
        false
    ),
    LOCAL_ACCURATE(
        "localAccurate", "🎯",
        R.string.engine_accurate_label, R.string.engine_accurate_desc,
        R.string.engine_caption_accurate,
        R.string.help_engine_accurate_title, R.string.help_engine_accurate,
        true
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

    companion object {
        fun fromId(id: String): EngineChoice {
            return entries.find { it.id == id } ?: LOCAL_FAST
        }
    }
}
