package com.example.aicoder

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

class AiClient(
    baseUrl: String,
    private val apiKey: String
) {
    private val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    fun streamChatCompletion(request: ChatRequest): Flow<String> = callbackFlow {
        val body = json.encodeToString(request)
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val builder = Request.Builder()
            .url("$normalizedBaseUrl/chat/completions")
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .post(body)

        if (apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer $apiKey")
        }

        val eventSource = EventSources.createFactory(client).newEventSource(
            builder.build(),
            object : EventSourceListener() {
                override fun onOpen(eventSource: EventSource, response: Response) = Unit

                override fun onEvent(
                    eventSource: EventSource,
                    id: String?,
                    type: String?,
                    data: String
                ) {
                    if (data == "[DONE]") {
                        close()
                    } else if (data.isNotBlank()) {
                        trySend(data).isSuccess
                    }
                }

                override fun onFailure(
                    eventSource: EventSource,
                    t: Throwable?,
                    response: Response?
                ) {
                    val code = response?.code
                    val bodyText = runCatching { response?.body?.string() }.getOrNull().orEmpty()
                    val message = if (code != null) {
                        "API error $code${if (bodyText.isNotBlank()) ": $bodyText" else ""}"
                    } else {
                        t?.message ?: "Network error"
                    }
                    close(IllegalStateException(message, t))
                }

                override fun onClosed(eventSource: EventSource) {
                    close()
                }
            }
        )

        awaitClose { eventSource.cancel() }
    }
}
