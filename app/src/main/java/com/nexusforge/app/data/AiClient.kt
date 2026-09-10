package com.nexusforge.app.data

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

/**
 * Talks to any OpenAI-compatible `/chat/completions` endpoint (OpenAI, DeepSeek, Ollama,
 * Gemini's OpenAI shim, LM Studio, vLLM, a custom gateway...). Provider-agnostic by design:
 * the base URL and key are the only things that change between "providers".
 */
class AiClient(private val config: ProviderConfig) {

    private val normalizedBaseUrl = config.baseUrl.trim().trimEnd('/')

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // streaming: no read timeout
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    /** Raw SSE `data:` payload lines, one per emission, terminated by [DONE] or close(). */
    fun streamChatCompletion(request: ChatRequest): Flow<String> = callbackFlow {
        val body = json.encodeToString(request)
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val builder = Request.Builder()
            .url("$normalizedBaseUrl/chat/completions")
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .post(body)

        if (config.apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer ${config.apiKey}")
        }

        val eventSource = EventSources.createFactory(client).newEventSource(
            builder.build(),
            object : EventSourceListener() {
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    if (data == "[DONE]") {
                        close()
                    } else if (data.isNotBlank()) {
                        trySend(data)
                    }
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    val code = response?.code
                    val bodyText = runCatching { response?.body?.string() }.getOrNull().orEmpty()
                    val message = when {
                        code == 401 || code == 403 -> "Provider rejected the request (HTTP $code) — check your API key."
                        code != null -> "Provider error $code${if (bodyText.isNotBlank()) ": ${bodyText.take(300)}" else ""}"
                        t != null -> "Connection failed: ${t.message ?: t::class.simpleName}"
                        else -> "Unknown network error."
                    }
                    close(IllegalStateException(message, t))
                }

                override fun onClosed(eventSource: EventSource) { close() }
            }
        )

        awaitClose {
            // Cancelling a still-active streaming call can force a real, blocking socket
            // close — same class of bug as close() below, and the more likely trigger of the
            // two: this runs at the end of EVERY stream (not just final cleanup), and a
            // longer-running response (e.g. a large code block) leaves a wider window where
            // the connection is still genuinely live when this fires. Dispatch it off Main.
            Thread { runCatching { eventSource.cancel() } }.start()
        }
    }

    /**
     * Closing an OkHttp connection pool can perform a real, blocking network write (the TLS
     * close_notify) on whatever thread calls it. Every caller of this — including the
     * `finally` block after an agent turn, and the Organizer's cleanup — runs on
     * viewModelScope's Main dispatcher, so evicting synchronously there is a guaranteed
     * NetworkOnMainThreadException (Android enforces this at the OS level for targetSdk ≥ 11;
     * it's not just a StrictMode warning). Running the actual eviction on a throwaway thread
     * makes close() safe to call from anywhere, including inside a cancelled coroutine's
     * `finally` block, without every call site needing to remember withContext(Dispatchers.IO).
     */
    fun close() {
        Thread {
            runCatching {
                client.dispatcher.cancelAll()
                client.connectionPool.evictAll()
            }
        }.start()
    }
}
