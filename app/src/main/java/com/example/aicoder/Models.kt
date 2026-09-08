package com.example.aicoder

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

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
data class FunctionCall(
    val name: String,
    val arguments: String
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val tools: List<Tool>? = null,
    @SerialName("tool_choice") val toolChoice: String = "auto",
    val stream: Boolean = true,
    val temperature: Double = 0.2,
    @SerialName("max_tokens") val maxTokens: Int = 8192
)

@Serializable
data class Tool(
    val type: String = "function",
    val function: FunctionDescription
)

@Serializable
data class FunctionDescription(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

@Serializable
data class ChatResponseChunk(
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
data class ChunkFunctionCall(
    val name: String? = null,
    val arguments: String? = null
)


@Serializable
data class ParsedFile(
    val path: String,
    val content: String
)

@Serializable
data class ChatUiMessage(
    val role: String,
    val content: String,
    val timeLabel: String = "now",
    val toolName: String? = null
)

@Serializable
data class StoredSession(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val providerName: String,
    val modelName: String,
    val messages: List<ChatUiMessage>,
    val baseUrl: String = "https://api.openai.com/v1",
    val rawHistory: List<ChatMessage> = emptyList()
)

data class FileNode(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val depth: Int,
    val sizeBytes: Long = 0L
)

data class ActivityItem(
    val id: Long,
    val kind: ActivityKind,
    val title: String,
    val detail: String,
    val progress: Int? = null,
    val success: Boolean? = null
)

enum class ActivityKind {
    THINKING,
    FILE,
    TERMINAL,
    ZIP,
    STREAM,
    SYSTEM
}

data class DiffItem(
    val path: String,
    val status: String,
    val addedLines: Int,
    val removedLines: Int,
    val preview: String
)

enum class AppTab(val label: String) {
    CHAT("Chat & Stream"),
    DIFF("Code Diff"),
    WORKSPACE("Workspace"),
    PROVIDER("Provider")
}

data class UiState(
    val messages: List<ChatUiMessage> = emptyList(),
    val isTyping: Boolean = false,
    val streamingText: String = "",
    val streamingCode: String = "",
    val activeTask: String = "Idle",
    val activeTool: String? = null,
    val fileTreeNodes: List<FileNode> = emptyList(),
    val activity: List<ActivityItem> = emptyList(),
    val diffs: List<DiffItem> = emptyList(),
    val selectedFile: String? = null,
    val selectedFileContent: String = "",
    val currentZipPath: String? = null,
    val currentTab: AppTab = AppTab.CHAT,
    val providerName: String = "OpenAI Compatible",
    val baseUrl: String = "https://api.openai.com/v1",
    val modelName: String = "gpt-4o-mini",
    val hasApiKey: Boolean = false,
    val temperature: Float = 0.2f,
    val maxOutputTokens: Int = 8192,
    val agentGenerationCount: Int = 0,
    val workspacePath: String = "",
    val filesToolEnabled: Boolean = true,
    val zipToolEnabled: Boolean = true,
    val error: String? = null,
    val sessions: List<StoredSession> = emptyList(),
    val sessionId: String = "",
    val workspaceReady: Boolean = false,
    val touchedFile: String? = null,
    val providerReady: Boolean = false,
    val organizeInput: String = "",
    val organizeStreaming: String = "",
    val organizeProjectName: String? = null,
    val organizeFiles: List<ParsedFile> = emptyList(),
    val isOrganizing: Boolean = false
)
