package jp.odds.service.claude

import jp.odds.dto.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicReference

/**
 * Routes a prompt to whichever provider is currently selected. `claude.provider` in
 * application.properties sets the starting point; the UI can flip it at runtime, which lives in
 * memory only and resets on restart.
 */
@Service
class ClaudeService(
    providers: List<ClaudeProvider>,
    @Value("\${claude.provider:CLI}")
    private val configuredDefault: ClaudeProviderType,
    @Value("\${claude.cli.model:}")
    private val cliModel: String,
    @Value("\${claude.api.model:claude-opus-5}")
    private val apiModel: String
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val byType: Map<ClaudeProviderType, ClaudeProvider> = providers.associateBy { it.type }

    private val active = AtomicReference(configuredDefault)

    fun status(): ClaudeStatusResponse = ClaudeStatusResponse(
        active = active.get(),
        configuredDefault = configuredDefault,
        model = modelLabel(active.get()),
        providers = ClaudeProviderType.entries.map { type ->
            val registered = byType[type]
                ?: return@map ClaudeProviderInfo(type, false, "Provider $type is not registered")
            val reason = registered.unavailableReason()
            ClaudeProviderInfo(type = type, available = reason == null, unavailableReason = reason)
        }
    )

    /** Rejects a provider that cannot serve requests, so the toggle never lands somewhere dead. */
    fun setProvider(type: ClaudeProviderType): ClaudeStatusResponse {
        provider(type).unavailableReason()?.let { throw IllegalArgumentException(it) }
        active.set(type)
        log.info("Claude provider switched to {}", type)
        return status()
    }

    suspend fun ask(request: ClaudeAskRequest): ClaudeAskResponse {
        require(request.prompt.isNotBlank()) { "Prompt must not be blank" }
        val chosen = request.provider ?: active.get()
        return provider(chosen).ask(request.prompt.trim())
    }

    private fun provider(type: ClaudeProviderType): ClaudeProvider =
        byType[type] ?: throw IllegalArgumentException("No Claude provider registered for $type")

    private fun modelLabel(type: ClaudeProviderType): String = when (type) {
        ClaudeProviderType.CLI -> cliModel.ifBlank { "CLI default" }
        ClaudeProviderType.API -> apiModel
    }
}
