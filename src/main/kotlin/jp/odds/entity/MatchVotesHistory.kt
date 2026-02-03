package jp.odds.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "match_votes_history", indexes = [
    Index(name = "idx_votes_event_id", columnList = "event_id"),
    Index(name = "idx_votes_recorded_at", columnList = "recorded_at")
])
class MatchVotesHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "event_id", nullable = false)
    var eventId: Long = 0,

    @Column(name = "voting_home")
    var votingHome: Int? = null,

    @Column(name = "voting_draw")
    var votingDraw: Int? = null,

    @Column(name = "voting_away")
    var votingAway: Int? = null,

    @Column(name = "voting_total")
    var votingTotal: Int? = null,

    @Column(name = "recorded_at", nullable = false)
    var recordedAt: Instant = Instant.now()
)
