package jp.odds.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "match_odds_history", indexes = [
    Index(name = "idx_odds_event_id", columnList = "event_id"),
    Index(name = "idx_odds_recorded_at", columnList = "recorded_at")
])
class MatchOddsHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "event_id", nullable = false)
    var eventId: Long = 0,

    @Column(name = "odds_home")
    var oddsHome: String? = null,

    @Column(name = "odds_draw")
    var oddsDraw: String? = null,

    @Column(name = "odds_away")
    var oddsAway: String? = null,

    @Column(name = "recorded_at", nullable = false)
    var recordedAt: Instant = Instant.now()
)
