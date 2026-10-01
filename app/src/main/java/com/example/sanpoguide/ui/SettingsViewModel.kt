package com.example.sanpoguide.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sanpoguide.SanpoApp
import com.example.sanpoguide.guide.GuideException
import com.example.sanpoguide.guide.GuideRepository
import com.example.sanpoguide.guide.Provider
import com.example.sanpoguide.prompt.Prompts
import com.example.sanpoguide.settings.GuideSettings
import com.example.sanpoguide.settings.MapStyle
import com.example.sanpoguide.settings.TalkLevel
import com.example.sanpoguide.settings.Threshold
import com.example.sanpoguide.settings.Thresholds
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface TestState {
    data object Idle : TestState
    data object Running : TestState
    data class Success(val reply: String) : TestState
    data class Failed(val message: String) : TestState
}

/** Edits a draft of the AI settings; nothing is persisted until [save]. */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as SanpoApp).settings
    private val prompts = (application as SanpoApp).prompts

    private val _draft = MutableStateFlow(repo.settings.value)
    val draft: StateFlow<GuideSettings> = _draft.asStateFlow()

    private val _test = MutableStateFlow<TestState>(TestState.Idle)
    val test: StateFlow<TestState> = _test.asStateFlow()
    private var testJob: Job? = null

    fun reset() {
        _draft.value = repo.settings.value
        clearTest()
    }

    fun selectProvider(p: Provider) = edit { it.copy(provider = p) }
    fun setApiKey(key: String) = edit { it.copy(apiKeys = it.apiKeys + (it.provider to key)) }
    fun setModel(model: String) = edit { it.copy(models = it.models + (it.provider to model)) }
    fun setCustomBaseUrl(url: String) = edit { it.copy(customBaseUrl = url) }
    fun setTalkLevel(level: TalkLevel) = edit { it.copy(talkLevel = level) }
    fun setMoodEnabled(on: Boolean) = edit { it.copy(moodEnabled = on) }
    fun setAmbientEnabled(on: Boolean) = edit { it.copy(ambientEnabled = on) }
    fun setAmbientVolume(percent: Int) = edit { it.copy(ambientVolume = percent) }
    fun setAmbientEarphonesOnly(on: Boolean) = edit { it.copy(ambientEarphonesOnly = on) }
    fun setSpotPhotos(on: Boolean) = edit { it.copy(spotPhotos = on) }
    fun setPhotosOnMobileData(on: Boolean) = edit { it.copy(photosOnMobileData = on) }
    fun setShareLocationWithAi(on: Boolean) = edit { it.copy(shareLocationWithAi = on) }
    fun setMapStyle(style: MapStyle) = edit { it.copy(mapStyle = style) }
    fun setGoogleMapsApiKey(key: String) = edit { it.copy(googleMapsApiKey = key) }

    /** [value] null (blank or not a number) is kept as invalid, so the field shows an error. */
    fun setThreshold(t: Threshold, value: Int?) = edit { it.copy(thresholds = it.thresholds.with(t, value ?: INVALID)) }
    fun resetThresholds() = edit { it.copy(thresholds = Thresholds()) }

    fun save() = repo.save(_draft.value)

    fun runTest() {
        val settings = _draft.value
        if (!settings.isConfigured) {
            _test.value = TestState.Failed("APIキーとモデル名（カスタムの場合は接続先URLも）を入力してください")
            return
        }
        testJob?.cancel()
        testJob = viewModelScope.launch {
            _test.value = TestState.Running
            _test.value = try {
                val reply = GuideRepository.createClient(settings)
                    .generate(
                        prompts.render(Prompts.ConnectionTest.SYSTEM),
                        prompts.render(Prompts.ConnectionTest.USER),
                    )
                TestState.Success(reply.take(40))
            } catch (e: GuideException) {
                TestState.Failed(e.message.orEmpty())
            } catch (e: Exception) {
                TestState.Failed("接続に失敗しました: ${e.message}")
            }
        }
    }

    private fun edit(change: (GuideSettings) -> GuideSettings) {
        _draft.update(change)
        clearTest()
    }

    private fun clearTest() {
        testJob?.cancel()
        _test.value = TestState.Idle
    }

    companion object {
        /** Below every [Threshold.min]. */
        const val INVALID = -1
    }
}
