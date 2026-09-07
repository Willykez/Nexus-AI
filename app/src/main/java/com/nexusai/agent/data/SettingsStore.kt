package com.nexusai.agent.data

import android.content.Context
import android.content.SharedPreferences

/** Minimal persistence layer for provider connection settings. */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("nexus_ai_settings", Context.MODE_PRIVATE)

    fun load(): ProviderConfig = ProviderConfig(
        baseUrl = prefs.getString(KEY_BASE_URL, ProviderConfig().baseUrl) ?: ProviderConfig().baseUrl,
        apiKey = prefs.getString(KEY_API_KEY, "") ?: "",
        model = prefs.getString(KEY_MODEL, ProviderConfig().model) ?: ProviderConfig().model
    )

    fun save(config: ProviderConfig) {
        prefs.edit()
            .putString(KEY_BASE_URL, config.baseUrl)
            .putString(KEY_API_KEY, config.apiKey)
            .putString(KEY_MODEL, config.model)
            .apply()
    }

    companion object {
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
    }
}
