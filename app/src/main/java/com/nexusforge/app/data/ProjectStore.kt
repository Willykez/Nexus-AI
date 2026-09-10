package com.nexusforge.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Every project the app knows about — a private sandbox or an attached real folder — kept as a
 * small on-disk list so a conversation can point at one of them explicitly (see the project
 * picker) instead of every chat silently sharing a single global workspace.
 */
class ProjectStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val file = File(context.filesDir, "projects.json")
    private val listSerializer = ListSerializer(Project.serializer())
    private val lock = Any()

    suspend fun list(): List<Project> = withContext(Dispatchers.IO) {
        synchronized(lock) { readAll() }.sortedByDescending { it.lastUsedAt }
    }

    suspend fun get(id: String): Project? = withContext(Dispatchers.IO) {
        synchronized(lock) { readAll() }.find { it.id == id }
    }

    suspend fun createSandbox(name: String): Project = withContext(Dispatchers.IO) {
        val project = Project(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "New sandbox" },
            source = ProjectSource.Sandbox(UUID.randomUUID().toString()),
            createdAt = System.currentTimeMillis(),
            lastUsedAt = System.currentTimeMillis()
        )
        synchronized(lock) { writeAll(readAll() + project) }
        project
    }

    /** Reuses an existing entry for the same folder instead of creating a duplicate. */
    suspend fun attachFolder(treeUri: String, displayName: String): Project = withContext(Dispatchers.IO) {
        val existing = synchronized(lock) { readAll() }.find {
            (it.source as? ProjectSource.AttachedFolder)?.treeUri == treeUri
        }
        if (existing != null) return@withContext touchInternal(existing.id) ?: existing
        val project = Project(
            id = UUID.randomUUID().toString(),
            name = displayName,
            source = ProjectSource.AttachedFolder(treeUri, displayName),
            createdAt = System.currentTimeMillis(),
            lastUsedAt = System.currentTimeMillis()
        )
        synchronized(lock) { writeAll(readAll() + project) }
        project
    }

    suspend fun touch(id: String): Project? = withContext(Dispatchers.IO) { touchInternal(id) }

    private fun touchInternal(id: String): Project? = synchronized(lock) {
        val updated = readAll().map { if (it.id == id) it.copy(lastUsedAt = System.currentTimeMillis()) else it }
        writeAll(updated)
        updated.find { it.id == id }
    }

    suspend fun rename(id: String, newName: String) = withContext(Dispatchers.IO) {
        if (newName.isBlank()) return@withContext
        synchronized(lock) { writeAll(readAll().map { if (it.id == id) it.copy(name = newName.trim()) else it }) }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val project = synchronized(lock) { readAll() }.find { it.id == id }
        synchronized(lock) { writeAll(readAll().filterNot { it.id == id }) }
        // Best-effort cleanup so a deleted sandbox project doesn't linger on disk.
        val sandboxSource = project?.source as? ProjectSource.Sandbox
        if (sandboxSource != null) {
            File(context.filesDir, "workspaces/${sandboxSource.projectId}").deleteRecursively()
        }
    }

    private fun readAll(): List<Project> {
        if (!file.exists()) return emptyList()
        return runCatching { json.decodeFromString(listSerializer, file.readText()) }.getOrDefault(emptyList())
    }

    private fun writeAll(projects: List<Project>) {
        file.writeText(json.encodeToString(listSerializer, projects))
    }
}
