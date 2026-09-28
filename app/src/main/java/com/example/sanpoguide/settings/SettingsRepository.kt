package com.example.sanpoguide.settings

import android.content.Context
import androidx.core.content.edit
import com.example.sanpoguide.guide.Provider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which AI service to use, with per-provider keys and models so switching back keeps them,
 * plus how often the walking companion speaks up and the limits for its safety and amenity remarks.
 */
data class GuideSettings(
    val provider: Provider = Provider.CLAUDE,
    val apiKeys: Map<Provider, String> = emptyMap(),
    val models: Map<Provider, String> = emptyMap(),
    val customBaseUrl: String = "",
    val talkLevel: TalkLevel = TalkLevel.NORMAL,
    val thresholds: Thresholds = Thresholds(),
) {
    fun apiKey(p: Provider = provider): String = apiKeys[p].orEmpty()
    fun model(p: Provider = provider): String = models[p]?.takeIf { it.isNotBlank() } ?: p.defaultModel
    fun baseUrl(p: Provider = provider): String = if (p == Provider.CUSTOM) customBaseUrl else p.defaultBaseUrl

    val isConfigured: Boolean
        get() = apiKey().isNotBlank() && model().isNotBlank() &&
            (provider == Provider.CLAUDE || baseUrl().isNotBlank())
}

/** Persists [GuideSettings]; API keys are stored encrypted with [KeyCipher]. */
class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("guide_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<GuideSettings> = _settings.asStateFlow()

    fun save(settings: GuideSettings) {
        prefs.edit {
            putString(KEY_PROVIDER, settings.provider.name)
            putString(KEY_CUSTOM_BASE_URL, settings.customBaseUrl.trim())
            putString(KEY_TALK_LEVEL, settings.talkLevel.name)
            Provider.entries.forEach { p ->
                val key = settings.apiKeys[p]?.trim().orEmpty()
                if (key.isEmpty()) remove(apiKeyPref(p)) else putString(apiKeyPref(p), KeyCipher.encrypt(key))
                val model = settings.models[p]?.trim().orEmpty()
                if (model.isEmpty()) remove(modelPref(p)) else putString(modelPref(p), model)
            }
            // Only overrides are stored, so a changed default in a later version still applies.
            Threshold.entries.forEach { t ->
                val value = settings.thresholds.values[t]
                if (value == null || value == t.default || !t.isValid(value)) remove(thresholdPref(t))
                else putInt(thresholdPref(t), value)
            }
        }
        _settings.value = load()
    }

    private fun load(): GuideSettings {
        val provider = prefs.getString(KEY_PROVIDER, null)
            ?.let { name -> Provider.entries.firstOrNull { it.name == name } }
            ?: Provider.CLAUDE
        return GuideSettings(
            provider = provider,
            apiKeys = Provider.entries.mapNotNull { p ->
                prefs.getString(apiKeyPref(p), null)?.let(KeyCipher::decrypt)?.let { p to it }
            }.toMap(),
            models = Provider.entries.mapNotNull { p ->
                prefs.getString(modelPref(p), null)?.let { p to it }
            }.toMap(),
            customBaseUrl = prefs.getString(KEY_CUSTOM_BASE_URL, "").orEmpty(),
            talkLevel = prefs.getString(KEY_TALK_LEVEL, null)
                ?.let { name -> TalkLevel.entries.firstOrNull { it.name == name } }
                ?: TalkLevel.NORMAL,
            thresholds = Thresholds(
                Threshold.entries.filter { prefs.contains(thresholdPref(it)) }
                    .associateWith { prefs.getInt(thresholdPref(it), it.default) },
            ),
        )
    }

    private fun apiKeyPref(p: Provider) = "api_key_${p.name}"
    private fun thresholdPref(t: Threshold) = "threshold_${t.name}"
    private fun modelPref(p: Provider) = "model_${p.name}"

    private companion object {
        const val KEY_PROVIDER = "provider"
        const val KEY_CUSTOM_BASE_URL = "custom_base_url"
        const val KEY_TALK_LEVEL = "talk_level"
    }
}
