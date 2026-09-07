package com.nexusai.agent.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Native, sandboxed workspace tool executor. Every operation is confined to a single root
 * directory inside the app's private storage (context.filesDir/workspace) so an untrusted model
 * response can never write or read outside of it, regardless of ".." traversal attempts.
 */
class WorkspaceEngine(private val rootDir: File) {

    init {
        if (!rootDir.exists()) rootDir.mkdirs()
    }

    sealed class WorkspaceException(message: String) : IOException(message) {
        class PathEscape(path: String) : WorkspaceException("Refused unsafe path outside workspace: $path")
        class NotFound(path: String) : WorkspaceException("File not found: $path")
        class IsDirectory(path: String) : WorkspaceException("Path is a directory, not a file: $path")
    }

    /** Resolves a workspace-relative path to an absolute File, rejecting any path traversal. */
    private fun resolveSafe(relativePath: String): File {
        val cleaned = relativePath.trim().removePrefix("/").removePrefix("./")
        val target = File(rootDir, cleaned).canonicalFile
        val rootCanonical = rootDir.canonicalFile
        if (!target.path.equals(rootCanonical.path) && !target.path.startsWith(rootCanonical.path + File.separator)) {
            throw WorkspaceException.PathEscape(relativePath)
        }
        return target
    }

    suspend fun writeFile(relativePath: String, content: String): String = withContext(Dispatchers.IO) {
        val file = resolveSafe(relativePath)
        file.parentFile?.mkdirs()
        file.writeText(content, Charsets.UTF_8)
        "Wrote ${content.toByteArray(Charsets.UTF_8).size} bytes to $relativePath"
    }

    suspend fun readFile(relativePath: String): String = withContext(Dispatchers.IO) {
        val file = resolveSafe(relativePath)
        if (!file.exists()) throw WorkspaceException.NotFound(relativePath)
        if (file.isDirectory) throw WorkspaceException.IsDirectory(relativePath)
        file.readText(Charsets.UTF_8)
    }

    suspend fun listFiles(relativePath: String = ""): FileNode = withContext(Dispatchers.IO) {
        val start = if (relativePath.isBlank()) rootDir else resolveSafe(relativePath)
        buildNode(start)
    }

    private fun buildNode(file: File): FileNode {
        val relative = file.relativeTo(rootDir).path.ifBlank { "" }
        return if (file.isDirectory) {
            val children = (file.listFiles()?.toList() ?: emptyList())
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                .map { buildNode(it) }
            FileNode(
                name = if (relative.isBlank()) "workspace" else file.name,
                path = relative,
                isDirectory = true,
                children = children
            )
        } else {
            FileNode(
                name = file.name,
                path = relative,
                isDirectory = false,
                sizeBytes = file.length()
            )
        }
    }

    /** Recursively zips every file currently under the workspace root into filesDir/exports/<name>.zip. */
    suspend fun zipProject(archiveName: String = "workspace"): File = withContext(Dispatchers.IO) {
        val exportsDir = File(rootDir.parentFile, "exports").apply { mkdirs() }
        val safeName = archiveName.trim().ifBlank { "workspace" }.replace(Regex("[^A-Za-z0-9_\\-]"), "_")
        val zipFile = File(exportsDir, "$safeName.zip")

        ZipOutputStream(zipFile.outputStream().buffered()).use { zos ->
            zos.setLevel(6)
            if (rootDir.exists()) {
                addDirToZip(rootDir, rootDir, zos)
            }
        }
        zipFile
    }

    private fun addDirToZip(root: File, current: File, zos: ZipOutputStream) {
        val files = current.listFiles() ?: return
        for (file in files.sortedBy { it.name }) {
            val entryPath = file.relativeTo(root).path.replace(File.separatorChar, '/')
            if (file.isDirectory) {
                zos.putNextEntry(ZipEntry("$entryPath/"))
                zos.closeEntry()
                addDirToZip(root, file, zos)
            } else {
                zos.putNextEntry(ZipEntry(entryPath))
                BufferedInputStream(FileInputStream(file)).use { input ->
                    input.copyTo(zos, bufferSize = 8 * 1024)
                }
                zos.closeEntry()
            }
        }
    }

    fun workspaceRootPath(): String = rootDir.absolutePath
}
