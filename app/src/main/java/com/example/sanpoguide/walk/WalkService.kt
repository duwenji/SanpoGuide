package com.example.sanpoguide.walk

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.sanpoguide.R
import com.example.sanpoguide.SanpoApp
import com.example.sanpoguide.companion.TalkEvent
import com.example.sanpoguide.companion.Utterance
import com.example.sanpoguide.companion.WalkSession
import com.example.sanpoguide.companion.Weather
import com.example.sanpoguide.data.Poi
import com.example.sanpoguide.history.SpotVisit
import com.example.sanpoguide.prompt.Prompts
import com.example.sanpoguide.ui.MainActivity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * Walk mode: tracks the walk in the foreground and lets the companion talk — a greeting,
 * a guide or a "welcome back" at spots, occasional small talk, and a summary at the end.
 * Each walk is saved to the history.
 */
class WalkService : LifecycleService() {
    private val app get() = application as SanpoApp
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private var session: WalkSession? = null
    private var lastLocation: Location? = null
    private var weather: Weather? = null
    private var weatherFetchedAt = 0L
    private var greeted = false

    /** One line at a time; events that arrive while the companion is talking wait for the next tick. */
    private val talking = Mutex()

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let(::onLocation)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (session == null) {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, walkingNotification("いってらっしゃい。近くに来たらお知らせします"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
            val s = WalkSession()
            session = s
            app.feed.startWalk(s)
            startLocationUpdates()
            startTicker()
            _walking.value = true
        }
        return START_STICKY
    }

    override fun onDestroy() {
        fused.removeLocationUpdates(callback)
        session?.let(::finishWalk)
        session = null
        _walking.value = false
        super.onDestroy()
    }

    @SuppressLint("MissingPermission") // Checked by MainActivity before starting the service.
    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L)
            .setMinUpdateDistanceMeters(10f)
            .build()
        fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    /** Location updates stop while the user stands still, so rests and time-based talk need a clock. */
    private fun startTicker() = lifecycleScope.launch {
        while (isActive) {
            delay(TICK_MS)
            lastLocation?.let { refreshWeather(it) }
            maybeTalk()
        }
    }

    private fun onLocation(location: Location) {
        val s = session ?: return
        lastLocation = location
        s.onLocation(location)
        app.feed.update(s)
        lifecycleScope.launch {
            if (!greeted) {
                greeted = true
                refreshWeather(location)
                talk { speakLine(app.companion.say(TalkEvent.Start, s, weather)) }
            }
            try {
                app.spots.onLocation(location)
            } catch (e: Exception) {
                Log.w(TAG, "Spot search failed", e)
            }
            maybeTalk()
        }
    }

    /** Decides whether anything is worth saying right now, most important first. */
    private suspend fun maybeTalk() {
        val s = session ?: return
        val location = lastLocation ?: return
        // Let the current line finish before starting another.
        if (app.speaker.isSpeaking) return
        val level = app.settings.settings.value.talkLevel
        val now = System.currentTimeMillis()

        // While the user stands still, don't run through every spot within reach one by one;
        // the spot they arrived at has been introduced, and a rest remark may be due instead.
        val stationary = s.restMinutes(now) >= STATIONARY_MINUTES
        if (!stationary && now - s.lastSpotTalkAt >= level.spotGapMs) {
            val spot = app.spots.spots.value
                .filter { poi -> poi.id !in s.talkedAbout && s.visits.none { it.matches(poi) } }
                .map { it to it.distanceFrom(location) }
                .filter { (_, d) -> d <= ANNOUNCE_RADIUS_M }
                .sortedWith(compareByDescending<Pair<Poi, Float>> { it.first.notability }.thenBy { it.second })
                .firstOrNull()
            if (spot != null) {
                talk {
                    s.lastSpotTalkAt = now
                    talkAboutSpot(s, spot.first, spot.second.toInt())
                }
                return
            }
        }

        if (now - s.lastSpokeAt < level.minGapMs) return
        val restMinutes = s.restMinutes(now)
        if (level.remarkOnRest && restMinutes >= REST_MINUTES && !s.restRemarked) {
            talk {
                s.markRestRemarked()
                speakLine(app.companion.say(TalkEvent.Rest(restMinutes), s, weather))
            }
            return
        }
        val walkedSinceMilestone = s.distanceM - s.lastMilestoneDistanceM
        if (walkedSinceMilestone >= level.milestoneDistanceM ||
            (now - s.lastMilestoneAt >= level.milestoneIntervalMs && walkedSinceMilestone > 100)
        ) {
            talk {
                s.lastMilestoneAt = now
                s.lastMilestoneDistanceM = s.distanceM
                speakLine(app.companion.say(TalkEvent.Milestone, s, weather))
                saveProgress(s)
            }
        }
    }

    private suspend fun talkAboutSpot(s: WalkSession, poi: Poi, distanceM: Int) {
        s.talkedAbout += poi.id
        val past = app.history.visitsTo(poi, excludingWalk = s.id)
        val text = if (past.isEmpty()) {
            try {
                app.guides.guideFor(poi, distanceM)
            } catch (e: Exception) {
                Log.w(TAG, "Guide generation failed", e)
                app.prompts.render(Prompts.Fallback.NEARBY, mapOf("name" to poi.name, "category" to poi.category))
            }
        } else {
            app.companion.say(TalkEvent.Revisit(poi, past), s, weather)
        }
        s.visits += SpotVisit(poi.id, poi.name, poi.category, System.currentTimeMillis(), text, poi.lat, poi.lon)
        app.feed.update(s)
        saveProgress(s)
        getSystemService(NotificationManager::class.java)
            .notify(SPOT_NOTIFICATION_ID, spotNotification(poi, text))
        speakLine(text, poi.name)
    }

    private fun speakLine(text: String, spotName: String? = null) {
        // A line that finishes generating after the walk ended is dropped, so it can't
        // re-post the ongoing walk notification.
        val s = session ?: return
        s.remember(text)
        app.feed.add(Utterance(System.currentTimeMillis(), text, spotName))
        app.speaker.speak(if (spotName != null) "$spotName。$text" else text)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, walkingNotification(text))
    }

    private inline fun talk(block: () -> Unit) {
        if (!talking.tryLock()) return
        try {
            block()
        } finally {
            talking.unlock()
        }
    }

    private suspend fun refreshWeather(location: Location) {
        val now = System.currentTimeMillis()
        if (now - weatherFetchedAt < WEATHER_REFRESH_MS) return
        weatherFetchedAt = now
        weather = try {
            app.weather.current(location.latitude, location.longitude)
        } catch (e: Exception) {
            Log.w(TAG, "Weather fetch failed", e)
            weather
        }
    }

    /** Saves the walk so far, in the app scope so a stopping service doesn't cut the write short. */
    private fun saveProgress(s: WalkSession) {
        val record = s.toRecord()
        app.appScope.launch { app.history.save(record) }
    }

    private fun finishWalk(s: WalkSession) {
        val record = s.toRecord()
        val lastWeather = weather
        app.feed.endWalk()
        if (record.durationMs < MIN_SAVED_WALK_MS && record.visits.isEmpty()) return
        app.appScope.launch {
            app.history.save(record)
            val line = app.companion.say(TalkEvent.Finish(record), null, lastWeather)
            app.feed.add(Utterance(System.currentTimeMillis(), line))
            app.speaker.speak(line)
        }
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun walkingNotification(text: String): Notification {
        val stop = PendingIntent.getService(
            this, 1, Intent(this, WalkService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, SanpoApp.CHANNEL_WALK)
            .setSmallIcon(R.drawable.ic_walk)
            .setContentTitle("散策中")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent())
            .addAction(0, "終了", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun spotNotification(poi: Poi, text: String): Notification =
        NotificationCompat.Builder(this, SanpoApp.CHANNEL_SPOT)
            .setSmallIcon(R.drawable.ic_walk)
            .setContentTitle("${poi.name}（${poi.category}）")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()

    companion object {
        private const val TAG = "WalkService"
        private const val NOTIFICATION_ID = 1
        // One slot for spot talks: each new spot replaces the last instead of piling up.
        private const val SPOT_NOTIFICATION_ID = 2
        private const val ACTION_STOP = "stop"
        private const val TICK_MS = 30_000L
        private const val WEATHER_REFRESH_MS = 30 * 60_000L
        private const val REST_MINUTES = 4
        private const val STATIONARY_MINUTES = 2
        private const val MIN_SAVED_WALK_MS = 60_000L
        const val ANNOUNCE_RADIUS_M = 60f

        private val _walking = MutableStateFlow(false)
        val walking: StateFlow<Boolean> = _walking.asStateFlow()

        fun start(context: Context) {
            context.startForegroundService(Intent(context, WalkService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WalkService::class.java))
        }
    }
}
