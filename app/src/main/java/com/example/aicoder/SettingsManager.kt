package com.example.aicoder

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.settingsStore by preferencesDataStore(name = "ai_coder_settings")

data class AiSettings(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val temperature: Float = 0.2f,
    val maxOutputTokens: Int = 8192,
    val filesToolEnabled: Boolean = true,
    val zipToolEnabled: Boolean = true
)

class SettingsManager(private val context: Context) {
    private object Keys {
        val baseUrl = stringPreferencesKey("base_url")
        val encryptedApiKey = stringPreferencesKey("encrypted_api_key")
        val model = stringPreferencesKey("model")
        val temperature = floatPreferencesKey("temperature")
        val maxTokens = intPreferencesKey("max_output_tokens")
        val filesTool = booleanPreferencesKey("files_tool_enabled")
        val zipTool = booleanPreferencesKey("zip_tool_enabled")
    }

    val settingsFlow: Flow<AiSettings> = context.settingsStore.data.map { prefs ->
        val baseUrl = prefs[Keys.baseUrl] ?: "https://api.openai.com/v1"
        val encrypted = prefs[profileKey(baseUrl)] ?: if (baseUrl == "https://api.openai.com/v1") prefs[Keys.encryptedApiKey] else null
        val apiKey = decrypt(encrypted.orEmpty())
        AiSettings(
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = prefs[profileModelKey(baseUrl)] ?: prefs[Keys.model] ?: "gpt-4o-mini",
            temperature = prefs[Keys.temperature] ?: 0.2f,
            maxOutputTokens = prefs[Keys.maxTokens] ?: 8192,
            filesToolEnabled = prefs[Keys.filesTool] ?: true,
            zipToolEnabled = prefs[Keys.zipTool] ?: true
        )
    }

    suspend fun saveSettings(baseUrl: String, apiKey: String?, modelName: String) {
        val normalized = baseUrl.trim().trimEnd('/')
        context.settingsStore.edit { prefs ->
            prefs[Keys.baseUrl] = normalized
            prefs[Keys.model] = modelName.trim()
            prefs[profileModelKey(normalized)] = modelName.trim()
            if (apiKey != null) {
                prefs[profileKey(normalized)] = encrypt(apiKey.trim())
            }
            // Migrate the legacy single-key slot only to its original OpenAI profile.
            if (normalized == "https://api.openai.com/v1" && prefs[Keys.encryptedApiKey] != null && prefs[profileKey(normalized)] == null) {
                prefs[profileKey(normalized)] = prefs[Keys.encryptedApiKey].orEmpty()
            }
            prefs.remove(Keys.encryptedApiKey)
        }
    }

    suspend fun getApiKeyForBaseUrl(baseUrl: String): String = context.settingsStore.data.map { prefs ->
        decrypt(prefs[profileKey(baseUrl)].orEmpty())
    }.first()

    suspend fun activateSession(baseUrl: String, modelName: String) {
        val normalized = baseUrl.trim().trimEnd('/')
        context.settingsStore.edit { prefs ->
            prefs[Keys.baseUrl] = normalized
            prefs[Keys.model] = modelName.trim()
            prefs[profileModelKey(normalized)] = modelName.trim()
        }
    }

    suspend fun saveGenerationConfig(temperature: Float, maxTokens: Int) {
        context.settingsStore.edit { prefs ->
            prefs[Keys.temperature] = temperature.coerceIn(0f, 1f)
            prefs[Keys.maxTokens] = maxTokens.coerceIn(1024, 16384)
        }
    }

    suspend fun saveCapabilities(filesEnabled: Boolean, zipEnabled: Boolean) {
        context.settingsStore.edit { prefs ->
            prefs[Keys.filesTool] = filesEnabled
            prefs[Keys.zipTool] = zipEnabled
        }
    }

    private fun profileKey(baseUrl: String) = stringPreferencesKey("api_key_${sha256(baseUrl)}")
    private fun profileModelKey(baseUrl: String) = stringPreferencesKey("model_${sha256(baseUrl)}")

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.trim().trimEnd('/').toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(32)

    private fun getKey(): SecretKey {
        val alias = "ai_coder_api_key"
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
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        val packed = cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        if (encoded.isEmpty()) return ""
        return try {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            require(packed.size > 12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, packed.copyOfRange(0, 12)))
            String(cipher.doFinal(packed.copyOfRange(12, packed.size)), StandardCharsets.UTF_8)
        } catch (_: Exception) { "" }
    }
}
