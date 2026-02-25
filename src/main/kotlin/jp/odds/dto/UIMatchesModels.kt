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
    val roi: Double?
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
