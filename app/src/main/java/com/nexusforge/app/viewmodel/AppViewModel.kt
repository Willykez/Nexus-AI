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
import com.nexusforge.app.data.ProviderProfile
import com.nexusforge.app.data.ProviderProfileStore
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

    // provider profiles — see "Additional fix" thread: fully-saved credentials, switch by name
    val activeProviderProfile: ProviderProfile? = null,
    val providerProfiles: List<ProviderProfile> = emptyList(),
    val showProviderPicker: Boolean = false,
    val providerFormEditing: ProviderProfile? = null,
    val showProviderForm: Boolean = false,

    // chat
    val sessionId: String = UUID.randomUUID().toString(),
    val messages: List<UiChatMessage> = emptyList(),
    val isAgentRunning: Boolean = false,
    val statusLabel: String = "Idle",
    val error: String? = null,

    // workspace
    val fileTree: FileNode = FileNode("", "project", true, 0),
    val projectFilePaths: List<String> = emptyList(), // flattened, for @ mentions
    val highlightedPath: String? = null,
    val selectedFile: String? = null,
    val selectedFileContent: String = "",
    val workspaceStats: WorkspaceStats? = null,
    val lastExportedZip: String? = null,
    val showFileTreeSheet: Boolean = false,

    // history
    val sessionSummaries: List<ChatSessionSummary> = emptyList(),
    val showHistorySidebar: Boolean = false,

    // organizer
    val organizerLog: List<LogLine> = emptyList(),
    val organizerRunning: Boolean = false,
    val organizerPhase: OrganizerPhase = OrganizerPhase.IDLE,
    val organizerThinkingChars: Int = 0,
    val organizerResultSummary: String? = null
)

enum class OrganizerPhase { IDLE, THINKING, WRITING, DONE, FAILED }

private fun flattenFiles(node: FileNode, acc: MutableList<String> = mutableListOf()): List<String> {
    if (!node.isDirectory && node.path.isNotBlank()) acc.add(node.path)
    node.children.forEach { flattenFiles(it, acc) }
    return acc
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsStore = SettingsStore(application)
    private val historyStore = ChatHistoryStore(application)
    private val projectStore = ProjectStore(application)
    private val providerProfileStore = ProviderProfileStore(application)
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
        viewModelScope.launch { initializeActiveProvider() }
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

    private suspend fun initializeActiveProvider() {
        val profiles = providerProfileStore.list()
        _state.update { it.copy(providerProfiles = profiles) }
        if (profiles.isEmpty()) return // no default auto-created — nothing to guess a key for
        val lastId = _state.value.settings?.lastActiveProviderProfileId
        val target = profiles.find { it.id == lastId } ?: profiles.first()
        _state.update { it.copy(activeProviderProfile = target) }
    }

    private fun engineFor(source: ProjectSource): WorkspaceEngine = when (source) {
        is ProjectSource.Sandbox -> SandboxWorkspaceEngine(getApplication(), source.projectId)
        is ProjectSource.AttachedFolder -> RealFolderWorkspaceEngine(getApplication(), Uri.parse(source.treeUri), source.displayName)
    }

    // ---------- Navigation ----------

    fun selectTab(tab: AppTab) = _state.update { it.copy(currentTab = tab) }

    fun openHistorySidebar() = _state.update { it.copy(showHistorySidebar = true) }
    fun dismissHistorySidebar() = _state.update { it.copy(showHistorySidebar = false) }

    fun openFileTreeSheet() = _state.update { it.copy(showFileTreeSheet = true) }
    fun dismissFileTreeSheet() = _state.update { it.copy(showFileTreeSheet = false) }

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

    // ---------- Provider profiles ----------

    fun openProviderPicker() {
        viewModelScope.launch { _state.update { it.copy(providerProfiles = providerProfileStore.list(), showProviderPicker = true) } }
    }

    fun dismissProviderPicker() = _state.update { it.copy(showProviderPicker = false) }

    fun selectProviderProfile(profile: ProviderProfile) {
        _state.update { it.copy(activeProviderProfile = profile, showProviderPicker = false) }
        viewModelScope.launch {
            settingsStore.saveLastActiveProviderProfile(profile.id)
            providerProfileStore.touch(profile.id)
            _state.update { it.copy(providerProfiles = providerProfileStore.list()) }
        }
    }

    fun requestAddProvider() = _state.update { it.copy(providerFormEditing = null, showProviderForm = true, showProviderPicker = false) }
    fun requestEditProvider(profile: ProviderProfile) = _state.update { it.copy(providerFormEditing = profile, showProviderForm = true, showProviderPicker = false) }
    fun dismissProviderForm() = _state.update { it.copy(showProviderForm = false) }

    fun saveProviderProfile(name: String, baseUrl: String, apiKey: String, model: String) {
        if (baseUrl.isBlank() || model.isBlank()) {
            viewModelScope.launch { _snackbar.emit("Base URL and model are required") }
            return
        }
        val editing = _state.value.providerFormEditing
        viewModelScope.launch {
            if (editing != null) {
                providerProfileStore.update(editing.id, name, baseUrl, apiKey, model)
            } else {
                val created = providerProfileStore.create(name, baseUrl, apiKey, model)
                selectProviderProfile(created)
            }
            val profiles = providerProfileStore.list()
            _state.update {
                it.copy(
                    providerProfiles = profiles, showProviderForm = false,
                    activeProviderProfile = if (editing != null && it.activeProviderProfile?.id == editing.id)
                        profiles.find { p -> p.id == editing.id } else it.activeProviderProfile
                )
            }
            _snackbar.emit(if (editing != null) "Provider updated" else "Provider saved and activated")
        }
    }

    fun deleteProviderProfile(profile: ProviderProfile) {
        viewModelScope.launch {
            providerProfileStore.delete(profile.id)
            val profiles = providerProfileStore.list()
            _state.update {
                it.copy(
                    providerProfiles = profiles,
                    activeProviderProfile = if (it.activeProviderProfile?.id == profile.id) profiles.firstOrNull() else it.activeProviderProfile
                )
            }
            _snackbar.emit("Deleted \"${profile.name}\"")
        }
    }

    // ---------- Chat ----------

    /** Reads any @path mentions that match a real project file and folds their content into the
     *  outgoing prompt, so the model genuinely has the file rather than just seeing its name. */
    private suspend fun expandMentions(rawText: String, engine: WorkspaceEngine): String {
        val knownPaths = _state.value.projectFilePaths.toSet()
        if (knownPaths.isEmpty()) return rawText
        val mentioned = Regex("@([\\w\\-./]+)").findAll(rawText).map { it.groupValues[1] }.distinct()
            .filter { it in knownPaths }.toList()
        if (mentioned.isEmpty()) return rawText
        val attached = StringBuilder()
        for (path in mentioned) {
            val content = runCatching { engine.readFile(path) }.getOrNull() ?: continue
            attached.append("\n\n[Attached: $path]\n```\n$content\n```")
        }
        return if (attached.isEmpty()) rawText else rawText + attached
    }

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
        val providerProfile = _state.value.activeProviderProfile
        if (providerProfile == null) {
            viewModelScope.launch { _snackbar.emit("Add or pick a provider first.") }
            openProviderPicker()
            return
        }

        _state.update { it.copy(messages = it.messages + UiChatMessage(role = "user", text = prompt), error = null) }

        agentJob = viewModelScope.launch {
            val expandedPrompt = expandMentions(prompt, engine)
            if (apiMessages.isEmpty()) {
                val settings = _state.value.settings
                apiMessages.add(ChatMessage(role = "system", content = ToolRegistry.systemPrompt(
                    settings?.capabilities ?: CapabilityFlags(), project.source
                )))
            }
            apiMessages.add(ChatMessage(role = "user", content = expandedPrompt))
            runAgentLoop(engine, providerProfile)
        }
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
                it.copy(sessionId = session.id, messages = restored, currentTab = AppTab.CHAT, showHistorySidebar = false)
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
        val providerProfile = current.activeProviderProfile
        val project = current.activeProject
        viewModelScope.launch {
            historyStore.saveSession(
                ChatSession(
                    id = current.sessionId,
                    title = ChatHistoryStore.titleFrom(firstUser),
                    providerLabel = providerProfile?.name ?: "—",
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

    private suspend fun runAgentLoop(engine: WorkspaceEngine, providerProfile: ProviderProfile) {
        val settings = _state.value.settings ?: return
        val client = AiClient(providerProfile.toConfig())
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
                    model = providerProfile.model,
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
                        }
                        if (!delta.toolCalls.isNullOrEmpty()) {
                            // Rebuilt from `calls` (keyed by the stream's own index) every time,
                            // rather than searched-and-replaced by toolName — a call's name can
                            // legitimately arrive blank in its first chunk and fill in a moment
                            // later, and matching by name meant that blank-named chip became a
                            // permanently-"running" orphan the instant the real name showed up,
                            // which is exactly the frozen second chip in the bug report.
                            updateMessage(assistant.id) { msg ->
                                val liveChips = calls.entries.sortedBy { it.key }.map { (_, c) ->
                                    val path = extractField(c.arguments.toString(), "path")
                                        ?: extractField(c.arguments.toString(), "archiveName")
                                    ToolChip(c.name.ifBlank { "unknown_tool" }, path, ToolStatus.RUNNING)
                                }
                                msg.copy(toolChips = liveChips)
                            }
                            _state.update { it.copy(statusLabel = "Running…") }
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

                // A blank id or name here means the provider's streaming tool-call chunks were
                // malformed (a real quirk with some non-OpenAI-compatible endpoints) — NOT that
                // no tool call happened. Silently dropping it made the loop think "no tools were
                // called" and quietly end the turn, which is exactly the "why does it stop"
                // symptom: frozen chips, no error text, no continuation. Falling back to a
                // synthetic id/name instead keeps the call in the loop so it either succeeds or
                // comes back as a clear "unsupported tool" error the model can see and retry from.
                val toolCalls = calls.entries.mapIndexed { position, (index, c) ->
                    val id = c.id.ifBlank { "call_${index}_$position" }
                    val name = c.name.ifBlank { "unknown_tool" }
                    ToolCall(id, "function", FunctionCall(name, c.arguments.toString()))
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
            if (tree != null) _state.update { it.copy(fileTree = tree, workspaceStats = stats, projectFilePaths = flattenFiles(tree)) }
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

    /** For the file-tree bottom sheet's inline expand-to-view — reads without leaving the sheet. */
    suspend fun readFileContent(path: String): String {
        val engine = workspaceEngine ?: return "No project selected."
        return runCatching { engine.readFile(path) }.getOrElse { "Error reading file: ${it.message}" }
    }

    // ---------- Organizer (paste-a-whole-project mode) ----------

    fun organizeDump(rawDump: String) {
        val providerProfile = _state.value.activeProviderProfile
        val engine = workspaceEngine ?: return
        if (providerProfile == null) {
            viewModelScope.launch { _snackbar.emit("Add or pick a provider first.") }
            openProviderPicker()
            return
        }
        if (rawDump.isBlank() || _state.value.organizerRunning) return
        _state.update {
            it.copy(
                organizerRunning = true, organizerLog = emptyList(), organizerPhase = OrganizerPhase.THINKING,
                organizerThinkingChars = 0, organizerResultSummary = null
            )
        }
        viewModelScope.launch {
            ProjectDumpEngine(providerProfile.toConfig()).organize(rawDump, engine).collect { progress ->
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
