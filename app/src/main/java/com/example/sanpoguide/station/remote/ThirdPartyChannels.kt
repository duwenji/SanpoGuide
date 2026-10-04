package com.example.sanpoguide.station.remote

import android.content.Context
import android.net.ConnectivityManager
import com.example.sanpoguide.BuildConfig
import com.example.sanpoguide.station.Station
import com.example.sanpoguide.station.format.StationPackage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

/** [ProviderHttp] over OkHttp: no location or user data in the request (API-002 位置情報), and a byte limit. */
class OkHttpProviderHttp(private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()) : ProviderHttp {
    override fun get(url: String, maxBytes: Int): ByteArray {
        client.newCall(Request.Builder().url(url).header("User-Agent", "SanpoGuide/${BuildConfig.VERSION_NAME}").build()).execute().use { res ->
            if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
            val body = res.body ?: throw IOException("empty response")
            if (body.contentLength() > maxBytes) throw TooLargeException(url)
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            body.byteStream().use { input ->
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    if (out.size() > maxBytes) throw TooLargeException(url)
                }
            }
            return out.toByteArray()
        }
    }
}

/**
 * Runs [ChannelSync] on Android: one operation at a time off the main thread, and what it holds as
 * flows for the screens. Lists are fetched at start-up and when a walk starts, if a day has passed;
 * never during a walk (API-002 取得の時期).
 */
class ThirdPartyChannels(context: Context, private val standard: StationPackage, private val scope: CoroutineScope) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val sync = ChannelSync(
        store = ChannelStore(File(context.filesDir, "channels")),
        http = OkHttpProviderHttp(),
        verifier = TinkEd25519,
        appVersion = BuildConfig.VERSION_CODE,
        now = Instant::now,
        allowLocalHttp = BuildConfig.DEBUG,
    )
    private val lock = Mutex()

    private val _stations = MutableStateFlow<List<Station>>(emptyList())
    /** The third-party channels usable now. */
    val stations: StateFlow<List<Station>> = _stations.asStateFlow()

    private val _providers = MutableStateFlow<List<ProviderView>>(emptyList())
    val providers: StateFlow<List<ProviderView>> = _providers.asStateFlow()

    private val _notices = MutableStateFlow<List<ChannelNotice>>(emptyList())
    val notices: StateFlow<List<ChannelNotice>> = _notices.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        // Publish what is on the device before any fetching.
        scope.launch { exec { } }
    }

    /** At start-up and when a walk starts: fetch what is due (packages only on an unmetered network). */
    fun refreshIfDue() {
        scope.launch { exec { refreshDue(unmetered()) } }
    }

    suspend fun refreshNow() = exec { refreshAll(unmetered()) }

    /** Adds a provider; throws [ChannelSyncException] with the reason to show. */
    suspend fun addProvider(url: String, providerId: String) = exec { addProvider(url, providerId) }

    suspend fun removeProvider(providerId: String) = exec { removeProvider(providerId) }

    suspend fun setEnabled(providerId: String, enabled: Boolean) = exec { setEnabled(providerId, enabled) }

    suspend fun install(providerId: String, channelId: String) = exec { install(providerId, channelId) }

    suspend fun uninstall(providerId: String, channelId: String) = exec { uninstall(providerId, channelId) }

    suspend fun dismissNotices() = exec { dismissNotices() }

    /** Runs [work] alone on the IO dispatcher, then publishes the new state, even when [work] throws. */
    private suspend fun <T> exec(work: ChannelSync.() -> T): T = lock.withLock {
        _busy.value = true
        try {
            withContext(Dispatchers.IO) {
                try {
                    sync.work()
                } finally {
                    val stations = sync.stations(standard)
                    val providers = sync.providers()
                    _stations.value = stations
                    _providers.value = providers
                    _notices.value = sync.state.notices
                }
            }
        } finally {
            _busy.value = false
        }
    }

    private fun unmetered(): Boolean = connectivity?.isActiveNetworkMetered == false
}
