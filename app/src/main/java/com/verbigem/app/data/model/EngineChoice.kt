package com.verbigem.app.data.model

enum class EngineChoice(
    val id: String,
    val icon: String,
    val labelKey: String,
    val isProOnly: Boolean
) {
    LOCAL_FAST("localFast", "⚡", "Szybki", false),
    LOCAL_ACCURATE("localAccurate", "🎯", "Wolny ale dokładny ~1.1 GB", true),
    BOTH("both", "⚖️", "Oba (porównaj)", true),
    ONLINE("online", "☁️", "API online", true);

    companion object {
        fun fromId(id: String): EngineChoice {
            return entries.find { it.id == id } ?: LOCAL_FAST
        }
    }
}
