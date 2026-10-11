package io.github.duwenji.sanpoguide.guide

/** LLM services the guide can use. Everything except Claude speaks the OpenAI chat API. */
enum class Provider(
    val label: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val keyPageUrl: String,
) {
    CLAUDE("Claude (Anthropic)", "", "claude-opus-5", "https://console.anthropic.com/settings/keys"),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com", "deepseek-chat", "https://platform.deepseek.com/api_keys"),
    OPENAI("OpenAI", "https://api.openai.com/v1", "gpt-5-mini", "https://platform.openai.com/api-keys"),
    GEMINI(
        "Gemini (Google)", "https://generativelanguage.googleapis.com/v1beta/openai",
        "gemini-2.5-flash", "https://aistudio.google.com/apikey",
    ),
    CUSTOM("カスタム（OpenAI互換）", "", "", ""),
    ;

    companion object {
        /** Claude models offered in settings; the first is the default. */
        val CLAUDE_MODELS = listOf("claude-opus-5", "claude-sonnet-5", "claude-haiku-4-5")
    }
}

/** A text-generation backend. */
interface LlmClient {
    suspend fun generate(system: String, user: String): String
}

/** A failure with a message that can be shown to the user as-is. */
class GuideException(message: String, cause: Throwable? = null) : Exception(message, cause)

internal fun messageForStatus(status: Int, detail: String?): String = when (status) {
    401 -> "APIキーが正しくありません"
    402 -> "APIの残高・支払い設定を確認してください"
    403 -> "このAPIキーでは利用が許可されていません"
    404 -> "モデル名または接続先URLが見つかりません"
    429 -> "リクエストが多すぎます。しばらく待ってから再度お試しください"
    in 500..599 -> "AIサービス側でエラーが発生しました（HTTP $status）"
    else -> "AIサービスのエラー（HTTP $status）" + (detail?.let { ": $it" } ?: "")
}
