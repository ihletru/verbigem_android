package com.verbigem.app.engine

import com.google.gson.Gson
import com.google.gson.JsonObject
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
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun translate(
        text: String,
        from: LangCode,
        to: LangCode,
        proxyEndpoint: String = "https://europe-west1-mini-verbigem.cloudfunctions.net/deepseekProxy"
    ): String = withContext(Dispatchers.IO) {
        val payload = JsonObject().apply {
            addProperty("text", text)
            addProperty("fromLang", from.code)
            addProperty("toLang", to.code)
        }

        val requestBody = payload.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(proxyEndpoint)
            .post(requestBody)
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IllegalStateException("API error: HTTP ${response.code}")
        }

        val responseString = response.body?.string() ?: ""
        val json = gson.fromJson(responseString, JsonObject::class.java)
        json.get("translation")?.asString ?: json.get("text")?.asString ?: ""
    }
}
