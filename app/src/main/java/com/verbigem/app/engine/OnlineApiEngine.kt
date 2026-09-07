package com.verbigem.app.engine

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.verbigem.app.data.model.FreeModelInfo
import com.verbigem.app.data.model.LangCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class OnlineApiEngine {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Verbigem's backend proxy — holds Verbigem's own OpenRouter key, so the
    // three curated (paid) models work for every user without any setup.
    private val proxyEndpoint =
        "https://europe-west1-mini-verbigem.cloudfunctions.net/deepseekProxy"
    private val openRouterChatEndpoint = "https://openrouter.ai/api/v1/chat/completions"
    private val openRouterModelsEndpoint = "https://openrouter.ai/api/v1/models"

    /**
     * Translates [text] with the chosen online model.
     *
     * Two paths:
     * - [apiKey] == null  -> the text is sent to Verbigem's backend proxy, which
     *   reads [model] and forwards it to OpenRouter using Verbigem's key. Used for
     *   the three curated models. The proxy MUST be updated to read the `model`
     *   field and pass it to OpenRouter (today it hardcodes deepseek).
     * - [apiKey] != null  -> a direct OpenRouter call with the user's own key
     *   (Bearer). Used for ":free" models the user unlocked with their key.
     */
    suspend fun translate(
        text: String,
        from: LangCode,
        to: LangCode,
        model: String = "deepseek/deepseek-v4-flash-latest",
        apiKey: String? = null
    ): String = withContext(Dispatchers.IO) {
        if (apiKey != null) {
            translateDirect(text, from, to, model, apiKey)
        } else {
            translateViaProxy(text, from, to, model)
        }
    }

    private fun buildSystemPrompt(from: LangCode, to: LangCode): String =
        "You are a translation engine. Translate the user's text from " +
            "${from.englishName} into ${to.englishName}. " +
            "Output only the translation, with no explanation, no quotes, " +
            "and no extra commentary."

    private suspend fun translateDirect(
        text: String,
        from: LangCode,
        to: LangCode,
        model: String,
        apiKey: String
    ): String {
        val messages = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("role", "system")
                addProperty("content", buildSystemPrompt(from, to))
            })
            add(JsonObject().apply {
                addProperty("role", "user")
                addProperty("content", text)
            })
        }
        val payload = JsonObject().apply {
            addProperty("model", model)
            add("messages", messages)
            addProperty("temperature", 0.0)
        }
        val request = Request.Builder()
            .url(openRouterChatEndpoint)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("HTTP-Referer", "https://verbigem.com")
            .addHeader("X-Title", "Verbigem")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            val body = runCatching { response.body?.string()?.take(300) }.getOrNull() ?: ""
            throw IllegalStateException("OpenRouter error: HTTP ${response.code} $body")
        }
        val json = gson.fromJson(response.body?.string() ?: "", JsonObject::class.java)
        val choices = json.getAsJsonArray("choices") ?: return ""
        val first = choices.firstOrNull()?.asJsonObject ?: return ""
        val content = first.getAsJsonObject("message")?.get("content")?.asString
        return content?.trim() ?: ""
    }

    private suspend fun translateViaProxy(
        text: String,
        from: LangCode,
        to: LangCode,
        model: String
    ): String {
        val payload = JsonObject().apply {
            addProperty("text", text)
            addProperty("fromLang", from.code)
            addProperty("toLang", to.code)
            addProperty("model", model)
        }
        val request = Request.Builder()
            .url(proxyEndpoint)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IllegalStateException("API error: HTTP ${response.code}")
        }
        val json = gson.fromJson(response.body?.string() ?: "", JsonObject::class.java)
        return json.get("translation")?.asString ?: json.get("text")?.asString ?: ""
    }

    /**
     * Fetches the current list of OpenRouter ":free" models. The set changes
     * almost daily, so we never hardcode it — call this whenever the user's own
     * key is present. Only truly-free models (both prompt and completion cost 0)
     * are returned, sorted by context length (largest first).
     */
    suspend fun fetchFreeModels(apiKey: String): List<FreeModelInfo> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(openRouterModelsEndpoint)
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) return@withContext emptyList()
        val json = gson.fromJson(response.body?.string() ?: "", JsonObject::class.java)
        val data = json.getAsJsonArray("data") ?: return@withContext emptyList()
        data.mapNotNull { el ->
            val obj = el.asJsonObject
            val id = obj.get("id")?.asString ?: return@mapNotNull null
            if (!id.endsWith(":free")) return@mapNotNull null
            val pricing = obj.getAsJsonObject("pricing")
            val promptCost = pricing?.get("prompt")?.asString ?: "0"
            val completionCost = pricing?.get("completion")?.asString ?: "0"
            if (promptCost != "0" || completionCost != "0") return@mapNotNull null
            val name = obj.get("name")?.asString ?: id
            val ctx = obj.get("context_length")?.asInt ?: 0
            FreeModelInfo(id = id, name = name, contextLength = ctx)
        }.sortedByDescending { it.contextLength }
    }
}
