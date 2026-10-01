package com.example.sanpoguide

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.example.sanpoguide.companion.WalkCompanion
import com.example.sanpoguide.companion.CompanionFeed
import com.example.sanpoguide.companion.WeatherClient
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
import com.example.sanpoguide.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
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
    val feed = CompanionFeed()

    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().userAgentValue = "SanpoGuide/${BuildConfig.VERSION_NAME} (Android)"

        settings = SettingsRepository(this)
        spots = SpotRepository(OverpassClient())
        prompts = Prompts.fromAssets(assets)
        guides = GuideRepository(settings, prompts)
        speaker = Speaker(this)
        photos = SpotPhotos(this)
        history = HistoryStore(this)
        mood = MoodSource(spots, feed.weather, appScope)
        companion = WalkCompanion(
            guides, history, prompts,
            mood = { mood.mood.value.takeIf { settings.settings.value.moodEnabled } },
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
