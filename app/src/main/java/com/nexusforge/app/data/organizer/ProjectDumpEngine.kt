package com.nexusforge.app.data.organizer

import com.nexusforge.app.data.AiClient
import com.nexusforge.app.data.ChatMessage
import com.nexusforge.app.data.ChatRequest
import com.nexusforge.app.data.ChatResponseChunk
import com.nexusforge.app.data.ProviderConfig
import com.nexusforge.app.data.workspace.WorkspaceEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

/**
 * The "dump a whole project in" mode: paste one giant blob of source, the model restructures
 * it into a real file tree, and this engine writes that tree through whichever WorkspaceEngine
 * is currently active (Sandbox or an AttachedFolder) — same write path, same safety guarantees
 * as the chat agent, just a different entry point into it.
 */
class ProjectDumpEngine(private val providerConfig: ProviderConfig) {

    private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

    sealed class Progress {
        data class Thinking(val charsReceived: Int) : Progress()
        data class Parsed(val projectName: String, val fileCount: Int) : Progress()
        data class Writing(val log: LogLine) : Progress()
        data class Failed(val message: String) : Progress()
        data object Done : Progress()
    }

    fun organize(rawDump: String, workspace: WorkspaceEngine): Flow<Progress> = flow {
        val client = AiClient(providerConfig)
        val prompt = """
            The user pasted an entire project's source below as one blob — multiple files
            concatenated together, possibly with filename markers, possibly without. Reconstruct
            it into a clean, real file tree.

            Respond with ONLY a JSON object of this exact shape, no prose, no markdown fences:
            {"projectName": "kebab-or-snake-case-name", "files": [{"path": "relative/path.ext", "content": "full file content"}]}

            Rules:
            - Every path is relative, forward-slash separated, no leading slash, no "..".
            - Preserve every file's full content exactly — do not summarize or truncate.
            - Infer sensible filenames/paths from context (package declarations, imports, comments)
              if the dump didn't label them itself.
            - projectName is a single root folder name for the whole thing.
        """.trimIndent()

        val messages = listOf(
            ChatMessage(role = "system", content = prompt),
            ChatMessage(role = "user", content = rawDump)
        )
        val request = ChatRequest(model = providerConfig.model, messages = messages, tools = null, stream = true, maxTokens = 16384)

        val text = StringBuilder()
        try {
            client.streamChatCompletion(request).collect { raw ->
                val chunk = lenientJson.decodeFromString(ChatResponseChunk.serializer(), raw)
                chunk.choices.firstOrNull()?.delta?.content?.let {
                    text.append(it)
                    emit(Progress.Thinking(text.length))
                }
            }
        } catch (e: Exception) {
            emit(Progress.Failed(e.message ?: "Connection failed while organizing the project."))
            client.close()
            return@flow
        }
        client.close()

        val cleaned = text.toString().trim().removeSurrounding("```json", "```").removeSurrounding("```", "```").trim()
        val parsed = try {
            lenientJson.decodeFromString(ParsedProject.serializer(), cleaned)
        } catch (e: Exception) {
            emit(Progress.Failed("The model's response wasn't valid JSON — try again, or with a smaller paste."))
            return@flow
        }

        if (parsed.files.isEmpty()) {
            emit(Progress.Failed("No files were recognized in that paste."))
            return@flow
        }

        emit(Progress.Parsed(parsed.projectName.ifBlank { "project" }, parsed.files.size))

        val root = parsed.projectName.ifBlank { "project" }.replace(Regex("[^A-Za-z0-9_\\-]"), "_")
        for (file in parsed.files) {
            val cleanPath = file.path.trim().trimStart('/').replace('\\', '/')
            if (cleanPath.isBlank() || cleanPath.contains("..")) {
                emit(Progress.Writing(LogLine("Skipped unsafe path: '${file.path}'", isError = true)))
                continue
            }
            val fullPath = "$root/$cleanPath"
            try {
                workspace.writeFile(fullPath, file.content)
                emit(Progress.Writing(LogLine("Wrote $fullPath")))
            } catch (e: Exception) {
                emit(Progress.Writing(LogLine("Error writing $fullPath: ${e.message}", isError = true)))
            }
        }
        emit(Progress.Done)
    }
}
