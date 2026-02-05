package jp.odds.dto

import jp.odds.entity.BetSelection
import jp.odds.entity.SportType
import java.time.Instant

data class CreateBetRequest(
    val eventId: Long,
    val sport: SportType,
    val selection: BetSelection,
    val homeTeamName: String,
    val awayTeamName: String,
    val startTimestamp: Long,
    val odds: Double?
)

data class MatchBetResponse(
    val id: Long,
    val eventId: Long,
    val sport: SportType,
    val selection: BetSelection,
    val homeTeamName: String,
    val awayTeamName: String,
    val startTimestamp: Long,
    val finalHomeScore: Int?,
    val finalAwayScore: Int?,
    val odds: Double?,
    val createdAt: Instant
)

data class BetsPageResponse(
    val content: List<MatchBetResponse>,
    val page: Int,
    val size: Int,
    val totalPages: Int,
    val totalElements: Long,
    val stats: BetStatistics
)

data class BetStatistics(
    val totalBets: Long,
    val finishedBets: Long,
    val wonBets: Long,
    val lostBets: Long,
    val winRatio: Double?,
    val avgWinningOdds: Double?,
    val avgLosingOdds: Double?,
    val betsWithOdds: Long,
    val expectedValue: Double?,
    val actualProfit: Double?
)

data class UpdateOddsRequest(
    val odds: Double?
)
