package jp.odds.dto

data class OddsHistoryPoint(
    val timestamp: Long,
    val home: String?,
    val draw: String?,
    val away: String?
)

data class VotesHistoryPoint(
    val timestamp: Long,
    val home: Int?,
    val draw: Int?,
    val away: Int?,
    val total: Int?
)

data class MatchHistoryResponse(
    val oddsHistory: List<OddsHistoryPoint>,
    val votesHistory: List<VotesHistoryPoint>
)

/**
 * Sofascore's per-match statistics (/event/{id}/statistics), flattened for the UI.
 *
 * Only populated once a match is under way - Sofascore 404s the endpoint before kick-off, which
 * surfaces here as an empty [periods] list.
 */
data class MatchStatisticsResponse(
    val periods: List<MatchStatisticsPeriod>
)

/** One period of the match: `ALL`, `1ST` or `2ND`. */
data class MatchStatisticsPeriod(
    val period: String,
    val groups: List<MatchStatisticsGroup>
)

data class MatchStatisticsGroup(
    val groupName: String,
    val items: List<MatchStatisticsItem>
)

data class MatchStatisticsItem(
    val name: String,
    val home: String,
    val away: String,
    /** Numeric side of [home]/[away] when Sofascore provides it, for drawing comparison bars. */
    val homeValue: Double?,
    val awayValue: Double?,
    /** Sofascore's verdict on who leads this stat: 1 = home, 2 = away, 3 = level. */
    val compareCode: Int?
)

data class WinningMatchStatistics(
    val averageVote: Double,
    val averageOdds: Double,
    val totalMatches: Int
)

data class LeagueStatistics(
    val tournamentId: Long,
    val tournamentName: String,
    val averageVote: Double,
    val averageOdds: Double,
    val totalMatches: Int
)

data class WinningMatchStatisticsByLeague(
    val overall: WinningMatchStatistics,
    val byLeague: List<LeagueStatistics>
)

data class LeagueProfitability(
    val tournamentId: Long?,
    val tournamentName: String?,
    val minVoteThreshold: Int?,
    val totalMatches: Int,
    val matchesAboveThreshold: Int,
    val roi: Double?,
    val favoriteWins: Int = 0,
    val averageFavoriteWinOdds: Double? = null
)

data class MatchBettingDetail(
    val homeTeamName: String,
    val awayTeamName: String,
    val homeScore: Int,
    val awayScore: Int,
    val oddsHome: Double?,
    val oddsDraw: Double?,
    val oddsAway: Double?,
    val votingHome: Int?,
    val votingDraw: Int?,
    val votingAway: Int?,
    val favoriteVote: Int?,
    val favoriteOdds: Double?,
    val favoriteWon: Boolean?,
    val tournamentName: String
)

data class ProfitabilityResponse(
    val overall: LeagueProfitability?,
    val byLeague: List<LeagueProfitability>,
    val matches: List<MatchBettingDetail>? = null
)
