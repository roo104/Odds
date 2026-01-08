package jp.odds.entity

import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "daily_match_data", uniqueConstraints = [
    UniqueConstraint(columnNames = ["match_date", "event_id"])
])
data class DailyMatchData(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "match_date", nullable = false)
    val matchDate: LocalDate,

    @Column(name = "event_id", nullable = false)
    val eventId: Long,

    @Column(name = "start_timestamp", nullable = false)
    val startTimestamp: Long,

    @Column(name = "home_team_id", nullable = false)
    val homeTeamId: Long,

    @Column(name = "home_team_name", nullable = false)
    val homeTeamName: String,

    @Column(name = "away_team_id", nullable = false)
    val awayTeamId: Long,

    @Column(name = "away_team_name", nullable = false)
    val awayTeamName: String,

    @Column(name = "tournament_name", nullable = false)
    val tournamentName: String,

    @Column(name = "category_name", nullable = false)
    val categoryName: String,

    @Column(name = "odds_home")
    val oddsHome: String? = null,

    @Column(name = "odds_draw")
    val oddsDraw: String? = null,

    @Column(name = "odds_away")
    val oddsAway: String? = null,

    @Column(name = "voting_home")
    val votingHome: Int? = null,

    @Column(name = "voting_draw")
    val votingDraw: Int? = null,

    @Column(name = "voting_away")
    val votingAway: Int? = null,

    @Column(name = "status_type", nullable = false)
    val statusType: String,

    @Column(name = "status_description", nullable = false)
    val statusDescription: String
)
