package jp.odds.service.claude

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.MessageCreateParams
import jp.odds.dto.ClaudeAskResponse
import jp.odds.dto.ClaudeProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Answers through the Anthropic API with an API key, which is what this becomes the day it runs
 * anywhere other than a laptop with the CLI logged in.
 *
 * Nothing needs configuring beyond exporting ANTHROPIC_API_KEY before starting the backend; the
 * client is built on first use so an absent key is a disabled toggle rather than a failed startup.
 */
@Component
class ClaudeApiProvider(
    @Value("\${claude.api.model:claude-opus-5}")
    private val model: String,
    @Value("\${claude.api.max-tokens:16000}")
    private val maxTokens: Long
) : ClaudeProvider {

    private val log = LoggerFactory.getLogger(javaClass)

    override val type = ClaudeProviderType.API

    private val client: AnthropicClient by lazy { AnthropicOkHttpClient.fromEnv() }

    override fun unavailableReason(): String? =
        if (!System.getenv("ANTHROPIC_API_KEY").isNullOrBlank()) null
        else "ANTHROPIC_API_KEY is not set. Export a key and restart the backend, or use the CLI provider."

    override suspend fun ask(prompt: String): ClaudeAskResponse = withContext(Dispatchers.IO) {
        unavailableReason()?.let { throw IllegalStateException(it) }

        val params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(maxTokens)
            .system(CLAUDE_SYSTEM_PROMPT)
            .addUserMessage(prompt)
            .build()

        log.debug("Calling Anthropic API with model {}", model)
        val startedAt = System.currentTimeMillis()
        val message = client.messages().create(params)

        val text = message.content()
            .mapNotNull { block -> block.text().orElse(null)?.text() }
            .joinToString("\n")
            .trim()

        if (text.isBlank()) {
            val stopReason = message.stopReason().map { it.toString() }.orElse("unknown")
            throw IllegalStateException("Claude returned no text (stop reason: $stopReason)")
        }

        ClaudeAskResponse(
            text = text,
            provider = type,
            model = message.model().toString(),
            durationMs = System.currentTimeMillis() - startedAt
        )
    }
}
