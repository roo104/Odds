package jp.odds.model

data class MatchBettingData(
    val tournamentId: Long,
    val tournamentName: String,
    val homeTeamName: String,
    val awayTeamName: String,
    val homeScore: Int,
    val awayScore: Int,
    val oddsHome: String?,
    val oddsDraw: String?,
    val oddsAway: String?,
    val votingHome: Int?,
    val votingDraw: Int?,
    val votingAway: Int?,
    val favoriteVote: Int,
    val favoriteOdds: Double,
    val favoriteWon: Boolean
)
