package com.studyspark.app.ai.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class GeminiClient(
    private val apiKeyProvider: () -> String?,
    private val http: OkHttpClient = defaultClient(),
    private val model: String = "gemini-3.6-flash"
) : LlmClient {
    override val provider: LlmProvider = LlmProvider.GEMINI
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun chat(request: LlmChatRequest): LlmChatResponse = withContext(Dispatchers.IO) {
        val key = apiKeyProvider()?.trim().orEmpty()
        if (key.isEmpty()) throw LlmException("Gemini API key not set", retryable = false)

        val system = request.messages.filter { it.role == "system" }.joinToString("\n") { it.content }
        val contents = buildJsonArray {
            request.messages.filter { it.role != "system" }.forEach { msg ->
                add(
                    buildJsonObject {
                        put("role", if (msg.role == "assistant") "model" else "user")
                        put(
                            "parts",
                            buildJsonArray {
                                add(buildJsonObject { put("text", msg.content) })
                            }
                        )
                    }
                )
            }
        }

        val body = buildJsonObject {
            if (system.isNotBlank()) {
                put(
                    "systemInstruction",
                    buildJsonObject {
                        put(
                            "parts",
                            buildJsonArray {
                                add(buildJsonObject { put("text", system) })
                            }
                        )
                    }
                )
            }
            put("contents", contents)
            put(
                "generationConfig",
                buildJsonObject {
                    put("temperature", request.temperature)
                    if (request.jsonMode) put("responseMimeType", "application/json")
                }
            )
        }

        val httpRequest = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", key)
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()

        http.newCall(httpRequest).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (response.code == 429 || response.code >= 500) {
                throw LlmException("Gemini unavailable (${response.code})", retryable = true)
            }
            if (!response.isSuccessful) {
                // Fall back to query-param auth for classic AIza keys if header auth fails
                if (response.code == 400 || response.code == 401 || response.code == 403) {
                    val retry = Request.Builder()
                        .url(
                            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"
                        )
                        .post(body.toString().toRequestBody(JSON))
                        .build()
                    http.newCall(retry).execute().use { second ->
                        val raw2 = second.body?.string().orEmpty()
                        if (second.code == 429 || second.code >= 500) {
                            throw LlmException("Gemini unavailable (${second.code})", retryable = true)
                        }
                        if (!second.isSuccessful) {
                            throw LlmException(
                                "Gemini error ${second.code}: ${summarizeApiError(raw2.ifBlank { raw })}",
                                retryable = false
                            )
                        }
                        return@withContext parseGeminiResponse(raw2)
                    }
                }
                throw LlmException(
                    "Gemini error ${response.code}: ${summarizeApiError(raw)}",
                    retryable = false
                )
            }
            parseGeminiResponse(raw)
        }
    }

    private fun parseGeminiResponse(raw: String): LlmChatResponse {
        val parsed = json.parseToJsonElement(raw).jsonObject
        val text = parsed["candidates"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("content")
            ?.jsonObject
            ?.get("parts")
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("text")
            ?.jsonPrimitive
            ?.contentOrNull
            .orEmpty()
        if (text.isBlank()) throw LlmException("Empty Gemini response", retryable = true)
        return LlmChatResponse(text = text, provider = provider, model = model)
    }

    private fun summarizeApiError(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return "(no details)"
        return if (trimmed.length <= 280) trimmed else trimmed.take(280) + "…"
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}

class GroqClient(
    private val apiKeyProvider: () -> String?,
    private val http: OkHttpClient = GeminiClient.defaultClient(),
    private val model: String = "openai/gpt-oss-20b"
) : LlmClient {
    override val provider: LlmProvider = LlmProvider.GROQ
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun chat(request: LlmChatRequest): LlmChatResponse = withContext(Dispatchers.IO) {
        val key = apiKeyProvider()?.trim().orEmpty()
        if (key.isEmpty()) throw LlmException("Groq API key not set", retryable = false)

        val messages = buildJsonArray {
            request.messages.forEach { msg ->
                add(
                    buildJsonObject {
                        put("role", msg.role)
                        put("content", msg.content)
                    }
                )
            }
        }

        val body = buildJsonObject {
            put("model", model)
            put("messages", messages)
            put("temperature", request.temperature)
            if (request.jsonMode) {
                put("response_format", buildJsonObject { put("type", "json_object") })
            }
        }

        val httpRequest = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .header("Authorization", "Bearer $key")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        http.newCall(httpRequest).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (response.code == 429 || response.code >= 500) {
                throw LlmException("Groq unavailable (${response.code})", retryable = true)
            }
            if (!response.isSuccessful) {
                throw LlmException("Groq error ${response.code}: $raw", retryable = false)
            }
            val parsed = json.parseToJsonElement(raw).jsonObject
            val text = parsed["choices"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("message")
                ?.jsonObject
                ?.get("content")
                ?.jsonPrimitive
                ?.contentOrNull
                .orEmpty()
            if (text.isBlank()) throw LlmException("Empty Groq response", retryable = true)
            LlmChatResponse(text = text, provider = provider, model = model)
        }
    }
}
