package com.mrrobot.aiworkspace.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Server-Sent-Events streaming chat client for OpenAI-compatible providers.
 *
 * Replaces the buffered `HttpURLConnection` round-trip in [ProviderChatClient]
 * for the 14 OpenAI-compatible providers (everything except Anthropic and
 * Gemini). The accumulated text is identical to what the buffered client
 * would have returned; the difference is that [onDelta] fires as each chunk
 * arrives, so the UI can paint partial text instead of a "thinking…" wait.
 *
 * Why OkHttp instead of `HttpURLConnection`:
 *  - Real connection pooling. Repeated chat turns to the same provider
 *    reuse the TLS connection (the old code called `disconnect()` on every
 *    request, killing keep-alive).
 *  - Streaming response bodies are first-class — `response.body!!.source()`
 *    gives a `BufferedSource` we can readUtf8Line on without buffering
 *    the whole reply.
 *  - Cancellation propagates: when the caller's coroutine is cancelled
 *    (user tapped Stop, ViewModel cleared, etc.) [coroutineContext.ensureActive]
 *    inside the read loop trips and we close the response cleanly.
 *
 * Thread model: caller is expected to be on a background dispatcher already
 * (e.g. `viewModelScope.launch` → repository → here). We additionally wrap
 * the OkHttp call in `withContext(Dispatchers.IO)` so that the synchronous
 * `client.newCall(request).execute()` doesn't accidentally park whatever
 * dispatcher is current.
 *
 * Provider scope: anything OpenAI-compatible. Anthropic and Gemini speak
 * different SSE shapes (event types vs content blocks); they stay buffered
 * via [ProviderChatClient.generateReply] until ported separately.
 */
object StreamingChatClient {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // Generous read timeout: streaming a long completion can take a
            // while between tokens, especially for reasoning models. 120s
            // here matches the buffered client's previous 90s `readTimeout`
            // with headroom for the per-chunk pauses that don't exist in
            // a buffered call.
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            // Don't auto-retry. The caller owns retry semantics; an auto-
            // retried POST would re-send the user's message and the user
            // would see two assistant turns for one prompt.
            .retryOnConnectionFailure(false)
            .build()
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * True when the provider speaks the OpenAI Chat Completions wire format
     * (and therefore supports `stream: true` with the standard `data: <json>`
     * SSE shape). Used by callers to decide between this client and the
     * buffered fallback in [ProviderChatClient].
     */
    fun supports(provider: ApiProvider): Boolean = when (provider) {
        ApiProvider.OpenRouter,
        ApiProvider.OpenAI,
        ApiProvider.Groq,
        ApiProvider.Mistral,
        ApiProvider.DeepSeek,
        ApiProvider.XAI,
        ApiProvider.Cohere,
        ApiProvider.Perplexity,
        ApiProvider.Together,
        ApiProvider.Fireworks,
        ApiProvider.Moonshot,
        ApiProvider.ZAI,
        ApiProvider.NvidiaNim,
        ApiProvider.HuggingFace -> true

        // Different wire formats — handled buffered for now.
        ApiProvider.Anthropic,
        ApiProvider.Gemini -> false
    }

    /**
     * Stream a chat completion. Returns the fully-accumulated reply text
     * (same shape as [ProviderChatClient.generateReply]) and invokes
     * [onDelta] with each chunk as it arrives.
     *
     * [onDelta] runs on whatever thread the OkHttp BufferedSource read
     * delivered the chunk on (an OkHttp dispatcher thread). Callers should
     * keep [onDelta] cheap or marshal to the main thread themselves.
     */
    suspend fun streamReply(
        provider: ApiProvider,
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        onDelta: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val url = endpointFor(provider)
        val payload = buildPayload(model, messages, systemPrompt)

        val requestBuilder = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")

        // OpenRouter requires/recommends these for ranking + telemetry.
        if (provider == ApiProvider.OpenRouter) {
            requestBuilder
                .header("HTTP-Referer", "https://github.com/devxvoid/Mr-Robot-AI-Workspace")
                .header("X-Title", "Mr. Robot AI Workspace")
        }

        val response = client.newCall(requestBuilder.build()).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                val body = runCatching { resp.body?.string().orEmpty() }.getOrDefault("")
                throw IllegalStateException("AI request failed (${resp.code}): ${body.take(800)}")
            }

            val source = resp.body?.source()
                ?: throw IllegalStateException("AI response had no body")

            val accumulated = StringBuilder()
            // SSE format we read here:
            //   data: {"choices":[{"delta":{"content":"Hello"}}]}
            //   data: {"choices":[{"delta":{"content":" world"}}]}
            //   data: [DONE]
            // Lines outside `data:` (event:, id:, blanks) are ignored.
            while (!source.exhausted()) {
                // Honour cancellation from the calling coroutine.
                coroutineContext.ensureActive()

                val rawLine = source.readUtf8Line() ?: break
                if (rawLine.isEmpty()) continue
                if (!rawLine.startsWith("data:")) continue

                val data = rawLine.removePrefix("data:").trim()
                if (data.isEmpty()) continue
                if (data == "[DONE]") break

                val chunk = parseDeltaContent(data)
                if (chunk.isNotEmpty()) {
                    accumulated.append(chunk)
                    // Best-effort delta dispatch. Throwing from onDelta
                    // here would tear down the read loop and lose the
                    // accumulated text — wrap so a UI bug doesn't kill
                    // the stream.
                    runCatching { onDelta(chunk) }
                }
            }

            accumulated.toString().trim()
        }
    }

    private fun endpointFor(provider: ApiProvider): String = when (provider) {
        ApiProvider.OpenRouter -> "https://openrouter.ai/api/v1/chat/completions"
        ApiProvider.OpenAI -> "https://api.openai.com/v1/chat/completions"
        ApiProvider.Groq -> "https://api.groq.com/openai/v1/chat/completions"
        ApiProvider.Mistral -> "https://api.mistral.ai/v1/chat/completions"
        ApiProvider.DeepSeek -> "https://api.deepseek.com/chat/completions"
        ApiProvider.XAI -> "https://api.x.ai/v1/chat/completions"
        ApiProvider.Cohere -> "https://api.cohere.com/compatibility/v1/chat/completions"
        ApiProvider.Perplexity -> "https://api.perplexity.ai/chat/completions"
        ApiProvider.Together -> "https://api.together.xyz/v1/chat/completions"
        ApiProvider.Fireworks -> "https://api.fireworks.ai/inference/v1/chat/completions"
        ApiProvider.Moonshot -> "https://api.moonshot.ai/v1/chat/completions"
        ApiProvider.ZAI -> "https://api.z.ai/api/paas/v4/chat/completions"
        ApiProvider.NvidiaNim -> "https://integrate.api.nvidia.com/v1/chat/completions"
        ApiProvider.HuggingFace -> "https://router.huggingface.co/v1/chat/completions"

        // Defensive: caller should never reach here because [supports]
        // returns false for these. Throwing makes the bug obvious.
        ApiProvider.Anthropic ->
            error("Anthropic uses a non-OpenAI SSE shape; use ProviderChatClient.generateReply.")
        ApiProvider.Gemini ->
            error("Gemini uses a non-OpenAI wire format; use ProviderChatClient.generateReply.")
    }

    /**
     * Build the JSON request body. Identical shape to the buffered client's
     * payload (so providers behave the same), with `stream: true` added.
     * Multimodal user messages keep the OpenAI-style `image_url` parts.
     */
    private fun buildPayload(
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String
    ): JSONObject {
        val jsonMessages = JSONArray()

        jsonMessages.put(
            JSONObject()
                .put("role", "system")
                .put("content", systemPrompt)
        )

        messages.forEach { message ->
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

        return JSONObject()
            .put("model", model)
            .put("messages", jsonMessages)
            .put("temperature", 0.7)
            .put("max_tokens", 1200)
            .put("stream", true)
    }

    /**
     * Extract the incremental content from a single SSE `data:` line. Tolerant
     * of malformed JSON (returns ""), missing `choices` (returns ""), and
     * non-content deltas like role announcements (also returns "").
     *
     * The three shapes we care about, from real provider responses:
     *   {"choices":[{"delta":{"content":"Hi"}}]}            // mid-stream
     *   {"choices":[{"delta":{"role":"assistant"}}]}        // first chunk
     *   {"choices":[{"delta":{},"finish_reason":"stop"}]}   // end
     *
     * Some providers (Perplexity, Mistral) include a `message` field with
     * the full accumulated text; we ignore that and only read deltas, since
     * trusting `message` would double-count tokens.
     */
    private fun parseDeltaContent(data: String): String {
        return try {
            val obj = JSONObject(data)
            val choices = obj.optJSONArray("choices") ?: return ""
            if (choices.length() == 0) return ""
            val first = choices.optJSONObject(0) ?: return ""
            val delta = first.optJSONObject("delta") ?: return ""
            delta.optString("content", "")
        } catch (_: Throwable) {
            ""
        }
    }
}
