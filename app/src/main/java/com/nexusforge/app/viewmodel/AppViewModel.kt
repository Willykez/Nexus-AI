package com.nexusforge.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexusforge.app.data.AiClient
import com.nexusforge.app.data.AppSettings
import com.nexusforge.app.data.AppTab
import com.nexusforge.app.data.CapabilityFlags
import com.nexusforge.app.data.ChatMessage
import com.nexusforge.app.data.ChatRequest
import com.nexusforge.app.data.ChatResponseChunk
import com.nexusforge.app.data.FileNode
import com.nexusforge.app.data.FunctionCall
import com.nexusforge.app.data.Project
import com.nexusforge.app.data.ProjectSource
import com.nexusforge.app.data.ProjectStore
import com.nexusforge.app.data.SettingsStore
import com.nexusforge.app.data.ThemeMode
import com.nexusforge.app.data.ToolCall
import com.nexusforge.app.data.ToolChip
import com.nexusforge.app.data.ToolRegistry
import com.nexusforge.app.data.ToolStatus
import com.nexusforge.app.data.UiChatMessage
import com.nexusforge.app.data.WorkspaceStats
import com.nexusforge.app.data.history.ChatHistoryStore
import com.nexusforge.app.data.history.ChatSession
import com.nexusforge.app.data.history.ChatSessionSummary
import com.nexusforge.app.data.history.PersistedMessage
import com.nexusforge.app.data.organizer.LogLine
import com.nexusforge.app.data.organizer.ProjectDumpEngine
import com.nexusforge.app.data.workspace.RealFolderWorkspaceEngine
import com.nexusforge.app.data.workspace.SandboxWorkspaceEngine
import com.nexusforge.app.data.workspace.WorkspaceEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.File
import java.util.UUID

private const val MAX_AGENT_ROUNDS = 8

data class AppUiState(
    val settingsLoaded: Boolean = false,
    val settings: AppSettings? = null,
    val currentTab: AppTab = AppTab.CHAT,

    // project
    val activeProject: Project? = null,
    val projects: List<Project> = emptyList(),
    val showProjectPicker: Boolean = false,

    // chat
    val sessionId: String = UUID.randomUUID().toString(),
    val messages: List<UiChatMessage> = emptyList(),
    val isAgentRunning: Boolean = false,
    val statusLabel: String = "Idle",
    val error: String? = null,

    // workspace
    val fileTree: FileNode = FileNode("", "project", true, 0),
    val highlightedPath: String? = null,
    val selectedFile: String? = null,
    val selectedFileContent: String = "",
    val workspaceStats: WorkspaceStats? = null,
    val lastExportedZip: String? = null,

    // history
    val sessionSummaries: List<ChatSessionSummary> = emptyList(),

    // organizer
    val organizerLog: List<LogLine> = emptyList(),
    val organizerRunning: Boolean = false,
    val organizerPhase: OrganizerPhase = OrganizerPhase.IDLE,
    val organizerThinkingChars: Int = 0,
    val organizerResultSummary: String? = null
)

enum class OrganizerPhase { IDLE, THINKING, WRITING, DONE, FAILED }

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsStore = SettingsStore(application)
    private val historyStore = ChatHistoryStore(application)
    private val projectStore = ProjectStore(application)
    private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private val _snackbar = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbar: SharedFlow<String> = _snackbar.asSharedFlow()

    private var workspaceEngine: WorkspaceEngine? = null
    private var apiMessages = mutableListOf<ChatMessage>()
    private var agentJob: Job? = null

    init {
        viewModelScope.launch {
            settingsStore.settingsFlow.collect { settings ->
                _state.update { it.copy(settingsLoaded = true, settings = settings) }
            }
        }
        viewModelScope.launch { initializeActiveProject() }
        refreshSessions()
    }

    private suspend fun initializeActiveProject() {
        val projects = projectStore.list()
        _state.update { it.copy(projects = projects) }
        if (projects.isEmpty()) {
            val created = projectStore.createSandbox("My Sandbox")
            selectProject(created)
            return
        }
        // Wait one tick for settingsFlow's first emission so lastActiveProjectId is available.
        val lastId = _state.value.settings?.lastActiveProjectId
        val target = projects.find { it.id == lastId } ?: projects.first()
        selectProject(target, persist = false)
    }

    private fun engineFor(source: ProjectSource): WorkspaceEngine = when (source) {
        is ProjectSource.Sandbox -> SandboxWorkspaceEngine(getApplication(), source.projectId)
        is ProjectSource.AttachedFolder -> RealFolderWorkspaceEngine(getApplication(), Uri.parse(source.treeUri), source.displayName)
    }

    // ---------- Navigation ----------

    fun selectTab(tab: AppTab) = _state.update { it.copy(currentTab = tab) }

    // ---------- Projects ----------

    fun openProjectPicker() {
        viewModelScope.launch { _state.update { it.copy(projects = projectStore.list(), showProjectPicker = true) } }
    }

    fun dismissProjectPicker() = _state.update { it.copy(showProjectPicker = false) }

    fun selectProject(project: Project, persist: Boolean = true) {
        workspaceEngine = engineFor(project.source)
        _state.update {
            it.copy(
                activeProject = project, showProjectPicker = false,
                selectedFile = null, selectedFileContent = "", highlightedPath = null
            )
        }
        refreshWorkspace()
        if (persist) {
            viewModelScope.launch {
                settingsStore.saveLastActiveProject(project.id)
                projectStore.touch(project.id)
                _state.update { it.copy(projects = projectStore.list()) }
            }
        }
    }

    fun createSandboxProject(name: String) {
        viewModelScope.launch {
            val project = projectStore.createSandbox(name)
            selectProject(project)
        }
    }

    fun attachFolderAsProject(treeUri: Uri, displayName: String) {
        val resolver = getApplication<Application>().contentResolver
        runCatching {
            resolver.takePersistableUriPermission(
                treeUri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        viewModelScope.launch {
            val project = projectStore.attachFolder(treeUri.toString(), displayName)
            selectProject(project)
            _snackbar.emit("Attached folder: $displayName")
        }
    }

    fun renameProject(project: Project, newName: String) {
        viewModelScope.launch {
            projectStore.rename(project.id, newName)
            val projects = projectStore.list()
            _state.update { it.copy(projects = projects) }
            if (_state.value.activeProject?.id == project.id) {
                _state.update { it.copy(activeProject = projects.find { p -> p.id == project.id }) }
            }
        }
    }

    fun deleteProject(project: Project) {
        viewModelScope.launch {
            projectStore.delete(project.id)
            val projects = projectStore.list()
            _state.update { it.copy(projects = projects) }
            if (_state.value.activeProject?.id == project.id) {
                val replacement = projects.firstOrNull() ?: projectStore.createSandbox("My Sandbox")
                selectProject(replacement)
            }
            _snackbar.emit("Deleted \"${project.name}\"")
        }
    }

    // ---------- Chat ----------

    fun sendMessage(rawText: String) {
        val prompt = rawText.trim()
        if (prompt.isBlank() || _state.value.isAgentRunning) return
        val engine = workspaceEngine
        val project = _state.value.activeProject
        if (engine == null || project == null) {
            viewModelScope.launch { _snackbar.emit("Pick a project first.") }
            openProjectPicker()
            return
        }

        if (apiMessages.isEmpty()) {
            val settings = _state.value.settings
            apiMessages.add(ChatMessage(role = "system", content = ToolRegistry.systemPrompt(
                settings?.capabilities ?: CapabilityFlags(), project.source
            )))
        }
        apiMessages.add(ChatMessage(role = "user", content = prompt))
        _state.update { it.copy(messages = it.messages + UiChatMessage(role = "user", text = prompt), error = null) }

        agentJob = viewModelScope.launch { runAgentLoop(engine) }
    }

    fun stopAgent() {
        agentJob?.cancel()
        agentJob = null
        _state.update { it.copy(isAgentRunning = false, statusLabel = "Stopped") }
    }

    fun newChat() {
        persistCurrentSessionIfNeeded()
        apiMessages = mutableListOf()
        _state.update {
            it.copy(sessionId = UUID.randomUUID().toString(), messages = emptyList(), statusLabel = "Idle", error = null)
        }
    }

    fun loadSession(id: String) {
        viewModelScope.launch {
            val session = historyStore.loadSession(id) ?: run {
                _snackbar.emit("That conversation is no longer available.")
                return@launch
            }
            apiMessages = session.apiMessages.toMutableList()
            val restored = session.renderedMessages.map { pm ->
                UiChatMessage(
                    role = pm.role, text = pm.text, isError = pm.isError,
                    toolChips = pm.toolSummaries.map { summary -> ToolChip(toolName = summary, status = ToolStatus.DONE) }
                )
            }
            val project = session.projectId?.let { projectStore.get(it) }
            if (project != null && project.id != _state.value.activeProject?.id) {
                selectProject(project, persist = false)
            } else if (session.projectId != null && project == null) {
                _snackbar.emit("This conversation's original project is gone — staying on the current one.")
            }
            _state.update {
                it.copy(sessionId = session.id, messages = restored, currentTab = AppTab.CHAT)
            }
        }
    }

    fun deleteSession(id: String) {
        viewModelScope.launch {
            historyStore.deleteSession(id)
            refreshSessions()
            if (_state.value.sessionId == id) newChat()
        }
    }

    private fun refreshSessions() {
        viewModelScope.launch {
            _state.update { it.copy(sessionSummaries = historyStore.listSessions()) }
        }
    }

    private fun persistCurrentSessionIfNeeded() {
        val current = _state.value
        val firstUser = current.messages.firstOrNull { it.role == "user" }?.text
        if (firstUser == null) return // nothing to save
        val settings = current.settings
        val project = current.activeProject
        viewModelScope.launch {
            historyStore.saveSession(
                ChatSession(
                    id = current.sessionId,
                    title = ChatHistoryStore.titleFrom(firstUser),
                    providerLabel = settings?.let { SettingsStore.providerLabel(it.provider.baseUrl) } ?: "—",
                    projectLabel = project?.name ?: "Unknown project",
                    projectId = project?.id,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    apiMessages = apiMessages.toList(),
                    renderedMessages = current.messages.map { m ->
                        PersistedMessage(
                            role = m.role, text = m.text, isError = m.isError,
                            toolSummaries = m.toolChips.map { chip -> "${chip.toolName}${chip.path?.let { " $it" } ?: ""}" }
                        )
                    }
                )
            )
            refreshSessions()
        }
    }

    private suspend fun runAgentLoop(engine: WorkspaceEngine) {
        val settings = _state.value.settings ?: return
        val client = AiClient(settings.provider)
        _state.update { it.copy(isAgentRunning = true, statusLabel = "Thinking…") }

        try {
            var round = 0
            while (round < MAX_AGENT_ROUNDS) {
                round++
                val assistant = UiChatMessage(role = "assistant", isStreaming = true)
                _state.update { it.copy(messages = it.messages + assistant) }

                val text = StringBuilder()
                val calls = linkedMapOf<Int, MutablePendingCall>()
                var streamError: String? = null

                val request = ChatRequest(
                    model = settings.provider.model,
                    messages = apiMessages.toList(),
                    tools = ToolRegistry.activeTools(settings.capabilities),
                    stream = true,
                    temperature = settings.temperature.toDouble(),
                    maxTokens = settings.maxOutputTokens
                )

                try {
                    client.streamChatCompletion(request).collect { raw ->
                        val chunk = lenientJson.decodeFromString(ChatResponseChunk.serializer(), raw)
                        val delta = chunk.choices.firstOrNull()?.delta ?: return@collect
                        delta.content?.let { token ->
                            text.append(token)
                            updateMessage(assistant.id) { it.copy(text = text.toString()) }
                        }
                        delta.toolCalls?.forEach { part ->
                            val holder = calls.getOrPut(part.index) { MutablePendingCall() }
                            part.id?.let { holder.id = it }
                            part.function?.name?.let { holder.name = it }
                            part.function?.arguments?.let { holder.arguments.append(it) }
                            val path = extractField(holder.arguments.toString(), "path")
                                ?: extractField(holder.arguments.toString(), "archiveName")
                            val chip = ToolChip(holder.name, path, ToolStatus.RUNNING)
                            updateMessage(assistant.id) { msg ->
                                val chips = msg.toolChips.toMutableList()
                                val idx = chips.indexOfFirst { it.toolName == holder.name && it.status == ToolStatus.RUNNING }
                                if (idx >= 0) chips[idx] = chip else chips.add(chip)
                                msg.copy(toolChips = chips)
                            }
                            _state.update { it.copy(statusLabel = "Running ${holder.name}…") }
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e // stopAgent() cancelling this job must actually stop it, not surface as a stream error
                } catch (e: Exception) {
                    streamError = e.message ?: "Connection error."
                }

                updateMessage(assistant.id) { it.copy(isStreaming = false) }

                if (streamError != null) {
                    updateMessage(assistant.id) { it.copy(text = it.text.ifBlank { "Connection error." } + "\n\n⚠ $streamError", isError = true) }
                    _state.update { it.copy(statusLabel = "Error", error = streamError) }
                    _snackbar.emit(streamError)
                    break
                }

                val toolCalls = calls.values.mapNotNull { c ->
                    if (c.name.isNotBlank() && c.id.isNotBlank()) ToolCall(c.id, "function", FunctionCall(c.name, c.arguments.toString())) else null
                }

                apiMessages.add(ChatMessage(role = "assistant", content = text.toString().ifBlank { null }, toolCalls = toolCalls.ifEmpty { null }))

                if (toolCalls.isEmpty()) {
                    _state.update { it.copy(statusLabel = "Idle") }
                    break
                }

                for (call in toolCalls) {
                    val result = executeTool(engine, call, settings.capabilities)
                    apiMessages.add(ChatMessage(role = "tool", content = result, toolCallId = call.id, name = call.function.name))
                    updateMessage(assistant.id) { msg ->
                        msg.copy(toolChips = msg.toolChips.map { chip ->
                            if (chip.toolName == call.function.name && chip.status == ToolStatus.RUNNING)
                                chip.copy(status = if (result.startsWith("Error")) ToolStatus.ERROR else ToolStatus.DONE)
                            else chip
                        })
                    }
                }
                refreshWorkspace()

                if (round == MAX_AGENT_ROUNDS) {
                    updateMessage(assistant.id) { it.copy(text = it.text.ifBlank { "" } + "\n\n_I've hit my step limit for this turn and stopped rather than keep going — tell me what's still missing and I'll continue._") }
                    _state.update { it.copy(statusLabel = "Stopped — step limit reached") }
                }
            }
        } finally {
            client.close()
            _state.update { it.copy(isAgentRunning = false) }
            if (_state.value.statusLabel.startsWith("Running") || _state.value.statusLabel == "Thinking…") {
                _state.update { it.copy(statusLabel = "Idle") }
            }
            agentJob = null
            persistCurrentSessionIfNeeded()
        }
    }

    private suspend fun executeTool(engine: WorkspaceEngine, call: ToolCall, flags: CapabilityFlags): String {
        val args = runCatching { JSONObject(call.function.arguments.ifBlank { "{}" }) }.getOrNull()
        return try {
            when (call.function.name) {
                ToolRegistry.READ_FILE -> {
                    if (!flags.fileReadWriteEnabled) return "Error: file access is disabled in Settings capability toggles"
                    engine.readFile(args?.optString("path").orEmpty())
                }
                ToolRegistry.WRITE_FILE -> {
                    if (!flags.fileReadWriteEnabled) return "Error: file access is disabled in Settings capability toggles"
                    val path = args?.optString("path").orEmpty()
                    val content = args?.optString("content").orEmpty()
                    val result = engine.writeFile(path, content)
                    _state.update { it.copy(highlightedPath = path) }
                    result
                }
                ToolRegistry.LIST_FILES -> engine.listFilesAsText(args?.optString("path").orEmpty())
                ToolRegistry.ZIP_PROJECT -> {
                    if (!flags.zipEnabled) return "Error: ZIP export is disabled in Settings capability toggles"
                    val name = args?.optString("archiveName")?.ifBlank { null } ?: "workspace"
                    val zip = engine.zipProject(name)
                    _state.update { it.copy(lastExportedZip = zip.absolutePath) }
                    "Archive created at ${zip.name} (${zip.length()} bytes)"
                }
                else -> "Error: unsupported tool ${call.function.name}"
            }
        } catch (e: Exception) {
            "Error executing ${call.function.name}: ${e.message}"
        }
    }

    private fun updateMessage(id: String, transform: (UiChatMessage) -> UiChatMessage) {
        _state.update { state -> state.copy(messages = state.messages.map { if (it.id == id) transform(it) else it }) }
    }

    private fun extractField(partialJson: String, field: String): String? {
        val regex = Regex("\"$field\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)")
        return regex.find(partialJson)?.groupValues?.get(1)?.replace("\\\"", "\"")
    }

    private class MutablePendingCall {
        var id: String = ""
        var name: String = ""
        val arguments = StringBuilder()
    }

    // ---------- Workspace ----------

    fun refreshWorkspace() {
        val engine = workspaceEngine ?: return
        viewModelScope.launch {
            val tree = runCatching { engine.fileTree() }.getOrNull()
            val stats = runCatching { engine.stats() }.getOrNull()
            if (tree != null) _state.update { it.copy(fileTree = tree, workspaceStats = stats) }
        }
    }

    fun openFile(path: String) {
        val engine = workspaceEngine ?: return
        viewModelScope.launch {
            val content = runCatching { engine.readFile(path) }.getOrElse { "Error reading file: ${it.message}" }
            _state.update { it.copy(selectedFile = path, selectedFileContent = content) }
        }
    }

    fun closeFilePreview() {
        _state.update { it.copy(selectedFile = null, selectedFileContent = "") }
    }

    fun renameFile(path: String, newName: String) {
        val engine = workspaceEngine ?: return
        viewModelScope.launch {
            val result = runCatching { engine.renameFile(path, newName) }.getOrElse { "Error: ${it.message}" }
            _snackbar.emit(result)
            refreshWorkspace()
        }
    }

    fun deleteFile(path: String) {
        val engine = workspaceEngine ?: return
        viewModelScope.launch {
            val result = runCatching { engine.deleteFile(path) }.getOrElse { "Error: ${it.message}" }
            _snackbar.emit(result)
            refreshWorkspace()
        }
    }

    fun zipWorkspace() {
        val engine = workspaceEngine ?: return
        viewModelScope.launch {
            try {
                val zip = engine.zipProject("export_${System.currentTimeMillis()}")
                _state.update { it.copy(lastExportedZip = zip.absolutePath) }
                _snackbar.emit("ZIP ready: ${zip.name}")
            } catch (e: Exception) {
                _snackbar.emit(e.message ?: "Failed to create ZIP archive")
            }
        }
    }

    // ---------- Organizer (paste-a-whole-project mode) ----------

    fun organizeDump(rawDump: String) {
        val settings = _state.value.settings ?: return
        val engine = workspaceEngine ?: return
        if (rawDump.isBlank() || _state.value.organizerRunning) return
        _state.update {
            it.copy(
                organizerRunning = true, organizerLog = emptyList(), organizerPhase = OrganizerPhase.THINKING,
                organizerThinkingChars = 0, organizerResultSummary = null
            )
        }
        viewModelScope.launch {
            ProjectDumpEngine(settings.provider).organize(rawDump, engine).collect { progress ->
                when (progress) {
                    is ProjectDumpEngine.Progress.Thinking ->
                        _state.update { it.copy(organizerThinkingChars = progress.charsReceived) }
                    is ProjectDumpEngine.Progress.Parsed ->
                        _state.update {
                            it.copy(
                                organizerPhase = OrganizerPhase.WRITING,
                                organizerLog = listOf(LogLine("Found ${progress.fileCount} files for project \"${progress.projectName}\" — writing…"))
                            )
                        }
                    is ProjectDumpEngine.Progress.Writing ->
                        _state.update { it.copy(organizerLog = it.organizerLog + progress.log) }
                    is ProjectDumpEngine.Progress.Failed -> {
                        _state.update {
                            it.copy(
                                organizerLog = it.organizerLog + LogLine(progress.message, isError = true),
                                organizerRunning = false, organizerPhase = OrganizerPhase.FAILED
                            )
                        }
                        _snackbar.emit(progress.message)
                    }
                    ProjectDumpEngine.Progress.Done -> {
                        val fileCount = _state.value.organizerLog.count { !it.isError && it.text.startsWith("Wrote ") }
                        _state.update {
                            it.copy(
                                organizerRunning = false, organizerPhase = OrganizerPhase.DONE,
                                organizerResultSummary = "Organized $fileCount file${if (fileCount == 1) "" else "s"} into the active project."
                            )
                        }
                        refreshWorkspace()
                    }
                }
            }
        }
    }

    // ---------- Settings ----------

    fun saveProvider(baseUrl: String, apiKey: String, model: String) {
        if (baseUrl.isBlank() || model.isBlank()) {
            viewModelScope.launch { _snackbar.emit("Base URL and model are required") }
            return
        }
        viewModelScope.launch {
            settingsStore.saveProvider(baseUrl, apiKey, model)
            _snackbar.emit("Provider saved and activated")
        }
    }

    fun saveCapabilities(fileReadWrite: Boolean, zip: Boolean) {
        viewModelScope.launch { settingsStore.saveCapabilities(fileReadWrite, zip) }
    }

    fun saveGenerationParams(temperature: Float, maxTokens: Int) {
        viewModelScope.launch { settingsStore.saveGenerationParams(temperature, maxTokens) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsStore.saveThemeMode(mode) }
    }

    fun currentZipFile(): File? = _state.value.lastExportedZip?.let { File(it) }
}
