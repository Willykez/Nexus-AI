package com.example.aicoder

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class WorkspaceEngine(context: Context) {
    private val context = context.applicationContext
    private val root = File(context.filesDir, "workspace").apply { mkdirs() }
    private val exportDir = File(context.filesDir, "exports").apply { mkdirs() }

    val workspacePath: String
        get() = root.absolutePath

    private fun safe(path: String, allowRoot: Boolean = false): File =
        WorkspacePathPolicy.resolve(root, path, allowRoot)

    suspend fun fileExists(path: String): Boolean = withContext(Dispatchers.IO) { runCatching { safe(path).exists() }.getOrDefault(false) }

    suspend fun readFile(path: String): String = withContext(Dispatchers.IO) {
        try {
            val file = safe(path)
            require(file.isFile) { "File not found: $path" }
            file.readText(StandardCharsets.UTF_8)
        } catch (e: Exception) {
            "Error reading $path: ${e.message}"
        }
    }

    suspend fun writeFile(
        path: String,
        content: String,
        onProgress: suspend (Int) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        try {
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
                check(temp.delete()) { "Temporary file cleanup failed" }
            }
            "Success: wrote ${data.size} bytes to $path"
        } catch (e: Exception) {
            "Error writing $path: ${e.message}"
        }
    }

    suspend fun listFiles(path: String = ""): String = withContext(Dispatchers.IO) {
        try {
            val dir = safe(path, allowRoot = true)
            require(dir.isDirectory) { "Directory not found: $path" }
            dir.walkTopDown()
                .filter { it != root }
                .sortedWith(compareBy<File> { it.relativeTo(root).path.count { c -> c == File.separatorChar } }.thenBy { it.name.lowercase() })
                .joinToString("\n") {
                    val rel = it.relativeTo(root).path.replace(File.separatorChar, '/')
                    if (it.isDirectory) "[DIR] $rel" else "[FILE] $rel"
                }
                .ifBlank { "Workspace is empty." }
        } catch (e: Exception) {
            "Error listing files: ${e.message}"
        }
    }

    suspend fun getFileTreeList(): List<FileNode> = withContext(Dispatchers.IO) {
        root.walkTopDown()
            .filter { it != root }
            .sortedWith(compareBy<File> { it.relativeTo(root).path.count { c -> c == File.separatorChar } }.thenBy { it.name.lowercase() })
            .map {
                val path = it.relativeTo(root).path.replace(File.separatorChar, '/')
                FileNode(
                    path = path,
                    name = it.name,
                    isDirectory = it.isDirectory,
                    depth = path.count { c -> c == '/' },
                    sizeBytes = if (it.isFile) it.length() else 0L
                )
            }
            .toList()
    }

    suspend fun deleteFile(path: String): String = withContext(Dispatchers.IO) {
        try {
            val file = safe(path)
            require(file.exists()) { "File or directory not found" }
            require(if (file.isDirectory) file.deleteRecursively() else file.delete()) { "Delete failed" }
            "Deleted $path"
        } catch (e: Exception) {
            "Error deleting $path: ${e.message}"
        }
    }

    suspend fun renameFile(oldPath: String, newName: String): String = withContext(Dispatchers.IO) {
        try {
            require(newName.isNotBlank()) { "New name cannot be empty" }
            require(!newName.contains('/') && !newName.contains('\\')) { "New name must be a single filename" }
            require(newName !in setOf(".", "..")) { "Invalid new name" }
            val old = safe(oldPath)
            require(old.exists()) { "File or directory not found" }
            val destination = File(old.parentFile, newName).canonicalFile
            require(destination.parentFile?.canonicalFile == old.parentFile?.canonicalFile) { "Invalid destination" }
            require(!destination.exists()) { "Destination already exists" }
            require(old.renameTo(destination)) { "Rename failed" }
            "Renamed $oldPath to $newName"
        } catch (e: Exception) {
            "Error renaming $oldPath: ${e.message}"
        }
    }

    suspend fun zipProject(onProgress: suspend (String) -> Unit = {}): File = withContext(Dispatchers.IO) {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
        val zip = File(exportDir, "workspace_$stamp.zip")
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

    suspend fun importProject(treeUri: Uri, onProgress: suspend (String) -> Unit = {}) = withContext(Dispatchers.IO) {
        val source = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalStateException("Selected folder is not accessible")
        require(source.isDirectory) { "Selected location is not a folder" }

        suspend fun copyDirectory(dir: DocumentFile, relative: String) {
            for (child in dir.listFiles()) {
                val name = child.name?.takeIf { it.isNotBlank() } ?: continue
                if (name == ".git" || name == ".gradle" || name == "build" || name == "node_modules") continue
                val childPath = if (relative.isBlank()) name else "$relative/$name"
                if (child.isDirectory) {
                    safe(childPath, allowRoot = true).mkdirs()
                    copyDirectory(child, childPath)
                } else {
                    val target = safe(childPath)
                    target.parentFile?.mkdirs()
                    context.contentResolver.openInputStream(child.uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw IllegalStateException("Could not read $childPath")
                    onProgress("Imported $childPath")
                }
            }
        }
        copyDirectory(source, "")
    }

    suspend fun stats(): WorkspaceStats = withContext(Dispatchers.IO) {
        val files = root.walkTopDown().filter { it.isFile }.toList()
        WorkspaceStats(
            fileCount = files.size,
            directoryCount = root.walkTopDown().count { it.isDirectory && it != root },
            bytes = files.sumOf { it.length() },
            workspacePath = root.absolutePath
        )
    }
}

data class WorkspaceStats(
    val fileCount: Int,
    val directoryCount: Int,
    val bytes: Long,
    val workspacePath: String
)
