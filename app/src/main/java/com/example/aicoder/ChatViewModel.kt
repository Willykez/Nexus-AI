package com.example.aicoder

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val workspace = WorkspaceEngine(application)
    private val settings = SettingsManager(application)
    private val conversations = ConversationStore(application)
    private val parser = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val eventIds = AtomicLong(0)
    private var agentJob: Job? = null
    private var currentClient = AiClient("https://api.openai.com/v1", "")
    private var currentSettings = AiSettings("https://api.openai.com/v1", "", "gpt-4o-mini")
    private var currentSessionId = UUID.randomUUID().toString()
    private var wireHistory = mutableListOf<ChatMessage>()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _snackbarEvent = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarEvent: SharedFlow<String> = _snackbarEvent.asSharedFlow()

    private val readFileTool = functionTool("read_file", "Read a UTF-8 text file from the private workspace.",
        schema(mapOf("path" to "Relative workspace path of the file."), listOf("path")))
    private val writeFileTool = functionTool("write_file", "Create or replace a UTF-8 text file. Parent directories are created automatically.",
        schema(mapOf("path" to "Relative workspace path of the file.", "content" to "Complete UTF-8 file content."), listOf("path", "content")))
    private val listFilesTool = functionTool("list_files", "List files and directories recursively in the private workspace.",
        schema(mapOf("path" to "Optional relative directory path. Empty means workspace root."), emptyList()))
    private val deleteFileTool = functionTool("delete_file", "Delete a file or directory from the private workspace. Use only when the user clearly asked for deletion.",
        schema(mapOf("path" to "Relative workspace path to delete."), listOf("path")))
    private val renameFileTool = functionTool("rename_file", "Rename a workspace file or directory without moving it outside its parent directory.",
        schema(mapOf("path" to "Relative workspace path of the existing file or directory.", "new_name" to "New single filename or directory name."), listOf("path", "new_name")))
    private val zipProjectTool = functionTool("zip_project", "Package the complete private workspace as a ZIP archive.", schema(emptyMap(), emptyList()))

    /** Tools actually offered to the model, respecting the capability toggles in Provider settings. */
    private fun activeTools(state: UiState): List<Tool> = buildList {
        if (state.filesToolEnabled) {
            add(readFileTool)
            add(writeFileTool)
            add(renameFileTool)
            add(deleteFileTool)
        }
        add(listFilesTool)
        if (state.zipToolEnabled) add(zipProjectTool)
    }

    fun setFilesToolEnabled(enabled: Boolean) {
        _uiState.update { it.copy(filesToolEnabled = enabled) }
        viewModelScope.launch { settings.saveCapabilities(enabled, _uiState.value.zipToolEnabled) }
    }

    fun setZipToolEnabled(enabled: Boolean) {
        _uiState.update { it.copy(zipToolEnabled = enabled) }
        viewModelScope.launch { settings.saveCapabilities(_uiState.value.filesToolEnabled, enabled) }
    }

    init {
        _uiState.update { it.copy(workspacePath = workspace.workspacePath, sessionId = currentSessionId) }
        refreshWorkspace()
        viewModelScope.launch {
            conversations.sessions.collect { value ->
                _uiState.update { it.copy(sessions = value) }
            }
        }
        viewModelScope.launch {
            settings.settingsFlow.collect { value ->
                currentSettings = value
                currentClient = AiClient(value.baseUrl, value.apiKey)
                _uiState.update {
                    it.copy(
                        baseUrl = value.baseUrl,
                        modelName = value.model,
                        hasApiKey = value.apiKey.isNotBlank(),
                        providerName = providerLabel(value.baseUrl),
                        providerReady = value.apiKey.isNotBlank() || !requiresApiKey(value.baseUrl),
                        temperature = value.temperature,
                        maxOutputTokens = value.maxOutputTokens,
                        filesToolEnabled = value.filesToolEnabled,
                        zipToolEnabled = value.zipToolEnabled
                    )
                }
            }
        }
    }

    private fun persistCurrentSession(history: List<ChatMessage> = wireHistory) {
        val state = _uiState.value
        if (state.messages.isEmpty()) return
        val title = state.messages.firstOrNull { it.role == "user" }?.content?.replace("\\s+".toRegex(), " ")?.trim()?.take(72)
            ?.ifBlank { "New conversation" } ?: "New conversation"
        viewModelScope.launch {
            conversations.upsert(
                StoredSession(
                    id = currentSessionId,
                    title = title,
                    updatedAt = System.currentTimeMillis(),
                    providerName = state.providerName,
                    baseUrl = state.baseUrl,
                    modelName = state.modelName,
                    messages = state.messages,
                    rawHistory = history
                )
            )
        }
    }

    fun newConversation() {
        stopAgent()
        currentSessionId = UUID.randomUUID().toString()
        wireHistory = mutableListOf()
        _uiState.update { it.copy(messages = emptyList(), streamingText = "", streamingCode = "", activity = emptyList(), diffs = emptyList(), error = null, activeTask = "Idle", sessionId = currentSessionId) }
    }

    fun loadConversation(session: StoredSession) {
        stopAgent()
        currentSessionId = session.id
        val savedBaseUrl = session.baseUrl.ifBlank { currentSettings.baseUrl }
        val savedModel = session.modelName.ifBlank { currentSettings.model }
        wireHistory = if (session.rawHistory.isNotEmpty()) session.rawHistory.toMutableList() else session.messages.map { ChatMessage(it.role, it.content) }.toMutableList()
        val savedKey = if (savedBaseUrl.trimEnd('/') == currentSettings.baseUrl.trimEnd('/')) currentSettings.apiKey else ""
        currentSettings = currentSettings.copy(baseUrl = savedBaseUrl, model = savedModel, apiKey = savedKey)
        currentClient = AiClient(savedBaseUrl, savedKey)
        _uiState.update {
            it.copy(
                messages = session.messages,
                streamingText = "",
                streamingCode = "",
                activity = emptyList(),
                diffs = emptyList(),
                error = null,
                activeTask = "Idle",
                sessionId = session.id,
                currentTab = AppTab.CHAT,
                providerName = providerLabel(savedBaseUrl),
                baseUrl = savedBaseUrl,
                modelName = savedModel,
                hasApiKey = savedKey.isNotBlank(),
                providerReady = savedKey.isNotBlank() || !requiresApiKey(savedBaseUrl)
            )
        }
        viewModelScope.launch {
            settings.activateSession(savedBaseUrl, savedModel)
            val keyForSession = settings.getApiKeyForBaseUrl(savedBaseUrl)
            currentSettings = currentSettings.copy(baseUrl = savedBaseUrl, model = savedModel, apiKey = keyForSession)
            currentClient = AiClient(savedBaseUrl, keyForSession)
            _uiState.update { it.copy(hasApiKey = keyForSession.isNotBlank(), providerReady = keyForSession.isNotBlank() || !requiresApiKey(savedBaseUrl)) }
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            conversations.delete(id)
            if (id == currentSessionId) newConversation()
        }
    }

    fun selectTab(tab: AppTab) = _uiState.update { it.copy(currentTab = tab) }

    fun sendMessage(text: String) {
        val prompt = text.trim()
        if (prompt.isBlank() || agentJob?.isActive == true) return
        val state = _uiState.value
        if (requiresApiKey(state.baseUrl) && !state.hasApiKey) {
            _snackbarEvent.tryEmit("Add an API key in Provider settings before sending.")
            _uiState.update { it.copy(currentTab = AppTab.PROVIDER, error = "${it.providerName} needs an API key. Add it in Provider settings, then try again.") }
            return
        }

        val userMessage = ChatUiMessage("user", prompt, timeLabel())
        wireHistory += ChatMessage("user", prompt)
        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                isTyping = true,
                streamingText = "",
                streamingCode = "",
                error = null,
                activeTask = "Planning agent run…",
                activity = listOf(
                    ActivityItem(nextId(), ActivityKind.THINKING, "Agent routing", "Reading the request and planning tool work…")
                ) + it.activity.take(19)
            )
        }

        persistCurrentSession()
        agentJob = viewModelScope.launch {
            runAgent()
        }
    }

    fun stopAgent() {
        agentJob?.cancel()
        agentJob = null
        _uiState.update {
            it.copy(
                isTyping = false,
                isOrganizing = false,
                activeTask = "Stopped",
                activeTool = null,
                isOrganizing = false,
                organizeStreaming = "",
                activity = listOf(ActivityItem(nextId(), ActivityKind.SYSTEM, "Run stopped", "Agent execution was cancelled by the user.", success = null)) + it.activity.take(19)
            )
        }
    }

    fun clearChat() = newConversation()

    fun updateGenerationConfig(temperature: Float, maxTokens: Int) {
        val t = temperature.coerceIn(0f, 1f)
        val m = maxTokens.coerceIn(1024, 16384)
        _uiState.update { it.copy(temperature = t, maxOutputTokens = m) }
        viewModelScope.launch { settings.saveGenerationConfig(t, m) }
    }

    fun saveSettings(baseUrl: String, apiKey: String, model: String) {
        viewModelScope.launch {
            if (baseUrl.isBlank() || model.isBlank()) {
                _snackbarEvent.emit("Base URL and model are required")
                return@launch
            }
            settings.saveSettings(baseUrl, apiKey.takeIf { it.isNotBlank() }, model)
            _snackbarEvent.emit("Provider saved and activated")
        }
    }

    fun importProject(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(activeTask = "Importing project…", error = null) }
            try {
                workspace.importProject(uri) { progress ->
                    _uiState.update { state ->
                        state.copy(activity = listOf(ActivityItem(nextId(), ActivityKind.FILE, "Project import", progress, null, null)) + state.activity.take(19))
                    }
                }
                refreshWorkspaceNow()
                _uiState.update { it.copy(activeTask = "Idle") }
                _snackbarEvent.emit("Project imported into the private workspace")
            } catch (e: Exception) {
                _uiState.update { it.copy(activeTask = "Import failed", error = e.message) }
                _snackbarEvent.emit(e.message ?: "Could not import project")
            }
        }
    }

    fun refreshWorkspace() {
        viewModelScope.launch { refreshWorkspaceNow() }
    }

    private suspend fun refreshWorkspaceNow() {
        val tree = workspace.getFileTreeList()
        _uiState.update { it.copy(fileTreeNodes = tree, workspaceReady = true) }
    }

    fun openFile(path: String) {
        viewModelScope.launch {
            val content = workspace.readFile(path)
            _uiState.update { it.copy(selectedFile = path, selectedFileContent = content, currentTab = AppTab.WORKSPACE) }
        }
    }

    fun deleteFile(path: String) {
        viewModelScope.launch {
            _snackbarEvent.emit(workspace.deleteFile(path))
            refreshWorkspace()
        }
    }

    fun renameFile(path: String, newName: String) {
        viewModelScope.launch {
            _snackbarEvent.emit(workspace.renameFile(path, newName))
            refreshWorkspace()
        }
    }

    fun zipWorkspace() {
        if (agentJob?.isActive == true) return
        agentJob = viewModelScope.launch {
            _uiState.update { it.copy(activeTask = "Packaging workspace…") }
            try {
                val zip = workspace.zipProject { progress ->
                    _uiState.update {
                        it.copy(activity = listOf(ActivityItem(nextId(), ActivityKind.ZIP, "Zip exporter", progress)) + it.activity.take(19))
                    }
                }
                _uiState.update {
                    it.copy(
                        currentZipPath = zip.absolutePath,
                        activeTask = "Archive ready",
                        activity = listOf(ActivityItem(nextId(), ActivityKind.ZIP, "Archive generated", zip.name, 100, true)) + it.activity.take(19)
                    )
                }
                _snackbarEvent.emit("ZIP ready: ${zip.name}")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(activeTask = "Zip failed", error = e.message) }
                addActivity(ActivityKind.SYSTEM, "Zip failed", e.message ?: "Unknown error", success = false)
                _snackbarEvent.emit(e.message ?: "Failed to create ZIP archive")
            } finally {
                agentJob = null
            }
        }
    }

    private suspend fun runAgent() {
        val messages = mutableListOf<ChatMessage>()
        messages += ChatMessage("system", systemPrompt())
        messages += if (wireHistory.isNotEmpty()) wireHistory else _uiState.value.messages.map { ChatMessage(it.role, it.content) }

        var rounds = 0
        try {
            while (rounds++ < 8 && kotlinx.coroutines.currentCoroutineContext().isActive) {
                val activeState = _uiState.value
                currentClient = AiClient(activeState.baseUrl, currentSettings.apiKey.takeIf { activeState.baseUrl.trimEnd('/') == currentSettings.baseUrl.trimEnd('/') }.orEmpty())
                val request = ChatRequest(
                    model = _uiState.value.modelName,
                    messages = messages.toList(),
                    tools = activeTools(_uiState.value),
                    stream = true,
                    temperature = _uiState.value.temperature.toDouble(),
                    maxTokens = _uiState.value.maxOutputTokens
                )
                val text = StringBuilder()
                val calls = linkedMapOf<Int, MutableToolCall>()
                _uiState.update { it.copy(activeTask = "Streaming ${_uiState.value.modelName}…", activeTool = null, streamingText = "", streamingCode = "") }
                addActivity(ActivityKind.STREAM, "Live token stream", "Connected to ${providerLabel(_uiState.value.baseUrl)}")

                currentClient.streamChatCompletion(request)
                    .catch { throw it }
                    .collect { raw ->
                        val chunk = parser.decodeFromString<ChatResponseChunk>(raw)
                        val delta = chunk.choices.firstOrNull()?.delta ?: return@collect
                        delta.content?.let { token ->
                            text.append(token)
                            _uiState.update {
                                it.copy(
                                    streamingText = text.toString(),
                                    activeTask = "Writing response…",
                                    activity = listOf(ActivityItem(nextId(), ActivityKind.STREAM, "Streaming", "${text.length} characters received")) + it.activity.take(19)
                                )
                            }
                        }
                        delta.toolCalls.orEmpty().forEach { part ->
                            val holder = calls.getOrPut(part.index) { MutableToolCall() }
                            if (part.id != null) holder.id = part.id
                            part.function?.name?.let { fragment ->
                                if (holder.name.isBlank()) holder.name = fragment else if (holder.name != fragment) holder.name += fragment
                            }
                            part.function?.arguments?.let {
                                holder.arguments.append(it)
                                _uiState.update { state ->
                                    state.copy(
                                        activeTool = holder.name,
                                        activeTask = "Preparing ${holder.name.ifBlank { "tool" }}…",
                                        streamingCode = if (holder.name == "write_file") extractStreamingContent(holder.arguments.toString()) else state.streamingCode,
                                        activity = listOf(ActivityItem(nextId(), ActivityKind.FILE, "Tool call buffer", holder.name.ifBlank { "function" }, null, null)) + state.activity.take(19)
                                    )
                                }
                            }
                        }
                    }

                val completedCalls = calls.values.mapIndexedNotNull { index, c ->
                    if (c.name.isNotBlank() && c.id.isNotBlank()) ToolCall(c.id, "function", FunctionCall(c.name, c.arguments.toString())) else null
                }

                if (completedCalls.isEmpty()) {
                    val finalText = text.toString().trim()
                    if (finalText.isNotBlank()) {
                        messages += ChatMessage("assistant", finalText)
                        wireHistory = messages.filter { it.role != "system" }.toMutableList()
                        _uiState.update { state ->
                            state.copy(
                                messages = state.messages + ChatUiMessage("assistant", finalText, timeLabel()),
                                streamingText = "",
                                streamingCode = "",
                                isTyping = false,
                                activeTask = "Idle",
                                activeTool = null,
                                agentGenerationCount = state.agentGenerationCount + 1
                            )
                        }
                        persistCurrentSession()
                    } else {
                        _uiState.update { it.copy(isTyping = false, activeTask = "Idle", streamingText = "", streamingCode = "") }
                    }
                    return
                }

                messages += ChatMessage("assistant", text.toString().ifBlank { null }, completedCalls)
                wireHistory = messages.filter { it.role != "system" }.toMutableList()
                _uiState.update { it.copy(streamingText = "", activeTask = "Executing ${completedCalls.size} tool${if (completedCalls.size == 1) "" else "s"}…") }

                for (call in completedCalls) {
                    val result = executeTool(call)
                    messages += ChatMessage("tool", result, toolCallId = call.id, name = call.function.name)
                    wireHistory = messages.filter { it.role != "system" }.toMutableList()
                    // Reflect each completed filesystem operation immediately, not only after the whole batch.
                    refreshWorkspaceNow()
                    persistCurrentSession()
                }
            }
            _uiState.update { it.copy(isTyping = false, activeTask = "Agent limit reached", activeTool = null, streamingText = "", streamingCode = "") }
            addActivity(ActivityKind.SYSTEM, "Agent stopped", "Reached the 8-round safety limit. Narrow the request or continue with a follow-up.", success = false)
            _snackbarEvent.emit("Agent stopped after 8 rounds. Try a narrower request or continue in chat.")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.update { it.copy(isTyping = false, activeTask = "Run failed", error = e.message, streamingText = "", streamingCode = "") }
            addActivity(ActivityKind.SYSTEM, "Agent error", e.message ?: "Unknown error", success = false)
            _snackbarEvent.emit(e.message ?: "Agent request failed")
        } finally {
            agentJob = null
        }
    }

    private suspend fun executeTool(call: ToolCall): String {
        val name = call.function.name
        _uiState.update { it.copy(activeTool = name, activeTask = toolLabel(name)) }
        addActivity(ActivityKind.FILE, toolLabel(name), "Executing native workspace tool…")
        return try {
            val state = _uiState.value
            if ((name == "read_file" || name == "write_file" || name == "delete_file" || name == "rename_file") && !state.filesToolEnabled) {
                val message = "File system read/write is disabled in Provider settings."
                addActivity(ActivityKind.SYSTEM, "Tool blocked", message, success = false)
                _snackbarEvent.emit(message)
                return "Error: $message"
            }
            if (name == "zip_project" && !state.zipToolEnabled) {
                val message = "Automatic ZIP export is disabled in Provider settings."
                addActivity(ActivityKind.SYSTEM, "Tool blocked", message, success = false)
                _snackbarEvent.emit(message)
                return "Error: $message"
            }
            val args = JSONObject(call.function.arguments.ifBlank { "{}" })
            when (name) {
                "read_file" -> {
                    val path = args.optString("path")
                    val result = workspace.readFile(path)
                    val success = !result.startsWith("Error", ignoreCase = true)
                    if (success) {
                        _uiState.update { it.copy(streamingCode = result.take(12000), selectedFile = path, selectedFileContent = result) }
                        addActivity(ActivityKind.FILE, "Read file", path, success = true)
                    } else {
                        addActivity(ActivityKind.FILE, "Read failed", result.removePrefix("Error reading $path: ").trim(), success = false)
                        _snackbarEvent.emit(result)
                    }
                    result
                }
                "write_file" -> {
                    val path = args.optString("path")
                    val content = args.optString("content")
                    val existed = workspace.fileExists(path)
                    val oldContent = if (existed) workspace.readFile(path).takeUnless { it.startsWith("Error") }.orEmpty() else ""
                    _uiState.update { it.copy(streamingCode = content, selectedFile = path, selectedFileContent = content, activeTask = "Writing $path…") }
                    val result = workspace.writeFile(path, content) { progress ->
                        _uiState.update { state ->
                            state.copy(activity = listOf(ActivityItem(nextId(), ActivityKind.FILE, "Writing $path", "Writing file…", progress, null)) + state.activity.take(19))
                        }
                    }
                    val success = !result.startsWith("Error", ignoreCase = true)
                    if (success) {
                        val (added, removed) = lineDelta(oldContent, content)
                        _uiState.update { state ->
                            state.copy(
                                diffs = listOf(DiffItem(path, if (existed) "MOD" else "NEW", added, removed, content.take(700))) + state.diffs.filterNot { it.path == path },
                                selectedFile = path,
                                selectedFileContent = content,
                                touchedFile = path,
                                streamingCode = ""
                            )
                        }
                        addActivity(ActivityKind.FILE, if (existed) "Updated file" else "Created file", "$path • +$added / -$removed lines", 100, true)
                    } else {
                        addActivity(ActivityKind.FILE, "Write failed", result.removePrefix("Error writing $path: ").trim(), 100, false)
                        _snackbarEvent.emit(result)
                    }
                    result
                }
                "delete_file" -> {
                    val path = args.optString("path")
                    val result = workspace.deleteFile(path)
                    val success = result.startsWith("Deleted ")
                    if (success) {
                        _uiState.update { state ->
                            state.copy(diffs = listOf(DiffItem(path, "DEL", 0, 0, "File or directory deleted")) + state.diffs.filterNot { it.path == path }, touchedFile = path)
                        }
                        addActivity(ActivityKind.FILE, "Deleted file", path, 100, true)
                        refreshWorkspaceNow()
                    } else {
                        addActivity(ActivityKind.FILE, "Delete failed", result, 100, false)
                        _snackbarEvent.emit(result)
                    }
                    result
                }
                "rename_file" -> {
                    val path = args.optString("path")
                    val newName = args.optString("new_name")
                    val result = workspace.renameFile(path, newName)
                    val success = result.startsWith("Renamed ")
                    if (success) {
                        val parent = path.substringBeforeLast('/', "")
                        val newPath = if (parent.isBlank()) newName else "$parent/$newName"
                        _uiState.update { state ->
                            state.copy(diffs = listOf(DiffItem(newPath, "REN", 0, 0, "$path → $newPath")) + state.diffs.filterNot { it.path == path || it.path == newPath }, touchedFile = newPath, selectedFile = newPath)
                        }
                        addActivity(ActivityKind.FILE, "Renamed file", "$path → $newPath", 100, true)
                        refreshWorkspaceNow()
                    } else {
                        addActivity(ActivityKind.FILE, "Rename failed", result, 100, false)
                        _snackbarEvent.emit(result)
                    }
                    result
                }
                "list_files" -> {
                    val path = args.optString("path")
                    val result = workspace.listFiles(path)
                    val success = !result.startsWith("Error", ignoreCase = true)
                    addActivity(ActivityKind.FILE, if (success) "Inspected workspace" else "List failed", path.ifBlank { "root" }, success = success)
                    if (!success) _snackbarEvent.emit(result)
                    result
                }
                "zip_project" -> {
                    val zip = workspace.zipProject { progress ->
                        _uiState.update { state ->
                            state.copy(activity = listOf(ActivityItem(nextId(), ActivityKind.ZIP, "Zipping project", progress)) + state.activity.take(19))
                        }
                    }
                    _uiState.update { it.copy(currentZipPath = zip.absolutePath) }
                    addActivity(ActivityKind.ZIP, "Zip complete", zip.name, 100, true)
                    "Success: archive created at ${zip.absolutePath}"
                }
                else -> "Error: unsupported tool $name"
            }
        } catch (e: Exception) {
            addActivity(ActivityKind.SYSTEM, "Tool failed", "$name: ${e.message}", success = false)
            "Error executing $name: ${e.message}"
        }
    }


    fun setOrganizeInput(text: String) = _uiState.update { it.copy(organizeInput = text) }

    fun clearOrganize() = _uiState.update { it.copy(organizeInput = "", organizeStreaming = "", organizeProjectName = null, organizeFiles = emptyList()) }

    fun organizePastedProject() {
        val source = _uiState.value.organizeInput
        if (source.isBlank() || _uiState.value.isOrganizing || agentJob?.isActive == true) return
        if (requiresApiKey(_uiState.value.baseUrl) && !_uiState.value.hasApiKey) {
            _snackbarEvent.tryEmit("Add an API key in Provider settings before organizing a project.")
            return
        }
        agentJob = viewModelScope.launch {
            val state = _uiState.value
            _uiState.update { it.copy(isOrganizing = true, organizeStreaming = "", organizeProjectName = null, organizeFiles = emptyList(), error = null, activeTask = "Organizing pasted project…") }
            try {
                val request = ChatRequest(
                    model = active.modelName,
                    messages = listOf(ChatMessage("system", organizeSystemPrompt()), ChatMessage("user", source)),
                    tools = null,
                    stream = true,
                    temperature = state.temperature.toDouble(),
                    maxTokens = state.maxOutputTokens
                )
                val text = StringBuilder()
                addActivity(ActivityKind.THINKING, "Project organization", "Parsing the pasted source into a safe file tree…")
                currentClient.streamChatCompletion(request).collect { raw ->
                    val chunk = parser.decodeFromString<ChatResponseChunk>(raw)
                    chunk.choices.firstOrNull()?.delta?.content?.let {
                        text.append(it)
                        _uiState.update { s -> s.copy(organizeStreaming = text.toString(), activeTask = "Parsing project…") }
                    }
                }
                val cleaned = text.toString().trim()
                    .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                val parsed = parser.decodeFromString<OrganizeResponse>(cleaned)
                val safeFiles = parsed.files.mapNotNull { file ->
                    val path = file.path.trim().replace('\\', '/')
                    if (path.isBlank() || path.startsWith("/") || path.contains(Regex("^[A-Za-z]:")) || path.split('/').any { it.isBlank() || it == "." || it == ".." }) null
                    else ParsedFile(path, file.content)
                }.distinctBy { it.path }
                if (safeFiles.isEmpty()) error("The model did not find any safe files in the pasted project.")
                _uiState.update { it.copy(isOrganizing = false, organizeStreaming = "", organizeProjectName = sanitizeProjectName(parsed.projectName), organizeFiles = safeFiles, activeTask = "Review ready") }
                addActivity(ActivityKind.FILE, "Project parsed", "${safeFiles.size} files ready for review", 100, true)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                _uiState.update { it.copy(isOrganizing = false, organizeStreaming = "", activeTask = "Organization failed", error = e.message ?: "Could not organize project") }
                addActivity(ActivityKind.SYSTEM, "Organization failed", e.message ?: "Unknown error", success = false)
                _snackbarEvent.emit(e.message ?: "Could not organize project")
            } finally { agentJob = null }
        }
    }

    fun writeOrganizedProject() {
        val state = _uiState.value
        val rootName = state.organizeProjectName ?: return
        if (state.organizeFiles.isEmpty() || state.isOrganizing || agentJob?.isActive == true) return
        agentJob = viewModelScope.launch {
            try {
                _uiState.update { it.copy(isOrganizing = true, activeTask = "Writing organized project…") }
                state.organizeFiles.forEach { file ->
                    val target = "$rootName/${file.path}"
                    val existed = workspace.fileExists(target)
                    val oldContent = if (existed) workspace.readFile(target).takeUnless { it.startsWith("Error") }.orEmpty() else ""
                    val result = workspace.writeFile(target, file.content)
                    if (!result.startsWith("Success:")) error(result)
                    val (added, removed) = if (existed) lineDelta(oldContent, file.content) else file.content.lines().size.coerceAtLeast(1) to 0
                    _uiState.update { s -> s.copy(touchedFile = target, diffs = listOf(DiffItem(target, if (existed) "MOD" else "NEW", added, removed, file.content.take(700))) + s.diffs.filterNot { it.path == target }) }
                    refreshWorkspaceNow()
                    addActivity(ActivityKind.FILE, "Created file", target, 100, true)
                }
                _uiState.update { it.copy(isOrganizing = false, activeTask = "Idle", currentTab = AppTab.WORKSPACE) }
                _snackbarEvent.emit("Organized project written to $rootName")
            } catch (e: Exception) {
                _uiState.update { it.copy(isOrganizing = false, activeTask = "Write failed", error = e.message) }
                addActivity(ActivityKind.SYSTEM, "Organization write failed", e.message ?: "Unknown error", success = false)
                _snackbarEvent.emit(e.message ?: "Could not write organized project")
            } finally { agentJob = null }
        }
    }

    private fun organizeSystemPrompt() = """
        Convert the pasted source dump into a safe project file manifest. Return ONLY minified JSON: {"projectName":"safe-root","files":[{"path":"relative/file.ext","content":"exact source content"}]}
        Preserve source content exactly. Infer paths from explicit headers, directory trees, package/class names, and Gradle settings. Never invent missing files. Paths must be relative, forward-slash separated, contain no . or .. segments, no absolute paths, and no drive letters. The root folder must contain only letters, digits, dot, dash, underscore.
    """.trimIndent()

    private fun sanitizeProjectName(raw: String): String = raw.trim().replace(Regex("[^A-Za-z0-9._-]"), "_").trim('_', '.', '-').ifBlank { "project" }

    private fun extractStreamingContent(arguments: String): String {
        val match = Regex("\\"content\\"\\s*:\\s*\\"((?:\\\\.|[^\\"\\\\])*)").find(arguments) ?: return ""
        return runCatching { parser.decodeFromString<String>("\"${match.groupValues[1]}\"") }.getOrDefault("")
    }

    private class MutableToolCall {
        var id: String = ""
        var name: String = ""
        val arguments = StringBuilder()
    }
}

@kotlinx.serialization.Serializable
private data class OrganizeResponse(val projectName: String = "project", val files: List<ParsedFile>)

private fun functionTool(name: String, description: String, parameters: JsonObject): Tool =
    Tool(function = FunctionDescription(name, description, parameters))

private fun schema(properties: Map<String, String>, required: List<String>): JsonObject = buildJsonObject {
    put("type", "object")
    put("properties", buildJsonObject {
        properties.forEach { (name, description) ->
            put(name, buildJsonObject {
                put("type", "string")
                put("description", description)
            })
        }
    })
    put("required", buildJsonArray { required.forEach { add(JsonPrimitive(it)) } })
}


private fun toolLabel(name: String): String = when (name) {
    "write_file" -> "Writing file…"
    "read_file" -> "Reading file…"
    "rename_file" -> "Renaming item…"
    "delete_file" -> "Deleting item…"
    "list_files" -> "Inspecting workspace…"
    "zip_project" -> "Zipping project…"
    else -> "Running $name…"
}

private fun lineDelta(oldText: String, newText: String): Pair<Int, Int> {
    if (oldText == newText) return 0 to 0
    val oldLines = if (oldText.isEmpty()) emptyList() else oldText.lines()
    val newLines = if (newText.isEmpty()) emptyList() else newText.lines()
    var prefix = 0
    while (prefix < oldLines.size && prefix < newLines.size && oldLines[prefix] == newLines[prefix]) prefix++
    var suffix = 0
    while (suffix < oldLines.size - prefix && suffix < newLines.size - prefix &&
        oldLines[oldLines.lastIndex - suffix] == newLines[newLines.lastIndex - suffix]) suffix++
    return (newLines.size - prefix - suffix).coerceAtLeast(0) to
        (oldLines.size - prefix - suffix).coerceAtLeast(0)
}

private fun providerLabel(url: String): String = when {
    url.contains("deepseek", true) -> "DeepSeek"
    url.contains("generativelanguage", true) || url.contains("gemini", true) -> "Gemini"
    url.contains("ollama", true) || url.contains("11434") -> "Ollama"
    url.contains("dashscope", true) || url.contains("aliyuncs", true) -> "Qwen"
    url.contains("groq", true) -> "Groq"
    url.contains("mistral", true) -> "Mistral"
    url.contains("openai", true) -> "OpenAI"
    else -> "OpenAI Compatible"
}

private fun timeLabel(): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())

private fun requiresApiKey(baseUrl: String): Boolean {
    val url = baseUrl.lowercase()
    return !(url.contains("localhost") || url.contains("127.0.0.1") || url.contains("10.0.2.2"))
}
