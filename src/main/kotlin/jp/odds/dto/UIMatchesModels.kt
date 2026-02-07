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
