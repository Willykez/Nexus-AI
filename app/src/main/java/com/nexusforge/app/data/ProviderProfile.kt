package com.nexusforge.app.data

/**
 * A fully-saved, ready-to-use provider credential — the point being that switching between,
 * say, three different Gemini API keys is a tap on a name, never a re-paste of a key you
 * might not remember. [apiKey] is plaintext here (this is the in-memory/runtime shape); at
 * rest it's Keystore-encrypted by ProviderProfileStore.
 */
data class ProviderProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val createdAt: Long,
    val lastUsedAt: Long
) {
    fun toConfig(): ProviderConfig = ProviderConfig(baseUrl = baseUrl, apiKey = apiKey, model = model)
}
