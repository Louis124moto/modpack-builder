package dev.louis.modpackbuilder.data.api

import dev.louis.modpackbuilder.data.local.AppSettings
import dev.louis.modpackbuilder.data.local.LlmProvider
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class LlmClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun identifyMods(description: String, settings: AppSettings): List<String> {
        require(settings.apiKey.isNotBlank()) { "Configure d'abord une clé API dans les paramètres." }
        val system = """Tu es un assistant de sélection de mods Minecraft. À partir de la demande, identifie uniquement des noms de projets de mods Modrinth pertinents. Réponds exclusivement avec un tableau JSON de chaînes, sans Markdown, sans commentaire. N'invente pas de noms de mods. Limite-toi à 30 projets."""
        val body = when (settings.provider) {
            LlmProvider.OPENAI_COMPATIBLE -> buildJsonObject {
                put("model", settings.model)
                put("temperature", 0.1)
                putJsonArray("messages") {
                    addJsonObject { put("role", "system"); put("content", system) }
                    addJsonObject { put("role", "user"); put("content", description) }
                }
            }
            LlmProvider.ANTHROPIC -> buildJsonObject {
                put("model", settings.model)
                put("max_tokens", 1200)
                put("system", system)
                putJsonArray("messages") { addJsonObject { put("role", "user"); put("content", description) } }
            }
            LlmProvider.GEMINI -> buildJsonObject {
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") { addJsonObject { put("text", system) } }
                }
                putJsonArray("contents") {
                    addJsonObject {
                        put("role", "user")
                        putJsonArray("parts") { addJsonObject { put("text", description) } }
                    }
                }
                putJsonObject("generationConfig") {
                    put("temperature", 0.1)
                    put("responseMimeType", "application/json")
                }
            }
        }
        val url = when (settings.provider) {
            LlmProvider.OPENAI_COMPATIBLE -> settings.baseUrl.trimEnd('/') + "/chat/completions"
            LlmProvider.ANTHROPIC -> "https://api.anthropic.com/v1/messages"
            LlmProvider.GEMINI -> "https://generativelanguage.googleapis.com/v1beta/models/${settings.model}:generateContent"
        }
        val request = Request.Builder().url(url)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("Content-Type", "application/json")
            .apply {
                when (settings.provider) {
                    LlmProvider.ANTHROPIC -> {
                        header("x-api-key", settings.apiKey)
                        header("anthropic-version", "2023-06-01")
                    }
                    LlmProvider.GEMINI -> header("x-goog-api-key", settings.apiKey)
                    LlmProvider.OPENAI_COMPATIBLE -> header("Authorization", "Bearer ${settings.apiKey}")
                }
            }.build()
        val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            http.newCall(request).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) error("Erreur LLM HTTP ${res.code}: ${text.take(500)}")
                text
            }
        }
        val root = json.parseToJsonElement(response).jsonObject
        val content = when (settings.provider) {
            LlmProvider.ANTHROPIC ->
                root["content"]?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
            LlmProvider.GEMINI ->
                root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("content")?.jsonObject?.get("parts")?.jsonArray
                    ?.mapNotNull { part -> part.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
                    ?.joinToString("")
            LlmProvider.OPENAI_COMPATIBLE ->
                root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
        } ?: error("Réponse LLM vide ou inattendue.")
        val cleaned = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val array = json.parseToJsonElement(cleaned).jsonArray
        return array.mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
            .filter { it.isNotBlank() }.distinct().take(30)
    }
}
