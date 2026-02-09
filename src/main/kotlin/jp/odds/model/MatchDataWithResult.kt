package jp.odds.model

interface MatchDataWithResult {
    val homeScore: Int?
    val awayScore: Int?
    val votingHome: Int?
    val votingDraw: Int?
    val votingAway: Int?
    val oddsHome: String?
    val oddsDraw: String?
    val oddsAway: String?
    val tournamentId: Long
    val tournamentName: String
}
