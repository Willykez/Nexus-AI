package com.nexusai.agent.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.UUID

/* ------------------------------------------------------------------------------------------
 *  Chat message wire format (OpenAI ChatCompletions-compatible)
 * ------------------------------------------------------------------------------------------ */

@Serializable
data class ChatMessage(
    val role: String, // "system" | "user" | "assistant" | "tool"
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    val name: String? = null
)

@Serializable
data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: FunctionCall
)

@Serializable
data class FunctionCall(
    val name: String,
    /** Raw JSON-encoded arguments string, exactly as the OpenAI wire format specifies. */
    val arguments: String
)

/* ------------------------------------------------------------------------------------------
 *  Tool / function declarations sent to the model
 * ------------------------------------------------------------------------------------------ */

@Serializable
data class Tool(
    val type: String = "function",
    val function: FunctionDefinition
)

@Serializable
data class FunctionDefinition(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

/** Central registry of every native tool the agent is allowed to invoke on-device. */
object ToolRegistry {

    const val TOOL_READ_FILE = "read_file"
    const val TOOL_WRITE_FILE = "write_file"
    const val TOOL_LIST_FILES = "list_files"
    const val TOOL_ZIP_PROJECT = "zip_project"

    val all: List<Tool> = listOf(
        Tool(
            function = FunctionDefinition(
                name = TOOL_READ_FILE,
                description = "Read the full text content of a file inside the sandboxed workspace directory.",
                parameters = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("path") {
                            put("type", "string")
                            put("description", "Workspace-relative file path, e.g. app/src/Main.kt")
                        }
                    }
                    putJsonArray("required") { add("path") }
                }
            )
        ),
        Tool(
            function = FunctionDefinition(
                name = TOOL_WRITE_FILE,
                description = "Create or overwrite a file inside the sandboxed workspace directory with the given text content. Creates any missing parent directories automatically.",
                parameters = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("path") {
                            put("type", "string")
                            put("description", "Workspace-relative file path to write, e.g. app/src/Main.kt")
                        }
                        putJsonObject("content") {
                            put("type", "string")
                            put("description", "The full text content to write to the file.")
                        }
                    }
                    putJsonArray("required") {
                        add("path")
                        add("content")
                    }
                }
            )
        ),
        Tool(
            function = FunctionDefinition(
                name = TOOL_LIST_FILES,
                description = "List all files and directories currently inside the sandboxed workspace, recursively, as a tree.",
                parameters = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("path") {
                            put("type", "string")
                            put("description", "Optional workspace-relative subdirectory to list. Defaults to the workspace root.")
                        }
                    }
                }
            )
        ),
        Tool(
            function = FunctionDefinition(
                name = TOOL_ZIP_PROJECT,
                description = "Package every file currently in the workspace directory into a single downloadable .zip archive.",
                parameters = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("archiveName") {
                            put("type", "string")
                            put("description", "Optional file name for the resulting archive, without extension. Defaults to 'workspace'.")
                        }
                    }
                }
            )
        )
    )
}

/* ------------------------------------------------------------------------------------------
 *  Request payload
 * ------------------------------------------------------------------------------------------ */

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val tools: List<Tool>? = null,
    @SerialName("tool_choice") val toolChoice: String? = null,
    val stream: Boolean = true,
    val temperature: Double = 0.4
)

/* ------------------------------------------------------------------------------------------
 *  Streaming response chunk (Server-Sent Events "data: {...}" payloads)
 * ------------------------------------------------------------------------------------------ */

@Serializable
data class ChatCompletionChunk(
    val id: String? = null,
    val choices: List<ChunkChoice> = emptyList()
)

@Serializable
data class ChunkChoice(
    val index: Int = 0,
    val delta: ChunkDelta = ChunkDelta(),
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class ChunkDelta(
    val role: String? = null,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCallDeltaWire>? = null
)

@Serializable
data class ToolCallDeltaWire(
    val index: Int,
    val id: String? = null,
    val type: String? = null,
    val function: FunctionCallDeltaWire? = null
)

@Serializable
data class FunctionCallDeltaWire(
    val name: String? = null,
    val arguments: String? = null
)

/* ------------------------------------------------------------------------------------------
 *  Domain-level streaming events emitted by AiClient to the ViewModel
 * ------------------------------------------------------------------------------------------ */

sealed interface StreamEvent {
    data class ContentDelta(val text: String) : StreamEvent
    data class ToolCallStart(val index: Int, val id: String, val name: String) : StreamEvent
    data class ToolCallArgumentsDelta(val index: Int, val argumentsChunk: String) : StreamEvent
    data class Done(val finishReason: String?) : StreamEvent
    data class Error(val message: String) : StreamEvent
}

/** Mutable accumulator used while a tool call streams in piecemeal across many chunks. */
data class PendingToolCall(
    var id: String = "call_${UUID.randomUUID().toString().take(8)}",
    var name: String = "",
    val argumentsBuilder: StringBuilder = StringBuilder()
)

/* ------------------------------------------------------------------------------------------
 *  Provider configuration
 * ------------------------------------------------------------------------------------------ */

@Serializable
data class ProviderConfig(
    val baseUrl: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    val model: String = "gpt-4o-mini"
)

enum class ProviderPreset(val label: String, val baseUrl: String, val defaultModel: String) {
    OPENAI("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
    OLLAMA("Ollama (local)", "http://10.0.2.2:11434/v1", "llama3.1"),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
    GEMINI("Gemini (OpenAI shim)", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-1.5-pro")
}

/* ------------------------------------------------------------------------------------------
 *  Activity feed (right-hand Live Activity / File Inspector panel)
 * ------------------------------------------------------------------------------------------ */

enum class ActionStatus { RUNNING, DONE, ERROR }

data class ActivityEvent(
    val id: String = UUID.randomUUID().toString(),
    val toolName: String,
    val targetPath: String? = null,
    var status: ActionStatus = ActionStatus.RUNNING,
    var detail: String = "",
    var progress: Float = 0f,
    val timestampMillis: Long = System.currentTimeMillis()
)

/* ------------------------------------------------------------------------------------------
 *  Workspace file tree
 * ------------------------------------------------------------------------------------------ */

data class FileNode(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long = 0L,
    val children: List<FileNode> = emptyList(),
    val justModified: Boolean = false
)
