package com.nexusforge.app.data.workspace

import android.content.Context
import com.nexusforge.app.data.FileNode
import com.nexusforge.app.data.WorkspaceStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A private, disposable sandbox at context.filesDir/workspace. This is the ONLY thing an agent
 * run against a Sandbox project can ever touch, regardless of what path a model response asks
 * for — every path is canonicalized and checked against the root before any I/O happens.
 */
class SandboxWorkspaceEngine(context: Context) : WorkspaceEngine {

    private val root = File(context.filesDir, "workspace").apply { mkdirs() }
    private val exportDir = File(context.filesDir, "exports").apply { mkdirs() }

    override fun locationLabel(): String = root.absolutePath

    private fun safe(path: String, allowRoot: Boolean = false): File {
        val clean = path.trim().replace('\\', '/')
        if (clean.isBlank()) {
            require(allowRoot) { "Path cannot be empty" }
            return root.canonicalFile
        }
        require(!clean.startsWith("/")) { "Absolute paths are not allowed" }
        require(!clean.split('/').any { it == ".." }) { "Parent traversal is not allowed" }
        require(!clean.split('/').any { it.isBlank() || it == "." }) { "Invalid path" }
        val base = root.canonicalFile
        val result = File(base, clean).canonicalFile
        require(result.toPath().startsWith(base.toPath())) { "Path escapes the sandbox" }
        return result
    }

    override suspend fun fileExists(path: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { safe(path).exists() }.getOrDefault(false)
    }

    override suspend fun readFile(path: String): String = withContext(Dispatchers.IO) {
        val file = safe(path)
        if (!file.isFile) throw WorkspaceEngine.WorkspaceException("File not found: $path")
        file.readText(StandardCharsets.UTF_8)
    }

    override suspend fun writeFile(path: String, content: String, onProgress: suspend (Int) -> Unit): String =
        withContext(Dispatchers.IO) {
            val target = safe(path)
            target.parentFile?.mkdirs()
            val data = content.toByteArray(StandardCharsets.UTF_8)
            val temp = File(target.parentFile, ".${target.name}.${System.nanoTime()}.tmp")
            FileOutputStream(temp).use { out ->
                var offset = 0
                while (offset < data.size) {
                    val count = minOf(8192, data.size - offset)
                    out.write(data, offset, count)
                    offset += count
                    onProgress((offset * 100 / data.size.coerceAtLeast(1)).coerceIn(0, 100))
                }
                out.fd.sync()
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            "Wrote ${data.size} bytes to $path"
        }

    override suspend fun deleteFile(path: String): String = withContext(Dispatchers.IO) {
        val file = safe(path)
        if (!file.exists()) throw WorkspaceEngine.WorkspaceException("Not found: $path")
        val ok = if (file.isDirectory) file.deleteRecursively() else file.delete()
        if (!ok) throw WorkspaceEngine.WorkspaceException("Delete failed for $path")
        "Deleted $path"
    }

    override suspend fun renameFile(path: String, newName: String): String = withContext(Dispatchers.IO) {
        require(newName.isNotBlank() && !newName.contains('/') && newName !in setOf(".", "..")) {
            "Invalid new name"
        }
        val old = safe(path)
        if (!old.exists()) throw WorkspaceEngine.WorkspaceException("Not found: $path")
        val destination = File(old.parentFile, newName)
        if (destination.exists()) throw WorkspaceEngine.WorkspaceException("$newName already exists here")
        if (!old.renameTo(destination)) throw WorkspaceEngine.WorkspaceException("Rename failed")
        "Renamed $path to $newName"
    }

    override suspend fun listFilesAsText(path: String): String = withContext(Dispatchers.IO) {
        val dir = safe(path, allowRoot = true)
        if (!dir.isDirectory) throw WorkspaceEngine.WorkspaceException("Not a directory: $path")
        dir.walkTopDown()
            .filter { it != root }
            .sortedWith(compareBy<File> { it.relativeTo(root).path.count { c -> c == File.separatorChar } }.thenBy { it.name.lowercase() })
            .joinToString("\n") {
                val rel = it.relativeTo(root).path.replace(File.separatorChar, '/')
                if (it.isDirectory) "[DIR] $rel" else "[FILE] $rel"
            }
            .ifBlank { "(empty)" }
    }

    override suspend fun fileTree(): FileNode = withContext(Dispatchers.IO) { buildNode(root) }

    private fun buildNode(file: File): FileNode {
        val relative = file.relativeTo(root).path.ifBlank { "" }
        return if (file.isDirectory) {
            val children = (file.listFiles()?.toList() ?: emptyList())
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                .map { buildNode(it) }
            FileNode(relative, if (relative.isBlank()) "project" else file.name, true,
                relative.count { it == '/' }, 0L, children)
        } else {
            FileNode(relative, file.name, false, relative.count { it == '/' }, file.length())
        }
    }

    override suspend fun stats(): WorkspaceStats = withContext(Dispatchers.IO) {
        val files = root.walkTopDown().filter { it.isFile }.toList()
        WorkspaceStats(
            fileCount = files.size,
            directoryCount = root.walkTopDown().count { it.isDirectory && it != root },
            bytes = files.sumOf { it.length() },
            label = "Private sandbox"
        )
    }

    override suspend fun zipProject(archiveName: String, onProgress: suspend (String) -> Unit): File =
        withContext(Dispatchers.IO) {
            val safeName = archiveName.trim().ifBlank { "workspace" }.replace(Regex("[^A-Za-z0-9_\\-]"), "_")
            val zip = File(exportDir, "$safeName.zip")
            val files = root.walkTopDown().filter { it.isFile }.toList()
            ZipOutputStream(FileOutputStream(zip)).use { out ->
                files.forEachIndexed { index, file ->
                    val relative = file.relativeTo(root).path.replace(File.separatorChar, '/')
                    onProgress("Packaging $relative (${index + 1}/${files.size})")
                    out.putNextEntry(ZipEntry(relative))
                    file.inputStream().use { input -> input.copyTo(out, 16 * 1024) }
                    out.closeEntry()
                }
            }
            zip
        }
}
