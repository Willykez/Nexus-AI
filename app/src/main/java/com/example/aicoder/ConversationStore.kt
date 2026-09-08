package com.example.aicoder

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.conversationStore by preferencesDataStore(name = "nexus_ai_conversations")

class ConversationStore(private val context: Context) {
    private val key = stringPreferencesKey("sessions_json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val sessions: Flow<List<StoredSession>> = context.conversationStore.data.map { prefs ->
        prefs[key]?.let { encoded ->
            runCatching { json.decodeFromString<List<StoredSession>>(encoded) }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    suspend fun upsert(session: StoredSession) {
        context.conversationStore.edit { prefs ->
            val current = prefs[key]?.let { runCatching { json.decodeFromString<List<StoredSession>>(it) }.getOrDefault(emptyList()) } ?: emptyList()
            val next = (current.filterNot { it.id == session.id } + session)
                .sortedByDescending { it.updatedAt }
            prefs[key] = json.encodeToString(next)
        }
    }

    suspend fun delete(id: String) {
        context.conversationStore.edit { prefs ->
            val current = prefs[key]?.let { runCatching { json.decodeFromString<List<StoredSession>>(it) }.getOrDefault(emptyList()) } ?: emptyList()
            prefs[key] = json.encodeToString(current.filterNot { it.id == id })
        }
    }
}
