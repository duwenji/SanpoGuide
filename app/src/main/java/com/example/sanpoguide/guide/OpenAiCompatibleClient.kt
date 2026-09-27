package com.example.sanpoguide.guide

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Any service exposing the OpenAI Chat Completions API (DeepSeek, OpenAI, Gemini, ...). */
class OpenAiCompatibleClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
) : LlmClient {

    override suspend fun generate(system: String, user: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("model", model)
            .put(
                "messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", user))
            )
            .toString()
            .toRequestBody("application/json".toMediaType())

        val request = try {
            Request.Builder()
                .url(baseUrl.trimEnd('/') + "/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .post(body)
                .build()
        } catch (e: IllegalArgumentException) {
            throw GuideException("接続先URLが正しくありません", e)
        }

        try {
            http.newCall(request).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    throw GuideException(messageForStatus(res.code, errorDetail(text)))
                }
                JSONObject(text)
                    .getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").optString("content")
                    .trim()
                    .ifEmpty { throw GuideException("AIから空の応答が返りました") }
            }
        } catch (e: IOException) {
            throw GuideException("AIサービスに接続できませんでした", e)
        } catch (e: JSONException) {
            throw GuideException("AIサービスの応答を解釈できませんでした", e)
        }
    }

    private fun errorDetail(body: String): String? = try {
        JSONObject(body).optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
    } catch (e: JSONException) {
        null
    }

    private companion object {
        val http: OkHttpClient = OkHttpClient.Builder()
            .callTimeout(90, TimeUnit.SECONDS)
            .build()
    }
}
