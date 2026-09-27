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
import com.example.sanpoguide.settings.TalkLevel
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
}
