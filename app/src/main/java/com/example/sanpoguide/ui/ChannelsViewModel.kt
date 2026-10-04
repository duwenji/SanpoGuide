package com.example.sanpoguide.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sanpoguide.SanpoApp
import com.example.sanpoguide.station.remote.ChannelNotice
import com.example.sanpoguide.station.remote.ChannelSyncException
import com.example.sanpoguide.station.remote.ProviderView
import com.example.sanpoguide.station.remote.TrialChannel
import com.example.sanpoguide.walk.WalkService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The providers the user added and the channels they deliver (API-002 設定画面の提供元). */
class ChannelsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SanpoApp
    private val channels = app.channels

    val providers: StateFlow<List<ProviderView>> = channels.providers
    val notices: StateFlow<List<ChannelNotice>> = channels.notices
    val busy: StateFlow<Boolean> = channels.busy
    val walking: StateFlow<Boolean> = WalkService.walking
    val trials: StateFlow<List<TrialChannel>> = channels.trials
    val developerMode: StateFlow<Boolean> = app.settings.settings.map { it.developerMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, app.settings.settings.value.developerMode)

    private val _message = MutableStateFlow<String?>(null)
    /** The outcome of the last action that failed, in the user's words. */
    val message: StateFlow<String?> = _message.asStateFlow()

    fun addProvider(url: String, providerId: String, onDone: () -> Unit) = act { channels.addProvider(url, providerId); onDone() }
    fun removeProvider(id: String) = act { channels.removeProvider(id) }
    fun setEnabled(id: String, on: Boolean) = act { channels.setEnabled(id, on) }
    fun refresh() = act { channels.refreshNow() }

    /** Takes the channel onto the device and makes it the one in use. */
    fun use(providerId: String, channelId: String) = act {
        channels.install(providerId, channelId)
        app.settings.save(app.settings.settings.value.copy(stationId = "$providerId/$channelId"))
    }

    fun update(providerId: String, channelId: String) = act { channels.install(providerId, channelId) }
    fun remove(providerId: String, channelId: String) = act { channels.uninstall(providerId, channelId) }
    fun dismissNotices() = act { channels.dismissNotices() }

    /** A test ticket from the QR code: the trial channel becomes the one in use. */
    fun loadTicket(text: String) = act {
        val id = channels.loadTicket(text)
        app.settings.save(app.settings.settings.value.copy(stationId = id))
    }

    fun removeTrial(providerId: String, channelId: String) = act { channels.removeTrial(providerId, channelId) }

    fun showMessage(text: String) {
        _message.value = text
    }
    fun clearMessage() {
        _message.value = null
    }

    private fun act(work: suspend () -> Unit) {
        _message.value = null
        viewModelScope.launch {
            try {
                work()
            } catch (e: ChannelSyncException) {
                _message.value = e.message
            }
        }
    }
}
