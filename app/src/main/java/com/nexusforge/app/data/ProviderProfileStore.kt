package com.nexusforge.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** On-disk shape — the key is encrypted, never written in plaintext. */
@Serializable
private data class PersistedProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val encryptedApiKey: String,
    val model: String,
    val createdAt: Long,
    val lastUsedAt: Long
)

/**
 * Every saved provider credential the user has added — the fix for "I have three Gemini keys
 * and keep having to re-paste one." Add once, switch by name forever after.
 */
class ProviderProfileStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val file = File(context.filesDir, "provider_profiles.json")
    private val listSerializer = ListSerializer(PersistedProfile.serializer())
    private val lock = Any()

    suspend fun list(): List<ProviderProfile> = withContext(Dispatchers.IO) {
        synchronized(lock) { readAll() }.map { it.toRuntime() }.sortedByDescending { it.lastUsedAt }
    }

    suspend fun get(id: String): ProviderProfile? = withContext(Dispatchers.IO) {
        synchronized(lock) { readAll() }.find { it.id == id }?.toRuntime()
    }

    suspend fun create(name: String, baseUrl: String, apiKey: String, model: String): ProviderProfile =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val persisted = PersistedProfile(
                id = UUID.randomUUID().toString(),
                name = name.trim().ifBlank { SettingsStore.providerLabel(baseUrl) },
                baseUrl = baseUrl.trim().trimEnd('/'),
                encryptedApiKey = SecureCrypto.encrypt(apiKey.trim()),
                model = model.trim(),
                createdAt = now, lastUsedAt = now
            )
            synchronized(lock) { writeAll(readAll() + persisted) }
            persisted.toRuntime()
        }

    /** Blank [apiKey] keeps the previously saved key rather than wiping it. */
    suspend fun update(id: String, name: String, baseUrl: String, apiKey: String, model: String) =
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                writeAll(readAll().map {
                    if (it.id != id) it else it.copy(
                        name = name.trim().ifBlank { it.name },
                        baseUrl = baseUrl.trim().trimEnd('/'),
                        encryptedApiKey = if (apiKey.isBlank()) it.encryptedApiKey else SecureCrypto.encrypt(apiKey.trim()),
                        model = model.trim()
                    )
                })
            }
        }

    suspend fun touch(id: String): ProviderProfile? = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val updated = readAll().map { if (it.id == id) it.copy(lastUsedAt = System.currentTimeMillis()) else it }
            writeAll(updated)
            updated.find { it.id == id }?.toRuntime()
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        synchronized(lock) { writeAll(readAll().filterNot { it.id == id }) }
    }

    private fun PersistedProfile.toRuntime() = ProviderProfile(
        id = id, name = name, baseUrl = baseUrl, apiKey = SecureCrypto.decrypt(encryptedApiKey),
        model = model, createdAt = createdAt, lastUsedAt = lastUsedAt
    )

    private fun readAll(): List<PersistedProfile> {
        if (!file.exists()) return emptyList()
        return runCatching { json.decodeFromString(listSerializer, file.readText()) }.getOrDefault(emptyList())
    }

    private fun writeAll(profiles: List<PersistedProfile>) {
        file.writeText(json.encodeToString(listSerializer, profiles))
    }
}
