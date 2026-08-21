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

/** Claude's own percentages for the three outcomes, 0-100. Null where it did not give one. */
data class PredictionProbabilities(
    val home: Double?,
    val draw: Double?,
    val away: Double?
)

/** Decimal prices, either the market's or the ones Claude's percentages imply. */
data class PredictionOdds(
    val home: Double?,
    val draw: Double?,
    val away: Double?
)

data class MatchPredictionResponse(
    val eventId: Long,
    val homeTeam: String,
    val awayTeam: String,
    val statusDescription: String,
    val isLive: Boolean,
    /** True when live statistics were available and fed into the prompt. */
    val hasStatistics: Boolean,
    /** Team news headlines fed into the prompt; 0 when there were none to give. */
    val teamNewsHeadlines: Int,
    val prediction: String,
    /** The facts the prediction was built from, so the UI can show what Claude actually saw. */
    val contextUsed: String,
    val probabilities: PredictionProbabilities?,
    /** HOME, DRAW or AWAY - whichever probability is highest; null without percentages. */
    val predictedOutcome: String?,
    /** When this prediction was made, epoch seconds. */
    val predictedAt: Long,
    val provider: ClaudeProviderType,
    val model: String?,
    val durationMs: Long,
    val costUsd: Double?
)

/**
 * A prediction as it was stored, for the matches table to show on hover. Carries the odds it was
 * made against, so Claude's percentages can be read next to the market they were arguing with.
 */
data class StoredMatchPrediction(
    val eventId: Long,
    val sport: String,
    val predictedAt: Long,
    val statusDescription: String,
    val wasLive: Boolean,
    val hadStatistics: Boolean,
    val teamNewsHeadlines: Int,
    val probabilities: PredictionProbabilities?,
    val predictedOutcome: String?,
    val marketOdds: PredictionOdds?,
    val homeScore: Int?,
    val awayScore: Int?,
    val prediction: String,
    val provider: ClaudeProviderType,
    val model: String?
)
