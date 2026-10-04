package com.example.sanpoguide.settings

import android.content.Context
import androidx.core.content.edit
import com.example.sanpoguide.guide.Provider
import com.example.sanpoguide.station.StationOverrides
import com.example.sanpoguide.station.format.GuideLength
import com.example.sanpoguide.station.format.SoundChoice
import com.example.sanpoguide.station.format.SpotKind
import com.example.sanpoguide.station.format.TalkEventKind
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
    /** The channel in use (docs/channels.md); how chatty the companion is comes with it. */
    val stationId: String = "standard",
    /** The user's changes to built-in channels, by channel id. */
    val stationOverrides: Map<String, StationOverrides> = emptyMap(),
    val thresholds: Thresholds = Thresholds(),
    /** Screen colors and the header picture follow the mood. The companion's tone is the channel's. */
    val moodEnabled: Boolean = true,
    /** Background sound during walk mode; off unless the user asks for it. */
    val ambientEnabled: Boolean = false,
    /** Percent. */
    val ambientVolume: Int = 40,
    /** Play only through earphones, never out loud from the phone's speaker. */
    val ambientEarphonesOnly: Boolean = true,
    /** Wikimedia Commons photos of spots in the guide sheet. */
    val spotPhotos: Boolean = true,
    /** Download photos on mobile data too, not only on Wi-Fi. */
    val photosOnMobileData: Boolean = false,
    /**
     * Send coordinates (the spot's, the user's and the route walked) to the AI service.
     * Off by default: without it the AI gets names, distances and figures only.
     */
    val shareLocationWithAi: Boolean = false,
    val mapStyle: MapStyle = MapStyle.GSI_STANDARD,
    /** The user's own Google Maps Platform key (Map Tiles API), for the Google map styles. */
    val googleMapsApiKey: String = "",
    /**
     * For publishers trying their channel before review: test tickets can be read (API-002).
     * Turned on by tapping the version seven times; saved at once, not with the rest of the settings.
     */
    val developerMode: Boolean = false,
) {
    fun apiKey(p: Provider = provider): String = apiKeys[p].orEmpty()
    fun model(p: Provider = provider): String = models[p]?.takeIf { it.isNotBlank() } ?: p.defaultModel
    fun baseUrl(p: Provider = provider): String = if (p == Provider.CUSTOM) customBaseUrl else p.defaultBaseUrl

    val isConfigured: Boolean
        get() = apiKey().isNotBlank() && model().isNotBlank() &&
            (provider == Provider.CLAUDE || baseUrl().isNotBlank())
}

/** Persists [GuideSettings]; API keys (AI and Google Maps) are stored encrypted with [KeyCipher]. */
class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("guide_settings", Context.MODE_PRIVATE)

    init {
        migrateToStations()
    }

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<GuideSettings> = _settings.asStateFlow()

    /** Saved at once, apart from [save]'s draft, which leaves it as it is. */
    fun setDeveloperMode(on: Boolean) {
        prefs.edit { putBoolean(KEY_DEVELOPER_MODE, on) }
        _settings.value = load()
    }

    fun save(settings: GuideSettings) {
        prefs.edit {
            putString(KEY_PROVIDER, settings.provider.name)
            putString(KEY_CUSTOM_BASE_URL, settings.customBaseUrl.trim())
            putString(KEY_STATION, settings.stationId)
            prefs.all.keys.filter { it.startsWith(STATION_PREFIX) }.forEach { remove(it) }
            settings.stationOverrides.forEach { (id, o) ->
                o.talkLevel?.let { putString(stationPref(id, FIELD_TALK_LEVEL), it.name) }
                o.moodTone?.let { putBoolean(stationPref(id, FIELD_MOOD_TONE), it) }
                o.events?.let { putString(stationPref(id, FIELD_EVENTS), OverrideCodec.encode(it)) }
                o.guideLength?.let { putString(stationPref(id, FIELD_GUIDE_LENGTH), it.json) }
                o.sound?.let { putString(stationPref(id, FIELD_SOUND), it.json) }
                o.prefer?.let { putString(stationPref(id, FIELD_PREFER), OverrideCodec.encode(it)) }
            }
            putBoolean(KEY_MOOD, settings.moodEnabled)
            putBoolean(KEY_AMBIENT, settings.ambientEnabled)
            putInt(KEY_AMBIENT_VOLUME, settings.ambientVolume.coerceIn(0, 100))
            putBoolean(KEY_AMBIENT_EARPHONES_ONLY, settings.ambientEarphonesOnly)
            putBoolean(KEY_SPOT_PHOTOS, settings.spotPhotos)
            putBoolean(KEY_PHOTOS_ON_MOBILE, settings.photosOnMobileData)
            putBoolean(KEY_SHARE_LOCATION_WITH_AI, settings.shareLocationWithAi)
            putString(KEY_MAP_STYLE, settings.mapStyle.name)
            val mapsKey = settings.googleMapsApiKey.trim()
            if (mapsKey.isEmpty()) remove(KEY_GOOGLE_MAPS_KEY) else putString(KEY_GOOGLE_MAPS_KEY, KeyCipher.encrypt(mapsKey))
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
            stationId = prefs.getString(KEY_STATION, null) ?: DEFAULTS.stationId,
            stationOverrides = loadStationOverrides(),
            thresholds = Thresholds(
                Threshold.entries.filter { prefs.contains(thresholdPref(it)) }
                    .associateWith { prefs.getInt(thresholdPref(it), it.default) },
            ),
            moodEnabled = prefs.getBoolean(KEY_MOOD, DEFAULTS.moodEnabled),
            ambientEnabled = prefs.getBoolean(KEY_AMBIENT, DEFAULTS.ambientEnabled),
            ambientVolume = prefs.getInt(KEY_AMBIENT_VOLUME, DEFAULTS.ambientVolume),
            ambientEarphonesOnly = prefs.getBoolean(KEY_AMBIENT_EARPHONES_ONLY, DEFAULTS.ambientEarphonesOnly),
            spotPhotos = prefs.getBoolean(KEY_SPOT_PHOTOS, DEFAULTS.spotPhotos),
            photosOnMobileData = prefs.getBoolean(KEY_PHOTOS_ON_MOBILE, DEFAULTS.photosOnMobileData),
            shareLocationWithAi = prefs.getBoolean(KEY_SHARE_LOCATION_WITH_AI, DEFAULTS.shareLocationWithAi),
            mapStyle = prefs.getString(KEY_MAP_STYLE, null)
                ?.let { name -> MapStyle.entries.firstOrNull { it.name == name } }
                ?: DEFAULTS.mapStyle,
            googleMapsApiKey = prefs.getString(KEY_GOOGLE_MAPS_KEY, null)?.let(KeyCipher::decrypt).orEmpty(),
            developerMode = prefs.getBoolean(KEY_DEVELOPER_MODE, DEFAULTS.developerMode),
        )
    }

    private fun loadStationOverrides(): Map<String, StationOverrides> {
        val all = prefs.all
        val ids = all.keys.filter { it.startsWith(STATION_PREFIX) }
            .mapNotNull { key -> FIELDS.firstOrNull { key.endsWith("_$it") }?.let { key.removePrefix(STATION_PREFIX).removeSuffix("_$it") } }
            .toSet()
        return ids.associateWith { id ->
            StationOverrides(
                talkLevel = (all[stationPref(id, FIELD_TALK_LEVEL)] as? String)
                    ?.let { name -> TalkLevel.entries.firstOrNull { it.name == name } },
                moodTone = all[stationPref(id, FIELD_MOOD_TONE)] as? Boolean,
                events = OverrideCodec.decodeSet<TalkEventKind>(all[stationPref(id, FIELD_EVENTS)] as? String),
                guideLength = OverrideCodec.decode<GuideLength>(all[stationPref(id, FIELD_GUIDE_LENGTH)] as? String),
                sound = OverrideCodec.decode<SoundChoice>(all[stationPref(id, FIELD_SOUND)] as? String),
                prefer = OverrideCodec.decodeSet<SpotKind>(all[stationPref(id, FIELD_PREFER)] as? String),
            )
        }.filterValues { !it.isEmpty }
    }

    /**
     * Before channels, how chatty the companion was and whether its tone followed the mood were
     * app-wide settings. They become the standard channel's, once (docs/channels.md「今の設定の移行」).
     */
    private fun migrateToStations() {
        if (!prefs.contains(KEY_TALK_LEVEL)) return
        val moved = StationMigration.fromLegacy(
            talkLevel = prefs.getString(KEY_TALK_LEVEL, null),
            moodEnabled = prefs.getBoolean(KEY_MOOD, DEFAULTS.moodEnabled),
        )
        prefs.edit {
            remove(KEY_TALK_LEVEL)
            moved.talkLevel?.let { putString(stationPref(STANDARD, FIELD_TALK_LEVEL), it.name) }
            moved.moodTone?.let { putBoolean(stationPref(STANDARD, FIELD_MOOD_TONE), it) }
        }
    }

    private fun stationPref(id: String, field: String) = "$STATION_PREFIX${id}_$field"
    private fun apiKeyPref(p: Provider) = "api_key_${p.name}"
    private fun thresholdPref(t: Threshold) = "threshold_${t.name}"
    private fun modelPref(p: Provider) = "model_${p.name}"

    private companion object {
        const val KEY_PROVIDER = "provider"
        const val KEY_CUSTOM_BASE_URL = "custom_base_url"
        /** Before channels; moved to the standard channel by [migrateToStations]. */
        const val KEY_TALK_LEVEL = "talk_level"
        const val KEY_STATION = "selected_station"
        /** `station_{id}_{field}`: the user's changes to a built-in channel. */
        const val STATION_PREFIX = "station_"
        const val FIELD_TALK_LEVEL = "talk_level"
        const val FIELD_MOOD_TONE = "mood_tone"
        const val FIELD_EVENTS = "events"
        const val FIELD_GUIDE_LENGTH = "guide_length"
        const val FIELD_SOUND = "sound"
        const val FIELD_PREFER = "prefer"
        val FIELDS = listOf(FIELD_TALK_LEVEL, FIELD_MOOD_TONE, FIELD_EVENTS, FIELD_GUIDE_LENGTH, FIELD_SOUND, FIELD_PREFER)
        const val STANDARD = "standard"
        const val KEY_MOOD = "mood"
        const val KEY_AMBIENT = "ambient"
        const val KEY_AMBIENT_VOLUME = "ambient_volume"
        const val KEY_AMBIENT_EARPHONES_ONLY = "ambient_earphones_only"
        const val KEY_SPOT_PHOTOS = "spot_photos"
        const val KEY_PHOTOS_ON_MOBILE = "photos_on_mobile"
        const val KEY_SHARE_LOCATION_WITH_AI = "share_location_with_ai"
        const val KEY_MAP_STYLE = "map_style"
        const val KEY_GOOGLE_MAPS_KEY = "google_maps_api_key"
        const val KEY_DEVELOPER_MODE = "developer_mode"
        val DEFAULTS = GuideSettings()
    }
}
