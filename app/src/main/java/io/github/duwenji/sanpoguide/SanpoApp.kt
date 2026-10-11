package io.github.duwenji.sanpoguide

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import io.github.duwenji.sanpoguide.companion.WalkCompanion
import io.github.duwenji.sanpoguide.companion.CompanionFeed
import io.github.duwenji.sanpoguide.companion.WeatherClient
import io.github.duwenji.sanpoguide.data.GoogleMapTiles
import io.github.duwenji.sanpoguide.data.OverpassClient
import io.github.duwenji.sanpoguide.data.RouteClient
import io.github.duwenji.sanpoguide.data.SpotPhotos
import io.github.duwenji.sanpoguide.data.SpotRepository
import io.github.duwenji.sanpoguide.guide.GuideRepository
import io.github.duwenji.sanpoguide.guide.Speaker
import io.github.duwenji.sanpoguide.history.HistoryStore
import io.github.duwenji.sanpoguide.mood.MoodSource
import io.github.duwenji.sanpoguide.prompt.PromptTemplates
import io.github.duwenji.sanpoguide.prompt.Prompts
import io.github.duwenji.sanpoguide.station.BuiltInStations
import io.github.duwenji.sanpoguide.station.StationRepository
import io.github.duwenji.sanpoguide.station.remote.ThirdPartyChannels
import io.github.duwenji.sanpoguide.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration

class SanpoApp : Application() {
    /** For work that must outlive a screen or the walk service, such as the end-of-walk summary. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var prompts: PromptTemplates
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var spots: SpotRepository
        private set
    lateinit var stations: StationRepository
        private set
    /** Third parties' channels from the providers the user added (ADR-001 T-5). */
    lateinit var channels: ThirdPartyChannels
        private set
    lateinit var guides: GuideRepository
        private set
    lateinit var speaker: Speaker
        private set
    lateinit var history: HistoryStore
        private set
    lateinit var companion: WalkCompanion
        private set
    lateinit var mood: MoodSource
        private set
    lateinit var photos: SpotPhotos
        private set
    val weather = WeatherClient()
    val routes = RouteClient()
    val googleTiles = GoogleMapTiles()
    val feed = CompanionFeed()

    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().userAgentValue = "SanpoGuide/${BuildConfig.VERSION_NAME} (Android)"

        settings = SettingsRepository(this)
        // The channel decides which shops to search for; read when searching, after start-up.
        spots = SpotRepository(OverpassClient(), shops = { stations.current.value.searchedKinds })
        prompts = Prompts.fromResources()
        val developerMode = settings.settings.map { it.developerMode }.stateIn(appScope, SharingStarted.Eagerly, settings.settings.value.developerMode)
        channels = ThirdPartyChannels(this, BuiltInStations.standardPackage(), appScope, developerMode)
        stations = StationRepository(BuiltInStations.load(), settings.settings, appScope, channels.stations)
        channels.refreshIfDue()
        appScope.launch {
            // A switch to or from a channel that prefers shops shows them, or clears them, right away.
            stations.current.map { it.searchedKinds }.distinctUntilChanged().drop(1).collect {
                try {
                    spots.onShopsChanged()
                } catch (e: Exception) {
                    Log.w("SanpoApp", "Spot search after a channel switch failed", e)
                }
            }
        }
        guides = GuideRepository(settings, prompts, station = { stations.current.value })
        speaker = Speaker(this)
        photos = SpotPhotos(this)
        history = HistoryStore(this)
        mood = MoodSource(spots, feed.weather, appScope)
        companion = WalkCompanion(
            guides, history, prompts,
            station = { stations.current.value },
            mood = { mood.mood.value.takeIf { stations.current.value.moodTone } },
            shareLocation = { settings.settings.value.shareLocationWithAi },
        )
        appScope.launch { history.load() }

        getSystemService(NotificationManager::class.java).apply {
            createNotificationChannel(
                NotificationChannel(CHANNEL_WALK, "散策モード", NotificationManager.IMPORTANCE_LOW)
            )
            createNotificationChannel(
                NotificationChannel(CHANNEL_SPOT, "スポット案内", NotificationManager.IMPORTANCE_HIGH)
            )
            // High importance keeps the default sound and vibration.
            createNotificationChannel(
                NotificationChannel(CHANNEL_ALERT, "雷雨の予報", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "散策中に雷雨が近づいたとき、音で知らせます"
                }
            )
        }
    }

    companion object {
        const val CHANNEL_WALK = "walk"
        const val CHANNEL_SPOT = "spot"
        const val CHANNEL_ALERT = "weather_alert"
    }
}
