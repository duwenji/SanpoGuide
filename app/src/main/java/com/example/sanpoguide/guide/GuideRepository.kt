package com.example.sanpoguide.guide

import com.example.sanpoguide.data.Poi
import com.example.sanpoguide.prompt.PromptTemplates
import com.example.sanpoguide.prompt.Prompts
import com.example.sanpoguide.settings.GuideSettings
import com.example.sanpoguide.settings.SettingsRepository
import com.example.sanpoguide.station.Station
import java.util.concurrent.ConcurrentHashMap

/** Returns a narration for a spot, caching it so each spot is generated (and billed) once. */
class GuideRepository(
    private val settingsRepo: SettingsRepository,
    private val prompts: PromptTemplates,
    /** The channel in use; its slots and guide length shape the narration. */
    private val station: () -> Station,
) {
    private val cache = ConcurrentHashMap<String, String>()
    private var client: Pair<GuideSettings, LlmClient>? = null

    suspend fun guideFor(poi: Poi, distanceM: Int?): String {
        val settings = settingsRepo.settings.value
        // Without an API key, read out a plain summary built from the map tags.
        if (!settings.isConfigured) return prompts.render(Prompts.Fallback.GUIDE, GuidePrompt.fallbackVars(poi))
        val share = settings.shareLocationWithAi
        // Each channel narrates in its own way, so a narration made for one isn't reused by another.
        val channel = station()
        val cacheKey = "${settings.provider}/${settings.model()}/${channel.key}/${poi.id}/$share"
        cache[cacheKey]?.let { return it }
        val text = clientFor(settings).generate(
            prompts.render(Prompts.Guide.SYSTEM, channel.guideSystemVars()),
            prompts.render(Prompts.Guide.USER, GuidePrompt.spotVars(poi, distanceM, share)),
        )
        cache[cacheKey] = text
        return text
    }

    /** Free-form generation with the configured service; null when no AI is configured. */
    suspend fun chat(system: String, user: String): String? {
        val settings = settingsRepo.settings.value
        if (!settings.isConfigured) return null
        return clientFor(settings).generate(system, user)
    }

    @Synchronized
    private fun clientFor(settings: GuideSettings): LlmClient {
        client?.let { (s, c) -> if (s == settings) return c }
        return createClient(settings).also { client = settings to it }
    }

    companion object {
        fun createClient(settings: GuideSettings): LlmClient = when (settings.provider) {
            Provider.CLAUDE -> ClaudeClient(settings.apiKey(), settings.model())
            else -> OpenAiCompatibleClient(settings.baseUrl(), settings.apiKey(), settings.model())
        }
    }
}
