package com.nexusforge.app.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Names + JSON-schema definitions for the four native tools every project source (Sandbox or
 * an AttachedFolder) understands. A disabled capability is left out of the list returned here
 * AND is re-checked at execution time by WorkspaceEngine callers — belt and suspenders, per the
 * "capability toggles are authoritative" invariant.
 */
object ToolRegistry {
    const val READ_FILE = "read_file"
    const val WRITE_FILE = "write_file"
    const val LIST_FILES = "list_files"
    const val ZIP_PROJECT = "zip_project"

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

    private val readFileTool = Tool(function = FunctionDescription(
        READ_FILE, "Read a UTF-8 text file from the active project.",
        schema(mapOf("path" to "Relative path of the file, from the project root."), listOf("path"))
    ))

    private val writeFileTool = Tool(function = FunctionDescription(
        WRITE_FILE, "Create or completely replace a UTF-8 text file. Parent directories are created automatically. Always pass the FULL file content, never a partial diff.",
        schema(mapOf(
            "path" to "Relative path of the file, from the project root.",
            "content" to "Complete UTF-8 file content."
        ), listOf("path", "content"))
    ))

    private val listFilesTool = Tool(function = FunctionDescription(
        LIST_FILES, "List files and directories recursively in the active project.",
        schema(mapOf("path" to "Optional relative directory path. Empty or omitted means project root."), emptyList())
    ))

    private val zipProjectTool = Tool(function = FunctionDescription(
        ZIP_PROJECT, "Package the complete active project into a downloadable ZIP archive.",
        schema(mapOf("archiveName" to "Optional archive base name, without extension."), emptyList())
    ))

    /** Tools actually offered to the model this turn, respecting the user's capability toggles. */
    fun activeTools(flags: CapabilityFlags): List<Tool> = buildList {
        if (flags.fileReadWriteEnabled) {
            add(readFileTool)
            add(writeFileTool)
        }
        add(listFilesTool) // read-only listing is always available — there's nothing unsafe about it
        if (flags.zipEnabled) add(zipProjectTool)
    }

    fun systemPrompt(flags: CapabilityFlags, projectSource: ProjectSource): String {
        val toolNames = activeTools(flags).joinToString(", ") { it.function.name }
        val sourceNote = when (projectSource) {
            is ProjectSource.Sandbox ->
                "The project lives in a private sandbox. Nothing you do here can affect any real file outside it."
            is ProjectSource.AttachedFolder ->
                "The project is a REAL folder the user picked on their device (\"${projectSource.displayName}\"). " +
                    "Every write is a real, permanent change to their files — read before you overwrite, and don't " +
                    "touch files you weren't asked about."
        }
        return """
            You are Nexus, an on-device AI coding agent embedded in an Android app. $sourceNote
            You have native tools to read files, write files, list the project tree, and zip the project into a
            downloadable archive. All paths are relative to the project root — never use absolute paths or ".." segments.
            Prefer calling tools to actually create or fix working code rather than only describing it. When a request
            has multiple steps (e.g. "write two files and zip them"), do them all in this turn without waiting for
            another message, narrating briefly as you go. After tool calls finish, summarize what changed in plain
            language — no raw JSON or tool names in your final prose.
            Available tools: $toolNames.
        """.trimIndent()
    }
}
