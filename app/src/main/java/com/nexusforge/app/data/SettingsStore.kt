package com.nexusforge.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "nexus_forge_settings")

data class AppSettings(
    val capabilities: CapabilityFlags,
    val temperature: Float,
    val maxOutputTokens: Int,
    val themeMode: ThemeMode,
    /** Which Project (see ProjectStore) to reopen on cold start — not a workspace itself. */
    val lastActiveProjectId: String?,
    /** Which saved ProviderProfile to reopen on cold start. */
    val lastActiveProviderProfileId: String?
)

/**
 * Global, provider-independent settings that survive app restart — everything else (chat
 * sessions, projects/workspaces, and now provider credentials) lives in its own dedicated store:
 * ChatHistoryStore, ProjectStore, ProviderProfileStore respectively. Keeping credentials out of
 * here is deliberate — a single global "the" API key was the whole problem when someone has
 * several keys for the same provider.
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val filesEnabled = booleanPreferencesKey("files_enabled")
        val zipEnabled = booleanPreferencesKey("zip_enabled")
        val temperature = stringPreferencesKey("temperature")
        val maxTokens = stringPreferencesKey("max_tokens")
        val themeMode = stringPreferencesKey("theme_mode") // "system" | "light" | "dark"
        val lastActiveProjectId = stringPreferencesKey("last_active_project_id")
        val lastActiveProviderProfileId = stringPreferencesKey("last_active_provider_profile_id")
    }

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            capabilities = CapabilityFlags(
                fileReadWriteEnabled = prefs[Keys.filesEnabled] ?: true,
                zipEnabled = prefs[Keys.zipEnabled] ?: true
            ),
            temperature = prefs[Keys.temperature]?.toFloatOrNull() ?: 0.2f,
            maxOutputTokens = prefs[Keys.maxTokens]?.toIntOrNull() ?: 8192,
            themeMode = when (prefs[Keys.themeMode]) {
                "light" -> ThemeMode.LIGHT
                "dark" -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            },
            lastActiveProjectId = prefs[Keys.lastActiveProjectId],
            lastActiveProviderProfileId = prefs[Keys.lastActiveProviderProfileId]
        )
    }

    suspend fun saveThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.themeMode] = when (mode) {
                ThemeMode.LIGHT -> "light"
                ThemeMode.DARK -> "dark"
                ThemeMode.SYSTEM -> "system"
            }
        }
    }

    suspend fun saveCapabilities(fileReadWrite: Boolean, zip: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.filesEnabled] = fileReadWrite
            prefs[Keys.zipEnabled] = zip
        }
    }

    suspend fun saveGenerationParams(temperature: Float, maxTokens: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.temperature] = temperature.toString()
            prefs[Keys.maxTokens] = maxTokens.toString()
        }
    }

    suspend fun saveLastActiveProject(id: String) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.lastActiveProjectId] = id }
    }

    suspend fun saveLastActiveProviderProfile(id: String) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.lastActiveProviderProfileId] = id }
    }

    companion object {
        /** Presets shown as tappable chips when adding a provider — a shortcut, never a requirement. */
        val PRESETS = listOf(
            Triple("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
            Triple("Gemini (OpenAI shim)", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-1.5-flash"),
            Triple("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
            Triple("Ollama (local)", "http://10.0.2.2:11434/v1", "llama3.1")
        )

        fun providerLabel(url: String): String = when {
            url.contains("deepseek", true) -> "DeepSeek"
            url.contains("generativelanguage", true) || url.contains("gemini", true) -> "Gemini"
            url.contains("ollama", true) || url.contains("11434") -> "Ollama"
            url.contains("openai", true) -> "OpenAI"
            else -> "Custom"
        }

        /** Ollama and other local hosts don't need a key to be considered "ready". */
        fun isKeylessLocal(url: String): Boolean =
            url.contains("10.0.2.2") || url.contains("127.0.0.1") || url.contains("localhost") || url.contains("11434")
    }
}
