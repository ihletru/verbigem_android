package com.verbigem.app.data.model

import com.verbigem.app.R

/**
 * A translation model the user can pick for the ONLINE engine.
 *
 * Only the three curated models live here. They always go through Verbigem's
 * backend proxy (which holds Verbigem's own OpenRouter key), so they work for
 * every user with no setup.
 *
 * The ":free" models are NOT listed here on purpose — that set changes almost
 * daily, so we fetch it live from OpenRouter (see
 * [com.verbigem.app.engine.OnlineApiEngine.fetchFreeModels]) and show it only
 * when the user has pasted their own OpenRouter key.
 */
data class OnlineModel(
    val id: String,
    val labelResId: Int,
    val descResId: Int
)

object OnlineModels {
    /** Shipped default — best quality-per-dollar of the curated three. */
    const val DEFAULT_ID = "deepseek/deepseek-v4-flash-latest"

    val CURATED: List<OnlineModel> = listOf(
        OnlineModel(
            id = DEFAULT_ID,
            labelResId = R.string.online_model_deepseek_label,
            descResId = R.string.online_model_deepseek_desc
        ),
        OnlineModel(
            id = "google/gemini-3.8-flash",
            labelResId = R.string.online_model_gemini_label,
            descResId = R.string.online_model_gemini_desc
        ),
        OnlineModel(
            id = "anthropic/claude-opus-5",
            labelResId = R.string.online_model_claude_label,
            descResId = R.string.online_model_claude_desc
        )
    )

    fun fromId(id: String): OnlineModel =
        CURATED.find { it.id == id } ?: CURATED.first()
}

/**
 * A ":free" model fetched live from OpenRouter. [name] is the display name
 * returned by the API (e.g. "NVIDIA Nemotron 3.5 Lightning (free)").
 */
data class FreeModelInfo(
    val id: String,
    val name: String,
    val contextLength: Int
)
