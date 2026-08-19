package jp.odds.dto

/** How a Claude request is served: the locally installed CLI, or the Anthropic API with a key. */
enum class ClaudeProviderType { CLI, API }

data class ClaudeAskRequest(
    val prompt: String,
    /** Overrides the active provider for this one request; null uses whatever the toggle says. */
    val provider: ClaudeProviderType? = null
)

data class ClaudeAskResponse(
    val text: String,
    val provider: ClaudeProviderType,
    val model: String?,
    val durationMs: Long,
    /** Only the CLI reports this; the API bills against the key instead. */
    val costUsd: Double? = null
)

data class ClaudeProviderInfo(
    val type: ClaudeProviderType,
    val available: Boolean,
    /** Why it cannot serve a request, for the UI to show; null when available. */
    val unavailableReason: String?
)

data class ClaudeStatusResponse(
    val active: ClaudeProviderType,
    val configuredDefault: ClaudeProviderType,
    val model: String,
    val providers: List<ClaudeProviderInfo>
)

data class SetClaudeProviderRequest(val provider: ClaudeProviderType)

data class MatchPredictionRequest(
    val eventId: Long,
    /** "football" or "handball"; matched case-insensitively against SportType. */
    val sport: String,
    val provider: ClaudeProviderType? = null
)

data class MatchPredictionResponse(
    val eventId: Long,
    val homeTeam: String,
    val awayTeam: String,
    val statusDescription: String,
    val isLive: Boolean,
    /** True when live statistics were available and fed into the prompt. */
    val hasStatistics: Boolean,
    val prediction: String,
    /** The facts the prediction was built from, so the UI can show what Claude actually saw. */
    val contextUsed: String,
    val provider: ClaudeProviderType,
    val model: String?,
    val durationMs: Long,
    val costUsd: Double?
)
