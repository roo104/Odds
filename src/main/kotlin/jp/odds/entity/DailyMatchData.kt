package jp.odds.entity

import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "daily_match_data", uniqueConstraints = [
    UniqueConstraint(columnNames = ["event_id"])
])
data class DailyMatchData(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "match_date", nullable = false)
    val matchDate: LocalDate = LocalDate.now(),

    @Column(name = "event_id", nullable = false)
    val eventId: Long = 0,

    @Column(name = "start_timestamp", nullable = false)
    val startTimestamp: Long = 0,

    @Column(name = "home_team_id", nullable = false)
    val homeTeamId: Long = 0,

    @Column(name = "home_team_name", nullable = false)
    val homeTeamName: String = "",

    @Column(name = "away_team_id", nullable = false)
    val awayTeamId: Long = 0,

    @Column(name = "away_team_name", nullable = false)
    val awayTeamName: String = "",

    @Column(name = "tournament_id", nullable = false)
    val tournamentId: Long = 0,

    @Column(name = "tournament_name", nullable = false)
    val tournamentName: String = "",

    @Column(name = "season_id")
    val seasonId: Long? = null,

    @Column(name = "category_name", nullable = false)
    val categoryName: String = "",

    @Column(name = "country_name")
    val countryName: String? = null,

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

    @Column(name = "voting_total")
    val votingTotal: Int? = null,

    @Column(name = "status_type", nullable = false)
    val statusType: String = "",

    @Column(name = "status_description", nullable = false)
    val statusDescription: String = "",

    @Column(name = "home_score")
    val homeScore: Int? = null,

    @Column(name = "away_score")
    val awayScore: Int? = null,

    @Column(name = "last_updated", nullable = true)
    val lastUpdated: Instant? = null,

    @Column(name = "is_top_league", nullable = false)
    val isTopLeague: Boolean = true
)
