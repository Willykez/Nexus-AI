package com.nexusforge.app.data.workspace

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.nexusforge.app.data.FileNode
import com.nexusforge.app.data.WorkspaceStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStreamWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Edits a REAL folder the user picked via the system folder picker (ACTION_OPEN_DOCUMENT_TREE).
 * Every write here is a permanent change to the user's actual files — there is no sandbox to
 * fall back on, so path validation (no absolute paths, no "..") is just as strict as the sandbox
 * engine's, but the consequence of a bug is different and callers/prompts should treat it that way.
 *
 * [treeUri] must already carry a persisted read/write URI permission (see
 * ContentResolver.takePersistableUriPermission, done once at attach time in Settings).
 */
class RealFolderWorkspaceEngine(
    private val context: Context,
    private val treeUri: Uri,
    private val displayName: String
) : WorkspaceEngine {

    private val genericMime = "application/octet-stream"
    private val exportDir = File(context.filesDir, "exports").apply { mkdirs() }

    private fun root(): DocumentFile {
        val doc = DocumentFile.fromTreeUri(context, treeUri)
        if (doc == null || !doc.isDirectory) {
            throw WorkspaceEngine.WorkspaceException("Attached folder is no longer accessible. Re-attach it in Settings.")
        }
        return doc
    }

    override fun locationLabel(): String = displayName

    private fun segments(path: String): List<String> {
        val clean = path.trim().replace('\\', '/').trim('/')
        if (clean.isBlank()) return emptyList()
        val parts = clean.split('/').filter { it.isNotBlank() }
        require(parts.none { it == ".." || it == "." }) { "Path escapes the attached folder" }
        return parts
    }

    /** Walks/creates directories down to the parent of the final segment. */
    private fun resolveParentDir(root: DocumentFile, dirSegments: List<String>, createMissing: Boolean): DocumentFile {
        var current = root
        for (segment in dirSegments) {
            val existing = current.findFile(segment)
            current = when {
                existing != null && existing.isDirectory -> existing
                existing != null -> throw WorkspaceEngine.WorkspaceException("$segment exists and is a file, not a folder")
                createMissing -> current.createDirectory(segment)
                    ?: throw WorkspaceEngine.WorkspaceException("Could not create folder $segment")
                else -> throw WorkspaceEngine.WorkspaceException("Folder not found: $segment")
            }
        }
        return current
    }

    private fun findDocument(path: String): DocumentFile? {
        val parts = segments(path)
        if (parts.isEmpty()) return root()
        var current: DocumentFile? = root()
        for (part in parts) {
            current = current?.findFile(part) ?: return null
        }
        return current
    }

    override suspend fun fileExists(path: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { findDocument(path) != null }.getOrDefault(false)
    }

    override suspend fun readFile(path: String): String = withContext(Dispatchers.IO) {
        val doc = findDocument(path) ?: throw WorkspaceEngine.WorkspaceException("File not found: $path")
        if (!doc.isFile) throw WorkspaceEngine.WorkspaceException("Not a file: $path")
        context.contentResolver.openInputStream(doc.uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw WorkspaceEngine.WorkspaceException("Could not open $path")
    }

    override suspend fun writeFile(path: String, content: String, onProgress: suspend (Int) -> Unit): String =
        withContext(Dispatchers.IO) {
            val parts = segments(path)
            require(parts.isNotEmpty()) { "Path cannot be empty" }
            val fileName = parts.last()
            val parentDir = resolveParentDir(root(), parts.dropLast(1), createMissing = true)
            val existing = parentDir.findFile(fileName)
            val target = if (existing != null && existing.isFile) existing
            else {
                existing?.delete()
                parentDir.createFile(genericMime, fileName)
                    ?: throw WorkspaceEngine.WorkspaceException("Could not create $path")
            }
            val stream = context.contentResolver.openOutputStream(target.uri, "wt")
                ?: throw WorkspaceEngine.WorkspaceException("Could not open $path for writing")
            stream.use { out -> OutputStreamWriter(out, Charsets.UTF_8).use { it.write(content) } }
            onProgress(100)
            "Wrote ${content.toByteArray(Charsets.UTF_8).size} bytes to $path"
        }

    override suspend fun deleteFile(path: String): String = withContext(Dispatchers.IO) {
        val doc = findDocument(path) ?: throw WorkspaceEngine.WorkspaceException("Not found: $path")
        if (!doc.delete()) throw WorkspaceEngine.WorkspaceException("Delete failed for $path")
        "Deleted $path"
    }

    override suspend fun renameFile(path: String, newName: String): String = withContext(Dispatchers.IO) {
        require(newName.isNotBlank() && !newName.contains('/')) { "Invalid new name" }
        val doc = findDocument(path) ?: throw WorkspaceEngine.WorkspaceException("Not found: $path")
        if (!doc.renameTo(newName)) throw WorkspaceEngine.WorkspaceException("Rename failed for $path")
        "Renamed $path to $newName"
    }

    override suspend fun listFilesAsText(path: String): String = withContext(Dispatchers.IO) {
        val start = findDocument(path) ?: throw WorkspaceEngine.WorkspaceException("Not found: $path")
        val lines = mutableListOf<String>()
        fun walk(doc: DocumentFile, prefix: String) {
            val children = doc.listFiles().sortedWith(compareBy({ !it.isDirectory }, { it.name.orEmpty().lowercase() }))
            for (child in children) {
                val rel = if (prefix.isBlank()) child.name.orEmpty() else "$prefix/${child.name}"
                lines += if (child.isDirectory) "[DIR] $rel" else "[FILE] $rel"
                if (child.isDirectory) walk(child, rel)
            }
        }
        walk(start, path.trim('/'))
        lines.joinToString("\n").ifBlank { "(empty)" }
    }

    override suspend fun fileTree(): FileNode = withContext(Dispatchers.IO) { buildNode(root(), "") }

    private fun buildNode(doc: DocumentFile, relative: String): FileNode {
        return if (doc.isDirectory) {
            val children = doc.listFiles()
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.orEmpty().lowercase() }))
                .map { buildNode(it, if (relative.isBlank()) it.name.orEmpty() else "$relative/${it.name}") }
            FileNode(relative, if (relative.isBlank()) displayName else doc.name.orEmpty(), true,
                relative.count { it == '/' }, 0L, children)
        } else {
            FileNode(relative, doc.name.orEmpty(), false, relative.count { it == '/' }, doc.length())
        }
    }

    override suspend fun stats(): WorkspaceStats = withContext(Dispatchers.IO) {
        var files = 0
        var dirs = 0
        var bytes = 0L
        fun walk(doc: DocumentFile) {
            for (child in doc.listFiles()) {
                if (child.isDirectory) { dirs++; walk(child) } else { files++; bytes += child.length() }
            }
        }
        walk(root())
        WorkspaceStats(files, dirs, bytes, "Attached folder: $displayName")
    }

    override suspend fun zipProject(archiveName: String, onProgress: suspend (String) -> Unit): File =
        withContext(Dispatchers.IO) {
            val safeName = archiveName.trim().ifBlank { displayName }.replace(Regex("[^A-Za-z0-9_\\-]"), "_")
            val zip = File(exportDir, "$safeName.zip")
            val allFiles = mutableListOf<Pair<String, DocumentFile>>()
            fun collect(doc: DocumentFile, prefix: String) {
                for (child in doc.listFiles()) {
                    val rel = if (prefix.isBlank()) child.name.orEmpty() else "$prefix/${child.name}"
                    if (child.isDirectory) collect(child, rel) else allFiles += rel to child
                }
            }
            collect(root(), "")
            ZipOutputStream(zip.outputStream()).use { out ->
                allFiles.forEachIndexed { index, (rel, doc) ->
                    onProgress("Packaging $rel (${index + 1}/${allFiles.size})")
                    out.putNextEntry(ZipEntry(rel))
                    context.contentResolver.openInputStream(doc.uri)?.use { it.copyTo(out, 16 * 1024) }
                    out.closeEntry()
                }
            }
            zip
        }
}
