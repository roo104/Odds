package jp.odds.service

import jp.odds.dto.BetStatistics
import jp.odds.dto.BetsPageResponse
import jp.odds.dto.CreateBetRequest
import jp.odds.dto.MatchBetResponse
import jp.odds.entity.BetSelection
import jp.odds.entity.MatchBet
import jp.odds.repository.MatchBetRepository
import jp.odds.service.response.model.SofascoreEventResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

@Service
class BetsService(
    webClientBuilder: WebClient.Builder,
    private val matchBetRepository: MatchBetRepository
) {
    private val logger = LoggerFactory.getLogger(BetsService::class.java)

    private val webClient: WebClient = webClientBuilder
        .baseUrl("https://www.sofascore.com/api/v1")
        .defaultHeader(
            "User-Agent",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36"
        )
        .build()

    suspend fun createBet(request: CreateBetRequest): MatchBetResponse = withContext(Dispatchers.IO) {
        // Check if bet already exists for this event and selection
        val existingBet = matchBetRepository.findByEventIdAndSelection(request.eventId, request.selection)
        if (existingBet.isPresent) {
            logger.info("Bet already exists for eventId=${request.eventId}, selection=${request.selection}, returning existing bet")
            return@withContext existingBet.get().toResponse()
        }

        val bet = MatchBet(
            eventId = request.eventId,
            sport = request.sport,
            selection = request.selection,
            homeTeamName = request.homeTeamName,
            awayTeamName = request.awayTeamName,
            startTimestamp = request.startTimestamp,
            odds = request.odds
        )
        logger.info("Creating new bet for eventId=${request.eventId}, selection=${request.selection}")
        matchBetRepository.save(bet).toResponse()
    }

    suspend fun listBets(pageable: Pageable): BetsPageResponse = withContext(Dispatchers.IO) {
        val page = matchBetRepository.findAll(pageable)
        val allBets = matchBetRepository.findAll()

        val stats = calculateStatistics(allBets)

        BetsPageResponse(
            content = page.content.map { it.toResponse() },
            page = page.number,
            size = page.size,
            totalPages = page.totalPages,
            totalElements = page.totalElements,
            stats = stats
        )
    }

    suspend fun updateOdds(betId: Long, odds: Double?): MatchBetResponse = withContext(Dispatchers.IO) {
        logger.info("Updating odds for betId=$betId to odds=$odds")
        val bet = matchBetRepository.findById(betId).orElseThrow {
            IllegalArgumentException("Bet not found: $betId")
        }
        bet.odds = odds
        matchBetRepository.save(bet).toResponse()
    }

    private fun calculateStatistics(bets: List<MatchBet>): BetStatistics {
        val totalBets = bets.size.toLong()
        val finishedBets = bets.count { it.finalHomeScore != null && it.finalAwayScore != null }.toLong()
        val wonBets = bets.count { isWinningBet(it) }.toLong()
        val lostBets = finishedBets - wonBets
        val winRatio = if (finishedBets > 0) wonBets.toDouble() / finishedBets.toDouble() else null

        // Calculate odds-related statistics
        val betsWithOdds = bets.count { it.odds != null }.toLong()

        val finishedBetsWithOdds = bets.filter {
            it.finalHomeScore != null && it.finalAwayScore != null && it.odds != null
        }

        val wonBetsWithOdds = finishedBetsWithOdds.filter { isWinningBet(it) }
        val lostBetsWithOdds = finishedBetsWithOdds.filter { !isWinningBet(it) }

        val avgWinningOdds = if (wonBetsWithOdds.isNotEmpty()) {
            wonBetsWithOdds.mapNotNull { it.odds }.average()
        } else null

        val avgLosingOdds = if (lostBetsWithOdds.isNotEmpty()) {
            lostBetsWithOdds.mapNotNull { it.odds }.average()
        } else null

        // Expected Value: (Win Probability * Average Winning Odds) - 1
        // This shows if your bets have positive expected value
        val expectedValue = if (finishedBetsWithOdds.isNotEmpty() && avgWinningOdds != null) {
            val winProbability = wonBetsWithOdds.size.toDouble() / finishedBetsWithOdds.size.toDouble()
            (winProbability * avgWinningOdds) - 1.0
        } else null

        // Actual Profit: Sum of (odds - 1) for wins minus number of losses
        // Assuming 1 unit bet on each, shows total profit/loss in units
        val actualProfit = if (finishedBetsWithOdds.isNotEmpty()) {
            val winningsProfit = wonBetsWithOdds.sumOf { (it.odds!! - 1.0) }
            val lossesAmount = lostBetsWithOdds.size.toDouble()
            winningsProfit - lossesAmount
        } else null

        return BetStatistics(
            totalBets = totalBets,
            finishedBets = finishedBets,
            wonBets = wonBets,
            lostBets = lostBets,
            winRatio = winRatio,
            avgWinningOdds = avgWinningOdds,
            avgLosingOdds = avgLosingOdds,
            betsWithOdds = betsWithOdds,
            expectedValue = expectedValue,
            actualProfit = actualProfit
        )
    }

    private fun isWinningBet(bet: MatchBet): Boolean {
        val homeScore = bet.finalHomeScore ?: return false
        val awayScore = bet.finalAwayScore ?: return false

        return when {
            homeScore == awayScore -> bet.selection == BetSelection.DRAW
            homeScore > awayScore -> bet.selection == BetSelection.HOME
            else -> bet.selection == BetSelection.AWAY
        }
    }

    suspend fun refreshBetScore(betId: Long): MatchBetResponse = withContext(Dispatchers.IO) {
        logger.info("Refreshing bet score for betId: $betId")
        val bet = matchBetRepository.findById(betId).orElseThrow {
            IllegalArgumentException("Bet not found: $betId")
        }
        logger.info("Found bet: eventId=${bet.eventId}, sport=${bet.sport}, homeTeam=${bet.homeTeamName}, awayTeam=${bet.awayTeamName}")

        try {
            logger.info("Fetching match data from Sofascore: event/${bet.eventId}")

            val eventData = webClient
                .get()
                .uri("/event/${bet.eventId}")
                .retrieve()
                .awaitBody<SofascoreEventResponse>()

            logger.info("Received event status: ${eventData.event.status.type}, description: ${eventData.event.status.description}")

            if (isFinal(eventData.event.status.type, eventData.event.status.description)) {
                val homeScore = eventData.event.homeScore?.current
                val awayScore = eventData.event.awayScore?.current

                if (homeScore != null && awayScore != null) {
                    logger.info("Found final score: $homeScore - $awayScore")
                    if (bet.finalHomeScore != homeScore || bet.finalAwayScore != awayScore) {
                        bet.finalHomeScore = homeScore
                        bet.finalAwayScore = awayScore
                        matchBetRepository.save(bet)
                        logger.info("Bet $betId updated with final scores: $homeScore - $awayScore")
                    } else {
                        logger.info("Bet $betId already has correct final score: $homeScore - $awayScore")
                    }
                } else {
                    logger.warn("Match is final but scores are null: home=$homeScore, away=$awayScore")
                }
            } else {
                logger.info("Match is not finished yet (status: ${eventData.event.status.type})")
            }
        } catch (e: Exception) {
            logger.error("Error fetching match data from Sofascore: ${e.message}", e)
        }

        bet.toResponse()
    }

    private fun isFinal(statusType: String?, statusDescription: String?): Boolean {
        val type = statusType?.lowercase() ?: ""
        val desc = statusDescription?.lowercase() ?: ""
        return type == "finished" || desc == "finished" || desc == "ended" || desc == "full time"
    }

    private fun MatchBet.toResponse(): MatchBetResponse = MatchBetResponse(
        id = requireNotNull(id),
        eventId = eventId,
        sport = sport,
        selection = selection,
        homeTeamName = homeTeamName,
        awayTeamName = awayTeamName,
        startTimestamp = startTimestamp,
        finalHomeScore = finalHomeScore,
        finalAwayScore = finalAwayScore,
        odds = odds,
        createdAt = createdAt
    )
}
