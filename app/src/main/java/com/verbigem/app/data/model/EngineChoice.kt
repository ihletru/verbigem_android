package com.verbigem.app.data.model

import com.verbigem.app.R

enum class EngineChoice(
    val id: String,
    val icon: String,
    val labelResId: Int,
    val descriptionResId: Int,
    val isProOnly: Boolean
) {
    LOCAL_FAST("localFast", "⚡", R.string.engine_fast_label, R.string.engine_fast_desc, false),
    LOCAL_ACCURATE("localAccurate", "🎯", R.string.engine_accurate_label, R.string.engine_accurate_desc, true),
    BOTH("both", "⚖️", R.string.engine_both_label, R.string.engine_both_desc, true),
    ONLINE("online", "☁️", R.string.engine_online_label, R.string.engine_online_desc, true);

    companion object {
        fun fromId(id: String): EngineChoice {
            return entries.find { it.id == id } ?: LOCAL_FAST
        }
    }
}
