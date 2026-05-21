package com.mrrobot.aiworkspace.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object ProviderChatClient {

    /**
     * Stream a chat completion when the provider supports OpenAI-compatible
     * SSE; otherwise fall through to the buffered [generateReply] path.
     *
     * [onDelta] is invoked on whatever thread the OkHttp BufferedSource read
     * delivered the chunk on. For Anthropic/Gemini (non-OpenAI shape),
     * [onDelta] is fired ONCE with the full reply once the buffered call
     * returns — so callers can use a single rendering path regardless of
     * whether the underlying provider streams or not.
     *
     * Tool-loop intermediate turns deliberately don't stream: we only want
     * the final answer to paint progressively. See
     * [ChatRepository.sendMessage]'s `onAssistantDelta` parameter.
     */
    suspend fun streamReply(
        settings: AppSettings,
        messages: List<ChatMessage>,
        systemPrompt: String? = null,
        onDelta: (String) -> Unit
    ): String {
        val apiKey = settings.activeApiKey()
        val provider = settings.selectedProvider
        val model = settings.activeModel()

        if (apiKey.isBlank()) {
            throw IllegalStateException("No active AI model. Open Settings, add an API key, then tap Save & Activate.")
        }

        val effectiveSystemPrompt = systemPrompt?.takeIf { it.isNotBlank() }
            ?: DEFAULT_SYSTEM_PROMPT

        return if (StreamingChatClient.supports(provider)) {
            StreamingChatClient.streamReply(
                provider = provider,
                apiKey = apiKey,
                model = model,
                messages = messages,
                systemPrompt = effectiveSystemPrompt,
                onDelta = onDelta
            )
        } else {
            // Anthropic / Gemini: buffered fallback. Emit the whole thing as
            // a single delta so the caller gets the same shape it would for
            // a streamed reply.
            val full = generateReply(settings, messages, systemPrompt)
            if (full.isNotEmpty()) onDelta(full)
            full
        }
    }

    suspend fun generateReply(
        settings: AppSettings,
        messages: List<ChatMessage>,
        systemPrompt: String? = null
    ): String = withContext(Dispatchers.IO) {
        val apiKey = settings.activeApiKey()
        val provider = settings.selectedProvider
        val model = settings.activeModel()

        if (apiKey.isBlank()) {
            throw IllegalStateException("No active AI model. Open Settings, add an API key, then tap Save & Activate.")
        }

        val effectiveSystemPrompt = systemPrompt?.takeIf { it.isNotBlank() }
            ?: DEFAULT_SYSTEM_PROMPT

        when (provider) {
            ApiProvider.OpenRouter -> openAiCompatible(
                url = "https://openrouter.ai/api/v1/chat/completions",
                apiKey = apiKey,
                model = model,
                messages = messages,
                systemPrompt = effectiveSystemPrompt,
                extraHeaders = mapOf(
                    "HTTP-Referer" to "https://github.com/devxvoid/Mr-Robot-AI-Workspace",
                    "X-Title" to "Mr. Robot AI Workspace"
                )
            )

            ApiProvider.OpenAI -> openAiCompatible(
                url = "https://api.openai.com/v1/chat/completions",
                apiKey = apiKey,
                model = model,
                messages = messages,
                systemPrompt = effectiveSystemPrompt
            )

            ApiProvider.Groq -> openAiCompatible(
                url = "https://api.groq.com/openai/v1/chat/completions",
                apiKey = apiKey,
                model = model,
                messages = messages,
                systemPrompt = effectiveSystemPrompt
            )

            ApiProvider.Mistral -> openAiCompatible(
                url = "https://api.mistral.ai/v1/chat/completions",
                apiKey = apiKey,
                model = model,
                messages = messages,
                systemPrompt = effectiveSystemPrompt
            )

            ApiProvider.DeepSeek -> openAiCompatible(
                url = "https://api.deepseek.com/chat/completions",
                apiKey = apiKey,
                model = model,
                messages = messages,
                systemPrompt = effectiveSystemPrompt
            )

            ApiProvider.XAI -> openAiCompatible(
                url = "https://api.x.ai/v1/chat/completions",
                apiKey = apiKey,
                model = model,
                messages = messages,
                systemPrompt = effectiveSystemPrompt
            )

            ApiProvider.Cohere -> openAiCompatible(
                url = "https://api.cohere.com/compatibility/v1/chat/completions",
                apiKey = apiKey,
                model = model,
                messages = messages,
                systemPrompt = effectiveSystemPrompt
            )

            ApiProvider.Perplexity -> openAiCompatible(
                url = "https://api.perplexity.ai/chat/completions",
                apiKey = apiKey,
                model = model,
                messages = messages,
                systemPrompt = effectiveSystemPrompt
            )

            ApiProvider.Together -> openAiCompatible(
                url = "https://api.together.xyz/v1/chat/completions",
                apiKey = apiKey,
                model = model,
                messages = messages,
                systemPrompt = effectiveSystemPrompt
            )

            ApiProvider.Fireworks -> openAiCompatible(
                url = "https://api.fireworks.ai/inference/v1/chat/completions",
                apiKey = apiKey,
                model = model,
                messages = messages,
                systemPrompt = effectiveSystemPrompt
            )

            ApiProvider.Moonshot -> openAiCompatible(
                url = "https://api.moonshot.ai/v1/chat/completions",
                apiKey = apiKey,
                model = model,
                messages = messages,
                systemPrompt = effectiveSystemPrompt
            )

            ApiProvider.ZAI -> openAiCompatible(
                url = "https://api.z.ai/api/paas/v4/chat/completions",
                apiKey = apiKey,
                model = model,
                messages = messages,
                systemPrompt = effectiveSystemPrompt
            )

            ApiProvider.NvidiaNim -> openAiCompatible(
                url = "https://integrate.api.nvidia.com/v1/chat/completions",
                apiKey = apiKey,
                model = model,
                messages = messages,
                systemPrompt = effectiveSystemPrompt
            )

            ApiProvider.HuggingFace -> openAiCompatible(
                url = "https://router.huggingface.co/v1/chat/completions",
                apiKey = apiKey,
                model = model,
                messages = messages,
                systemPrompt = effectiveSystemPrompt
            )

            ApiProvider.Anthropic -> anthropic(
                apiKey = apiKey,
                model = model,
                messages = messages,
                systemPrompt = effectiveSystemPrompt
            )

            ApiProvider.Gemini -> gemini(
                apiKey = apiKey,
                model = model,
                messages = messages,
                systemPrompt = effectiveSystemPrompt
            )
        }
    }

    private const val DEFAULT_SYSTEM_PROMPT =
        "You are ALPHA inside Mr. Robot AI Workspace. Be precise, practical, and helpful."

    private fun openAiCompatible(
        url: String,
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        extraHeaders: Map<String, String> = emptyMap()
    ): String {
        val jsonMessages = JSONArray()

        jsonMessages.put(
            JSONObject()
                .put("role", "system")
                .put("content", systemPrompt)
        )

        messages.forEach { message ->
            // If a user message has attached images, send a multimodal
            // content array (OpenAI-compatible "image_url" parts). Otherwise
            // fall back to a plain text content field for max compatibility.
            if (message.role == "user" && message.imageDataUrls.isNotEmpty()) {
                val parts = JSONArray()
                if (message.content.isNotBlank()) {
                    parts.put(
                        JSONObject()
                            .put("type", "text")
                            .put("text", message.content)
                    )
                }
                message.imageDataUrls.forEach { dataUrl ->
                    parts.put(
                        JSONObject()
                            .put("type", "image_url")
                            .put(
                                "image_url",
                                JSONObject().put("url", dataUrl)
                            )
                    )
                }
                jsonMessages.put(
                    JSONObject()
                        .put("role", message.role)
                        .put("content", parts)
                )
            } else {
                jsonMessages.put(
                    JSONObject()
                        .put("role", message.role)
                        .put("content", message.content)
                )
            }
        }

        val payload = JSONObject()
            .put("model", model)
            .put("messages", jsonMessages)
            .put("temperature", 0.7)
            .put("max_tokens", 1200)

        val response = postJson(
            url = url,
            body = payload,
            headers = mapOf(
                "Authorization" to "Bearer $apiKey",
                "Content-Type" to "application/json"
            ) + extraHeaders
        )

        return JSONObject(response)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()
    }

    private fun anthropic(
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String
    ): String {
        val anthropicMessages = JSONArray()

        messages
            .filter { it.role == "user" || it.role == "assistant" }
            .forEach { message ->
                if (message.role == "user" && message.imageDataUrls.isNotEmpty()) {
                    // Anthropic multimodal: content is an array with
                    // {type:"text", ...} and {type:"image", source:{...}} blocks.
                    val content = JSONArray()
                    if (message.content.isNotBlank()) {
                        content.put(
                            JSONObject()
                                .put("type", "text")
                                .put("text", message.content)
                        )
                    }
                    message.imageDataUrls.forEach { dataUrl ->
                        val (mediaType, b64) = splitDataUrl(dataUrl)
                            ?: return@forEach
                        content.put(
                            JSONObject()
                                .put("type", "image")
                                .put(
                                    "source",
                                    JSONObject()
                                        .put("type", "base64")
                                        .put("media_type", mediaType)
                                        .put("data", b64)
                                )
                        )
                    }
                    anthropicMessages.put(
                        JSONObject()
                            .put("role", message.role)
                            .put("content", content)
                    )
                } else {
                    anthropicMessages.put(
                        JSONObject()
                            .put("role", message.role)
                            .put("content", message.content)
                    )
                }
            }

        val payload = JSONObject()
            .put("model", model)
            .put("max_tokens", 1200)
            .put("system", systemPrompt)
            .put("messages", anthropicMessages)

        val response = postJson(
            url = "https://api.anthropic.com/v1/messages",
            body = payload,
            headers = mapOf(
                "x-api-key" to apiKey,
                "anthropic-version" to "2023-06-01",
                "Content-Type" to "application/json"
            )
        )

        return JSONObject(response)
            .getJSONArray("content")
            .getJSONObject(0)
            .getString("text")
            .trim()
    }

    private fun gemini(
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String
    ): String {
        // Build a single contents array. We collapse the system prompt and
        // assistant/user history into one composite turn, then attach any
        // images from the most-recent user message as inline_data parts.
        val textPrompt = buildString {
            append("SYSTEM: ").append(systemPrompt).append("\n\n")
            messages.forEach { message ->
                append(message.role.uppercase()).append(": ")
                    .append(message.content).append("\n\n")
            }
        }.trim()

        val parts = JSONArray()
        parts.put(JSONObject().put("text", textPrompt))

        // Attach images from the latest user message, if any.
        messages.lastOrNull { it.role == "user" }?.imageDataUrls
            ?.forEach { dataUrl ->
                val (mime, b64) = splitDataUrl(dataUrl) ?: return@forEach
                parts.put(
                    JSONObject().put(
                        "inline_data",
                        JSONObject()
                            .put("mime_type", mime)
                            .put("data", b64)
                    )
                )
            }

        val endpoint =
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=" +
                URLEncoder.encode(apiKey, "UTF-8")

        val payload = JSONObject()
            .put(
                "contents",
                JSONArray().put(JSONObject().put("parts", parts))
            )

        val response = postJson(
            url = endpoint,
            body = payload,
            headers = mapOf("Content-Type" to "application/json")
        )

        return JSONObject(response)
            .getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
            .trim()
    }

    private fun postJson(
        url: String,
        body: JSONObject,
        headers: Map<String, String>
    ): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30000
            readTimeout = 90000
            doOutput = true
            headers.forEach { (key, value) ->
                setRequestProperty(key, value)
            }
        }

        OutputStreamWriter(connection.outputStream).use { writer ->
            writer.write(body.toString())
            writer.flush()
        }

        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream

        val response = BufferedReader(InputStreamReader(stream)).use { reader ->
            reader.readText()
        }

        connection.disconnect()

        if (status !in 200..299) {
            throw IllegalStateException("AI request failed ($status): $response")
        }

        return response
    }

    /**
     * Split a data URL like `data:image/jpeg;base64,XXXX` into
     * (mime_type, base64_payload). Returns null if [dataUrl] doesn't
     * match the expected format.
     */
    private fun splitDataUrl(dataUrl: String): Pair<String, String>? {
        if (!dataUrl.startsWith("data:")) return null
        val comma = dataUrl.indexOf(',')
        if (comma <= 0) return null
        val header = dataUrl.substring(5, comma) // skip "data:"
        val payload = dataUrl.substring(comma + 1)
        val semi = header.indexOf(';')
        val mime = if (semi > 0) header.substring(0, semi) else header
        if (mime.isBlank() || payload.isBlank()) return null
        return mime to payload
    }
}
