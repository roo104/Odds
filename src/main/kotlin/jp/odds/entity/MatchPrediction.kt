package jp.odds.entity

import jakarta.persistence.*
import jp.odds.dto.ClaudeProviderType
import java.time.Instant

/**
 * One Claude prediction, as it was made: the percentages, the prices it was made against and the
 * prose behind them. Rows are never updated - a new run is a new row, and the latest one for a
 * match is what the matches table shows on hover.
 */
@Entity
@Table(
    name = "match_predictions", indexes = [
        Index(name = "idx_prediction_event", columnList = "event_id, created_at"),
        Index(name = "idx_prediction_sport_start", columnList = "sport, start_timestamp")
    ]
)
class MatchPrediction(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "event_id", nullable = false)
    var eventId: Long = 0,

    @Column(name = "sport", nullable = false)
    @Enumerated(EnumType.STRING)
    var sport: SportType = SportType.Football,

    @Column(name = "start_timestamp", nullable = false)
    var startTimestamp: Long = 0,

    @Column(name = "home_team_name", nullable = false)
    var homeTeamName: String = "",

    @Column(name = "away_team_name", nullable = false)
    var awayTeamName: String = "",

    @Column(name = "status_description", nullable = false)
    var statusDescription: String = "",

    @Column(name = "was_live", nullable = false)
    var wasLive: Boolean = false,

    @Column(name = "had_statistics", nullable = false)
    var hadStatistics: Boolean = false,

    /** Team news headlines fed into the prompt; 0 when there were none to give. */
    @Column(name = "team_news_headlines", nullable = false)
    var teamNewsHeadlines: Int = 0,

    /** Claude's own percentages, 0-100; null when the answer came back without them. */
    @Column(name = "probability_home")
    var probabilityHome: Double? = null,

    @Column(name = "probability_draw")
    var probabilityDraw: Double? = null,

    @Column(name = "probability_away")
    var probabilityAway: Double? = null,

    /** HOME, DRAW or AWAY - whichever probability is highest. */
    @Column(name = "predicted_outcome")
    var predictedOutcome: String? = null,

    /** Decimal prices at the moment of the prediction, so drift since then is visible. */
    @Column(name = "odds_home")
    var oddsHome: Double? = null,

    @Column(name = "odds_draw")
    var oddsDraw: Double? = null,

    @Column(name = "odds_away")
    var oddsAway: Double? = null,

    @Column(name = "home_score")
    var homeScore: Int? = null,

    @Column(name = "away_score")
    var awayScore: Int? = null,

    @Column(name = "prediction", nullable = false, columnDefinition = "TEXT")
    var prediction: String = "",

    @Column(name = "provider", nullable = false)
    @Enumerated(EnumType.STRING)
    var provider: ClaudeProviderType = ClaudeProviderType.CLI,

    @Column(name = "model")
    var model: String? = null,

    @Column(name = "duration_ms", nullable = false)
    var durationMs: Long = 0,

    @Column(name = "cost_usd")
    var costUsd: Double? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
)
