package com.nexusforge.app.data.history

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persists chat sessions to disk as plain JSON, on-device only — no server, no account.
 * Each session is one file (chat_sessions/<id>.json); index.json holds just the lightweight
 * summaries so History can render a list without loading every full session into memory.
 * A single lock keeps the index from being read mid-write during rapid saves (e.g. one per
 * streamed turn).
 */
class ChatHistoryStore(context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val dir = File(context.filesDir, "chat_sessions").apply { mkdirs() }
    private val indexFile = File(dir, "index.json")
    private val lock = Any()
    private val summaryListSerializer = ListSerializer(ChatSessionSummary.serializer())

    suspend fun listSessions(): List<ChatSessionSummary> = withContext(Dispatchers.IO) {
        synchronized(lock) { readIndex() }.sortedByDescending { it.updatedAt }
    }

    suspend fun loadSession(id: String): ChatSession? = withContext(Dispatchers.IO) {
        val file = sessionFile(id)
        if (!file.exists()) return@withContext null
        runCatching { json.decodeFromString(ChatSession.serializer(), file.readText()) }.getOrNull()
    }

    suspend fun saveSession(session: ChatSession) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            sessionFile(session.id).writeText(json.encodeToString(ChatSession.serializer(), session))
            val index = readIndex().filterNot { it.id == session.id } + ChatSessionSummary(
                id = session.id,
                title = session.title,
                providerLabel = session.providerLabel,
                projectLabel = session.projectLabel,
                updatedAt = session.updatedAt
            )
            writeIndex(index)
        }
    }

    suspend fun deleteSession(id: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            sessionFile(id).delete()
            writeIndex(readIndex().filterNot { it.id == id })
        }
    }

    private fun sessionFile(id: String) = File(dir, "$id.json")

    private fun readIndex(): List<ChatSessionSummary> {
        if (!indexFile.exists()) return emptyList()
        return runCatching { json.decodeFromString(summaryListSerializer, indexFile.readText()) }.getOrDefault(emptyList())
    }

    private fun writeIndex(index: List<ChatSessionSummary>) {
        indexFile.writeText(json.encodeToString(summaryListSerializer, index))
    }

    companion object {
        /** First line of the first user message, trimmed to a sensible title length. */
        fun titleFrom(firstUserText: String?): String {
            val text = firstUserText?.trim().orEmpty()
            if (text.isBlank()) return "New chat"
            val firstLine = text.lineSequence().first().trim()
            return if (firstLine.length > 48) firstLine.take(48).trimEnd() + "…" else firstLine
        }
    }
}
