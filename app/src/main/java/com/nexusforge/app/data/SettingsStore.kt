package com.nexusforge.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.settingsDataStore by preferencesDataStore(name = "nexus_forge_settings")

data class AppSettings(
    val provider: ProviderConfig,
    val capabilities: CapabilityFlags,
    val temperature: Float,
    val maxOutputTokens: Int,
    val projectSource: ProjectSource,
    val themeMode: ThemeMode
)

/**
 * Single source of truth for everything that survives app restart except chat sessions
 * themselves (see ChatHistoryStore). The API key is Keystore-encrypted at rest; re-saving other
 * fields with a blank key field keeps the previously saved key rather than wiping it.
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val baseUrl = stringPreferencesKey("base_url")
        val encryptedApiKey = stringPreferencesKey("encrypted_api_key")
        val model = stringPreferencesKey("model")
        val filesEnabled = booleanPreferencesKey("files_enabled")
        val zipEnabled = booleanPreferencesKey("zip_enabled")
        val temperature = stringPreferencesKey("temperature")
        val maxTokens = stringPreferencesKey("max_tokens")
        val projectMode = stringPreferencesKey("project_mode") // "sandbox" | "attached"
        val attachedTreeUri = stringPreferencesKey("attached_tree_uri")
        val attachedDisplayName = stringPreferencesKey("attached_display_name")
        val themeMode = stringPreferencesKey("theme_mode") // "system" | "light" | "dark"
    }

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        val mode = prefs[Keys.projectMode] ?: "sandbox"
        val source = if (mode == "attached" && prefs[Keys.attachedTreeUri] != null) {
            ProjectSource.AttachedFolder(
                treeUri = prefs[Keys.attachedTreeUri]!!,
                displayName = prefs[Keys.attachedDisplayName] ?: "Attached folder"
            )
        } else ProjectSource.Sandbox

        AppSettings(
            provider = ProviderConfig(
                baseUrl = prefs[Keys.baseUrl] ?: "https://api.openai.com/v1",
                apiKey = decrypt(prefs[Keys.encryptedApiKey].orEmpty()),
                model = prefs[Keys.model] ?: "gpt-4o-mini"
            ),
            capabilities = CapabilityFlags(
                fileReadWriteEnabled = prefs[Keys.filesEnabled] ?: true,
                zipEnabled = prefs[Keys.zipEnabled] ?: true
            ),
            temperature = prefs[Keys.temperature]?.toFloatOrNull() ?: 0.2f,
            maxOutputTokens = prefs[Keys.maxTokens]?.toIntOrNull() ?: 8192,
            projectSource = source,
            themeMode = when (prefs[Keys.themeMode]) {
                "light" -> ThemeMode.LIGHT
                "dark" -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
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

    suspend fun saveProvider(baseUrl: String, apiKey: String, model: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.baseUrl] = baseUrl.trim().trimEnd('/')
            if (apiKey.isNotBlank()) prefs[Keys.encryptedApiKey] = encrypt(apiKey.trim())
            prefs[Keys.model] = model.trim()
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

    suspend fun setProjectSourceSandbox() {
        context.settingsDataStore.edit { prefs -> prefs[Keys.projectMode] = "sandbox" }
    }

    suspend fun setProjectSourceAttached(treeUri: String, displayName: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.projectMode] = "attached"
            prefs[Keys.attachedTreeUri] = treeUri
            prefs[Keys.attachedDisplayName] = displayName
        }
    }

    // ---------- Keystore AES/GCM encryption for the API key ----------

    private fun getOrCreateKey(): SecretKey {
        val alias = "nexus_forge_api_key"
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        if (value.isEmpty()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val packed = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        if (encoded.isEmpty()) return ""
        return try {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            require(packed.size > 12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, packed.copyOfRange(0, 12)))
            String(cipher.doFinal(packed.copyOfRange(12, packed.size)), Charsets.UTF_8)
        } catch (_: Exception) { "" }
    }

    companion object {
        /** Presets shown as tappable chips in Settings — a shortcut, never a requirement. */
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
