package com.nexusai.agent.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexusai.agent.data.ActionStatus
import com.nexusai.agent.data.ActivityEvent
import com.nexusai.agent.data.AiClient
import com.nexusai.agent.data.ChatMessage
import com.nexusai.agent.data.FileNode
import com.nexusai.agent.data.PendingToolCall
import com.nexusai.agent.data.ProviderConfig
import com.nexusai.agent.data.SettingsStore
import com.nexusai.agent.data.StreamEvent
import com.nexusai.agent.data.ToolCall
import com.nexusai.agent.data.ToolRegistry
import com.nexusai.agent.data.WorkspaceEngine
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class Role { USER, ASSISTANT }

/** A single chunk of tool activity rendered inline inside a chat bubble. */
data class ToolChip(val toolName: String, val path: String?, val status: ActionStatus)

data class UiChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    var text: String = "",
    var isStreaming: Boolean = false,
    var toolChips: List<ToolChip> = emptyList(),
    var isError: Boolean = false
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsStore = SettingsStore(application)
    private val workspaceRoot = File(application.filesDir, "workspace")
    private val workspaceEngine = WorkspaceEngine(workspaceRoot)
    private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

    private var aiClient: AiClient

    private val _providerConfig = MutableStateFlow(settingsStore.load())
    val providerConfig: StateFlow<ProviderConfig> = _providerConfig.asStateFlow()

    private val _uiMessages = MutableStateFlow<List<UiChatMessage>>(emptyList())
    val uiMessages: StateFlow<List<UiChatMessage>> = _uiMessages.asStateFlow()

    private val _activityFeed = MutableStateFlow<List<ActivityEvent>>(emptyList())
    val activityFeed: StateFlow<List<ActivityEvent>> = _activityFeed.asStateFlow()

    private val _fileTree = MutableStateFlow(FileNode(name = "workspace", path = "", isDirectory = true))
    val fileTree: StateFlow<FileNode> = _fileTree.asStateFlow()

    private val _isAgentRunning = MutableStateFlow(false)
    val isAgentRunning: StateFlow<Boolean> = _isAgentRunning.asStateFlow()

    private val _statusLabel = MutableStateFlow("Idle")
    val statusLabel: StateFlow<String> = _statusLabel.asStateFlow()

    private val _lastExportedZip = MutableStateFlow<String?>(null)
    val lastExportedZip: StateFlow<String?> = _lastExportedZip.asStateFlow()

    /** Full OpenAI-wire conversation history sent with every request. */
    private val apiMessages = mutableListOf(
        ChatMessage(
            role = "system",
            content = "You are Nexus, an on-device AI coding agent embedded in an Android app. " +
                "You have native tools to read files, write files, list the workspace tree, and zip the " +
                "workspace into a downloadable archive. All paths are relative to the sandboxed workspace " +
                "root. Prefer calling tools to actually create working code rather than only describing it. " +
                "After writing files, briefly summarize what you built."
        )
    )

    init {
        aiClient = AiClient(_providerConfig.value)
        refreshFileTree()
    }

    fun updateProviderConfig(config: ProviderConfig) {
        _providerConfig.value = config
        settingsStore.save(config)
        aiClient.close()
        aiClient = AiClient(config)
    }

    fun sendMessage(userText: String) {
        if (userText.isBlank() || _isAgentRunning.value) return

        _uiMessages.update { it + UiChatMessage(role = Role.USER, text = userText) }
        apiMessages.add(ChatMessage(role = "user", content = userText))

        viewModelScope.launch {
            runAgentLoop()
        }
    }

    private suspend fun runAgentLoop() {
        _isAgentRunning.value = true
        var iterations = 0

        try {
            while (iterations < MAX_TOOL_ITERATIONS) {
                iterations++
                _statusLabel.value = "Thinking..."

                val assistantMessage = UiChatMessage(role = Role.ASSISTANT, isStreaming = true)
                _uiMessages.update { it + assistantMessage }

                val accumulatedText = StringBuilder()
                val pendingToolCalls = LinkedHashMap<Int, PendingToolCall>()
                val activeChips = LinkedHashMap<Int, ToolChip>()
                var finishReason: String? = null
                var streamError: String? = null

                aiClient.streamChatCompletion(apiMessages).collect { event ->
                    when (event) {
                        is StreamEvent.ContentDelta -> {
                            accumulatedText.append(event.text)
                            updateAssistantMessage(assistantMessage.id) { it.copy(text = accumulatedText.toString()) }
                        }

                        is StreamEvent.ToolCallStart -> {
                            _statusLabel.value = "Running ${event.name}..."
                            pendingToolCalls[event.index] = PendingToolCall(id = event.id, name = event.name)
                            activeChips[event.index] = ToolChip(event.name, null, ActionStatus.RUNNING)
                            updateAssistantMessage(assistantMessage.id) { it.copy(toolChips = activeChips.values.toList()) }
                            addActivityEvent(ActivityEvent(id = event.id, toolName = event.name, status = ActionStatus.RUNNING))
                        }

                        is StreamEvent.ToolCallArgumentsDelta -> {
                            val pending = pendingToolCalls[event.index] ?: return@collect
                            pending.argumentsBuilder.append(event.argumentsChunk)
                            val extractedPath = extractStringField(pending.argumentsBuilder.toString(), "path")
                                ?: extractStringField(pending.argumentsBuilder.toString(), "archiveName")
                            if (extractedPath != null) {
                                activeChips[event.index] = activeChips[event.index]?.copy(path = extractedPath)
                                    ?: ToolChip(pending.name, extractedPath, ActionStatus.RUNNING)
                                updateAssistantMessage(assistantMessage.id) { it.copy(toolChips = activeChips.values.toList()) }
                            }
                            updateActivityEvent(pending.id) {
                                it.copy(
                                    targetPath = extractedPath ?: it.targetPath,
                                    progress = (pending.argumentsBuilder.length / 300f).coerceIn(0.05f, 0.95f)
                                )
                            }
                        }

                        is StreamEvent.Done -> finishReason = event.finishReason
                        is StreamEvent.Error -> streamError = event.message
                    }
                }

                updateAssistantMessage(assistantMessage.id) { it.copy(isStreaming = false) }

                if (streamError != null) {
                    updateAssistantMessage(assistantMessage.id) {
                        it.copy(
                            text = it.text.ifBlank { "Connection error." } + "\n\n⚠ ${streamError}",
                            isError = true
                        )
                    }
                    _statusLabel.value = "Error"
                    break
                }

                val toolCalls = AiClient.assembleToolCalls(pendingToolCalls)
                apiMessages.add(
                    ChatMessage(
                        role = "assistant",
                        content = accumulatedText.toString().ifBlank { null },
                        toolCalls = toolCalls.ifEmpty { null }
                    )
                )

                if (toolCalls.isEmpty()) {
                    _statusLabel.value = "Idle"
                    break
                }

                for (call in toolCalls) {
                    val resultText = executeTool(call)
                    apiMessages.add(
                        ChatMessage(role = "tool", content = resultText, toolCallId = call.id, name = call.function.name)
                    )
                }
                refreshFileTree()
            }
        } finally {
            _isAgentRunning.value = false
            if (_statusLabel.value != "Error") _statusLabel.value = "Idle"
        }
    }

    private suspend fun executeTool(call: ToolCall): String {
        val argsJson: JsonElement? = runCatching {
            lenientJson.parseToJsonElement(call.function.arguments.ifBlank { "{}" })
        }.getOrNull()
        val args = argsJson?.jsonObject

        return try {
            val result = when (call.function.name) {
                ToolRegistry.TOOL_READ_FILE -> {
                    val path = args?.get("path")?.jsonPrimitive?.content.orEmpty()
                    workspaceEngine.readFile(path)
                }

                ToolRegistry.TOOL_WRITE_FILE -> {
                    val path = args?.get("path")?.jsonPrimitive?.content.orEmpty()
                    val content = args?.get("content")?.jsonPrimitive?.content.orEmpty()
                    workspaceEngine.writeFile(path, content)
                }

                ToolRegistry.TOOL_LIST_FILES -> {
                    val path = args?.get("path")?.jsonPrimitive?.content.orEmpty()
                    renderTreeAsText(workspaceEngine.listFiles(path))
                }

                ToolRegistry.TOOL_ZIP_PROJECT -> {
                    val name = args?.get("archiveName")?.jsonPrimitive?.content?.ifBlank { null } ?: "workspace"
                    val zipFile = workspaceEngine.zipProject(name)
                    _lastExportedZip.value = zipFile.absolutePath
                    "Archive created at ${zipFile.absolutePath} (${zipFile.length()} bytes)"
                }

                else -> "Unknown tool: ${call.function.name}"
            }
            markToolDone(call, success = true)
            result
        } catch (t: Throwable) {
            markToolDone(call, success = false)
            "Error executing ${call.function.name}: ${t.message}"
        }
    }

    private fun markToolDone(call: ToolCall, success: Boolean) {
        updateActivityEvent(call.id) {
            it.copy(
                status = if (success) ActionStatus.DONE else ActionStatus.ERROR,
                progress = 1f
            )
        }
        _uiMessages.update { messages ->
            messages.map { msg ->
                if (msg.toolChips.any { chip -> chip.toolName == call.function.name }) {
                    msg.copy(
                        toolChips = msg.toolChips.map { chip ->
                            if (chip.toolName == call.function.name && chip.status == ActionStatus.RUNNING) {
                                chip.copy(status = if (success) ActionStatus.DONE else ActionStatus.ERROR)
                            } else chip
                        }
                    )
                } else msg
            }
        }
    }

    private fun extractStringField(partialJson: String, field: String): String? {
        val regex = Regex("\"$field\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)")
        return regex.find(partialJson)?.groupValues?.get(1)?.replace("\\\"", "\"")
    }

    private fun refreshFileTree() {
        viewModelScope.launch {
            _fileTree.value = workspaceEngine.listFiles()
        }
    }

    private fun renderTreeAsText(node: FileNode, depth: Int = 0): String {
        val indent = "  ".repeat(depth)
        val line = if (depth == 0) "/" else "$indent${node.name}${if (node.isDirectory) "/" else ""}"
        val childrenText = node.children.joinToString("\n") { renderTreeAsText(it, depth + 1) }
        return if (childrenText.isBlank()) line else "$line\n$childrenText"
    }

    private inline fun updateAssistantMessage(id: String, transform: (UiChatMessage) -> UiChatMessage) {
        _uiMessages.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    private fun addActivityEvent(event: ActivityEvent) {
        _activityFeed.update { it + event }
    }

    private inline fun updateActivityEvent(id: String, transform: (ActivityEvent) -> ActivityEvent) {
        _activityFeed.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    fun workspacePath(): String = workspaceEngine.workspaceRootPath()

    override fun onCleared() {
        super.onCleared()
        aiClient.close()
    }

    companion object {
        private const val MAX_TOOL_ITERATIONS = 8
    }
}
