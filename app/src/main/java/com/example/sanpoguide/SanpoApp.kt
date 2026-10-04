package com.example.sanpoguide

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.example.sanpoguide.companion.WalkCompanion
import com.example.sanpoguide.companion.CompanionFeed
import com.example.sanpoguide.companion.WeatherClient
import com.example.sanpoguide.data.GoogleMapTiles
import com.example.sanpoguide.data.OverpassClient
import com.example.sanpoguide.data.RouteClient
import com.example.sanpoguide.data.SpotPhotos
import com.example.sanpoguide.data.SpotRepository
import com.example.sanpoguide.guide.GuideRepository
import com.example.sanpoguide.guide.Speaker
import com.example.sanpoguide.history.HistoryStore
import com.example.sanpoguide.mood.MoodSource
import com.example.sanpoguide.prompt.PromptTemplates
import com.example.sanpoguide.prompt.Prompts
import com.example.sanpoguide.station.BuiltInStations
import com.example.sanpoguide.station.StationRepository
import com.example.sanpoguide.station.remote.ThirdPartyChannels
import com.example.sanpoguide.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
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
        spots = SpotRepository(OverpassClient())
        prompts = Prompts.fromResources()
        val developerMode = settings.settings.map { it.developerMode }.stateIn(appScope, SharingStarted.Eagerly, settings.settings.value.developerMode)
        channels = ThirdPartyChannels(this, BuiltInStations.standardPackage(), appScope, developerMode)
        stations = StationRepository(BuiltInStations.load(), settings.settings, appScope, channels.stations)
        channels.refreshIfDue()
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
