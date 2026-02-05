package jp.odds.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "match_bets", indexes = [
    Index(name = "idx_bets_event_id", columnList = "event_id"),
    Index(name = "idx_bets_created_at", columnList = "created_at")
])
class MatchBet(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "event_id", nullable = false)
    var eventId: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "sport", nullable = false)
    var sport: SportType = SportType.Football,

    @Enumerated(EnumType.STRING)
    @Column(name = "selection", nullable = false)
    var selection: BetSelection = BetSelection.HOME,

    @Column(name = "home_team_name", nullable = false)
    var homeTeamName: String = "",

    @Column(name = "away_team_name", nullable = false)
    var awayTeamName: String = "",

    @Column(name = "start_timestamp", nullable = false)
    var startTimestamp: Long = 0,

    @Column(name = "final_home_score")
    var finalHomeScore: Int? = null,

    @Column(name = "final_away_score")
    var finalAwayScore: Int? = null,

    @Column(name = "odds")
    var odds: Double? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
)
