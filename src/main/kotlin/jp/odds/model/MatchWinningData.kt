package jp.odds.model

data class MatchWinningData(
    val tournamentId: Long,
    val tournamentName: String,
    val vote: Int,
    val odds: Double
)
