package io.github.duwenji.sanpoguide.guide

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Claude via the official Anthropic SDK. */
class ClaudeClient(apiKey: String, private val model: String) : LlmClient {
    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .build()

    override suspend fun generate(system: String, user: String): String = withContext(Dispatchers.IO) {
        val params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(4000L)
            .system(system)
            .addUserMessage(user)
            .apply {
                // Short narration for a phone on the move: low effort keeps it quick and cheap.
                // Haiku 4.5 does not accept the effort parameter.
                if (!model.startsWith("claude-haiku")) {
                    outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                }
                // If Opus 5 declines a request, let the server retry on the recommended
                // fallback model instead of returning the refusal.
                if (model == "claude-opus-5") {
                    putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                    putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                }
            }
            .build()

        val message = try {
            client.messages().create(params)
        } catch (e: AnthropicServiceException) {
            throw GuideException(messageForStatus(e.statusCode(), e.message), e)
        } catch (e: AnthropicIoException) {
            throw GuideException("AIサービスに接続できませんでした", e)
        }
        if (message.stopReason().orElse(null) == StopReason.REFUSAL) {
            throw GuideException("このスポットの解説は用意できませんでした")
        }
        message.content()
            .mapNotNull { block -> block.text().orElse(null)?.text() }
            .joinToString("")
            .trim()
    }
}
