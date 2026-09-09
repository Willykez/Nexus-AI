package com.nexusforge.app.data.workspace

import com.nexusforge.app.data.FileNode
import com.nexusforge.app.data.WorkspaceStats
import java.io.File

/**
 * Everything the agent (and the Workspace/Organizer screens) can do to the active project,
 * regardless of whether that project is a private sandbox or a real folder the user attached.
 * Every implementation MUST enforce its own sandboxing/path-safety rules internally — callers
 * never have to think about traversal here, only about what operation they want.
 */
interface WorkspaceEngine {

    /** Human-readable description of where files actually live, shown in Settings/Workspace. */
    fun locationLabel(): String

    suspend fun readFile(path: String): String

    /** Returns a short human-readable result string, e.g. "Wrote 512 bytes to Task.kt". */
    suspend fun writeFile(path: String, content: String, onProgress: suspend (Int) -> Unit = {}): String

    suspend fun fileExists(path: String): Boolean

    suspend fun deleteFile(path: String): String

    suspend fun renameFile(path: String, newName: String): String

    /** Text tree listing, used as the tool result fed back to the model. */
    suspend fun listFilesAsText(path: String = ""): String

    /** Structured tree, used to render the Workspace screen's file browser. */
    suspend fun fileTree(): FileNode

    suspend fun stats(): WorkspaceStats

    /** Zips the whole project into a local temp/exports file this process can share via FileProvider. */
    suspend fun zipProject(archiveName: String, onProgress: suspend (String) -> Unit = {}): File

    class WorkspaceException(message: String) : java.io.IOException(message)
}
