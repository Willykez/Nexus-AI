package com.nexusforge.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.util.UUID

// ---------- OpenAI-compatible wire format ----------

@Serializable
data class ChatMessage(
    val role: String,
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
data class FunctionCall(val name: String, val arguments: String)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val tools: List<Tool>? = null,
    @SerialName("tool_choice") val toolChoice: String? = if (tools.isNullOrEmpty()) null else "auto",
    val stream: Boolean = true,
    val temperature: Double = 0.2,
    @SerialName("max_tokens") val maxTokens: Int = 8192
)

@Serializable
data class Tool(val type: String = "function", val function: FunctionDescription)

@Serializable
data class FunctionDescription(val name: String, val description: String, val parameters: JsonObject)

@Serializable
data class ChatResponseChunk(val id: String? = null, val choices: List<ChunkChoice> = emptyList())

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
    @SerialName("tool_calls") val toolCalls: List<ChunkToolCall>? = null
)

@Serializable
data class ChunkToolCall(
    val index: Int = 0,
    val id: String? = null,
    val type: String? = null,
    val function: ChunkFunctionCall? = null
)

@Serializable
data class ChunkFunctionCall(val name: String? = null, val arguments: String? = null)

// ---------- Workspace-facing models ----------

data class FileNode(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val depth: Int,
    val sizeBytes: Long = 0L,
    val children: List<FileNode> = emptyList()
)

data class WorkspaceStats(
    val fileCount: Int,
    val directoryCount: Int,
    val bytes: Long,
    val label: String
)

// ---------- UI / agent-run models ----------

enum class ToolStatus { RUNNING, DONE, ERROR }

/** A chip of tool activity rendered inline in a chat bubble, e.g. "write_file Task.kt ✓". */
data class ToolChip(
    val toolName: String,
    val path: String? = null,
    val status: ToolStatus = ToolStatus.RUNNING,
    val detail: String? = null
)

enum class ActivityKind { THINKING, FILE, ZIP, STREAM, SYSTEM }

data class ActivityItem(
    val id: Long,
    val kind: ActivityKind,
    val title: String,
    val detail: String,
    val progress: Int? = null,
    val success: Boolean? = null
)

data class DiffItem(val path: String, val status: String, val lines: Int, val preview: String)

data class UiChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String, // "user" | "assistant"
    var text: String = "",
    var isStreaming: Boolean = false,
    var toolChips: List<ToolChip> = emptyList(),
    var isError: Boolean = false
)

enum class AppTab(val label: String) {
    CHAT("Chat"),
    WORKSPACE("Workspace"),
    ORGANIZER("Organize"),
    HISTORY("History"),
    SETTINGS("Settings")
}

/** Which underlying file system the active project talks to. Chosen in Settings. */
sealed class ProjectSource {
    /** A private, disposable sandbox at filesDir/workspace. Safe for the model to run wild in. */
    data object Sandbox : ProjectSource()

    /** A real, user-picked folder (via SAF) that the agent edits in place. */
    data class AttachedFolder(val treeUri: String, val displayName: String) : ProjectSource()
}

data class ProviderConfig(
    val baseUrl: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    val model: String = "gpt-4o-mini"
)

data class CapabilityFlags(
    val fileReadWriteEnabled: Boolean = true,
    val zipEnabled: Boolean = true
)
