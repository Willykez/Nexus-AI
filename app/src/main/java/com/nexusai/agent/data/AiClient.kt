package com.nexusai.agent.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.headers
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpStatement
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

/**
 * Provider-agnostic client for any OpenAI-compatible /chat/completions REST endpoint
 * (OpenAI, Ollama, DeepSeek, Gemini's OpenAI shim, LM Studio, vLLM, etc).
 *
 * Streams the response as Server-Sent Events and emits fine-grained [StreamEvent]s so the UI
 * can render text and tool-call arguments character-by-character as they arrive.
 */
class AiClient(private val config: ProviderConfig) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val http = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 180_000
            socketTimeoutMillis = 180_000
            connectTimeoutMillis = 30_000
        }
    }

    /**
     * Streams a single chat-completion turn. Consumers reduce the returned [StreamEvent]s to
     * reconstruct the assistant message text and any tool calls, then decide whether to execute
     * tools and start another turn.
     */
    fun streamChatCompletion(messages: List<ChatMessage>): Flow<StreamEvent> = flow {
        val request = ChatCompletionRequest(
            model = config.model,
            messages = messages,
            tools = ToolRegistry.all,
            stream = true
        )

        val pendingToolCalls = LinkedHashMap<Int, PendingToolCall>()
        var finishReason: String? = null

        try {
            val statement: HttpStatement = http.preparePost("${config.baseUrl.trimEnd('/')}/chat/completions") {
                contentType(ContentType.Application.Json)
                headers {
                    if (config.apiKey.isNotBlank()) {
                        append(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
                    }
                }
                setBody(json.encodeToString(ChatCompletionRequest.serializer(), request))
            }

            statement.execute { response ->
                if (!response.status.isSuccess()) {
                    val errorBody = runCatching { response.bodyAsText() }.getOrDefault("")
                    emit(StreamEvent.Error("HTTP ${response.status.value}: ${errorBody.take(400)}"))
                    return@execute
                }

                val channel: ByteReadChannel = response.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break
                    if (line.isBlank()) continue
                    if (!line.startsWith("data:")) continue

                    val payload = line.removePrefix("data:").trim()
                    if (payload == "[DONE]") {
                        emit(StreamEvent.Done(finishReason))
                        return@execute
                    }

                    val chunk = runCatching {
                        json.decodeFromString(ChatCompletionChunk.serializer(), payload)
                    }.getOrNull() ?: continue

                    val choice = chunk.choices.firstOrNull() ?: continue
                    choice.finishReason?.let { finishReason = it }

                    choice.delta.content?.let { text ->
                        if (text.isNotEmpty()) emit(StreamEvent.ContentDelta(text))
                    }

                    choice.delta.toolCalls?.forEach { deltaCall ->
                        val pending = pendingToolCalls.getOrPut(deltaCall.index) { PendingToolCall() }
                        deltaCall.id?.let { pending.id = it }
                        deltaCall.function?.name?.let { name ->
                            pending.name = name
                            emit(StreamEvent.ToolCallStart(deltaCall.index, pending.id, name))
                        }
                        deltaCall.function?.arguments?.let { argsChunk ->
                            pending.argumentsBuilder.append(argsChunk)
                            emit(StreamEvent.ToolCallArgumentsDelta(deltaCall.index, argsChunk))
                        }
                    }
                }
                emit(StreamEvent.Done(finishReason))
            }
        } catch (t: Throwable) {
            emit(StreamEvent.Error(t.message ?: "Unknown network error"))
        }
    }

    /** Exposes the fully-assembled pending tool calls after a stream completes (used by the ViewModel). */
    companion object {
        fun assembleToolCalls(pending: Map<Int, PendingToolCall>): List<ToolCall> =
            pending.entries.sortedBy { it.key }.map { (_, p) ->
                ToolCall(
                    id = p.id,
                    function = FunctionCall(name = p.name, arguments = p.argumentsBuilder.toString())
                )
            }
    }

    fun close() = http.close()
}
