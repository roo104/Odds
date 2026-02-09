package jp.odds.service

import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import jp.odds.dto.LeagueStatistics
import jp.odds.dto.WinningMatchStatistics
import jp.odds.dto.WinningMatchStatisticsByLeague
import jp.odds.model.MatchDataWithResult
import jp.odds.model.MatchWinningData
import jp.odds.repository.MatchOddsHistoryRepository
import jp.odds.repository.MatchVotesHistoryRepository
import jp.odds.service.response.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBody
import reactor.netty.http.client.HttpClient
import java.io.IOException
import java.net.UnknownHostException
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

abstract class BaseSofascoreService(
    webClientBuilder: WebClient.Builder,
    private val matchOddsHistoryRepository: MatchOddsHistoryRepository,
    private val matchVotesHistoryRepository: MatchVotesHistoryRepository
) {
    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    protected val webClient: WebClient = webClientBuilder
        .clientConnector(
            ReactorClientHttpConnector(
                HttpClient.create()
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .option(ChannelOption.SO_TIMEOUT, 10000)
                    .responseTimeout(Duration.ofSeconds(10))
                    .doOnConnected { conn ->
                        conn.addHandlerLast(ReadTimeoutHandler(10, TimeUnit.SECONDS))
                    }
                    .compress(true)
            )
        )
        .baseUrl("https://api.sofascore.com/api/v1")
        .defaultHeader(
            "User-Agent",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36"
        )
        .defaultHeader(
            "Accept",
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8"
        )
        .defaultHeader("Accept-Language", "en-US,en;q=0.9")
        .defaultHeader("Cache-Control", "max-age=0")
        .defaultHeader("Sec-Fetch-Dest", "document")
        .defaultHeader("Sec-Fetch-Mode", "navigate")
        .defaultHeader("Sec-Fetch-Site", "none")
        .defaultHeader("Sec-Fetch-User", "?1")
        .defaultHeader("sec-ch-ua", "\"Brave\";v=\"143\", \"Chromium\";v=\"143\", \"Not A(Brand\";v=\"24\"")
        .defaultHeader("sec-ch-ua-mobile", "?0")
        .defaultHeader("sec-ch-ua-platform", "\"macOS\"")
        .defaultHeader("Upgrade-Insecure-Requests", "1")
        .build()

    protected fun logWebClientError(operation: String, exception: Exception, context: Map<String, Any> = emptyMap()) {
        val contextStr = if (context.isNotEmpty()) {
            " [${context.entries.joinToString(", ") { "${it.key}=${it.value}" }}]"
        } else ""

        when (exception) {
            is WebClientResponseException -> {
                val statusCode = exception.statusCode.value()
                val responseBody = exception.responseBodyAsString.take(200).let {
                    if (it.length == 200) "$it..." else it
                }
                logger.warn(
                    "HTTP $statusCode error during $operation$contextStr - Response: ${responseBody.ifEmpty { "empty" }}"
                )
            }
            is WebClientRequestException -> {
                val cause = exception.cause
                when (cause) {
                    is TimeoutException -> logger.warn("Timeout during $operation$contextStr - Request took too long")
                    is UnknownHostException -> logger.warn("Unknown host during $operation$contextStr - ${cause.message}")
                    is IOException -> logger.warn("Network error during $operation$contextStr - ${cause.message}")
                    else -> logger.warn("Request failed during $operation$contextStr - ${exception.message}")
                }
            }
            else -> logger.warn("Unexpected error during $operation$contextStr - ${exception.javaClass.simpleName}: ${exception.message}")
        }
    }

    protected suspend fun fetchOddsForEvent(eventId: Long): Odds? = try {
        val oddsResponse = webClient
            .get()
            .uri("/event/$eventId/odds/1/all")
            .retrieve()
            .awaitBody<OddsResponse>()

        logger.debug("Fetched odds for event $eventId: ${oddsResponse.markets?.size} markets")

        val fullTimeMarket = oddsResponse.markets?.find {
            it.marketName.contains("Full time", ignoreCase = true) ||
                    it.marketName.contains("1X2", ignoreCase = true) ||
                    it.marketName.contains("Match winner", ignoreCase = true)
        }

        fullTimeMarket?.let {
            logger.debug("Found market: ${it.marketName} with ${it.choices?.size} choices")
        } ?: logger.debug("No full time market found for event $eventId")

        fullTimeMarket?.choices?.let { choices ->
            Odds(
                home = choices.find { it.name == "1" }?.fractionalValue,
                draw = choices.find { it.name == "X" }?.fractionalValue,
                away = choices.find { it.name == "2" }?.fractionalValue
            ).also { odds ->
                logger.debug("Extracted odds for event {}: {}", eventId, odds)
            }
        }
    } catch (e: Exception) {
        logWebClientError("fetching odds", e, mapOf("eventId" to eventId))
        null
    }

    protected suspend fun fetchVotingForEvent(eventId: Long): Voting? = try {
        val response = webClient
            .get()
            .uri("/event/$eventId/votes")
            .retrieve()
            .awaitBody<VotingResponse>()

        logger.debug("Fetched votes for event $eventId: vote1=${response.vote?.vote1}, voteX=${response.vote?.voteX}, vote2=${response.vote?.vote2}")

        response.vote?.let { vote ->
            val total = (vote.vote1 ?: 0) + (vote.vote2 ?: 0) + (vote.voteX ?: 0)
            if (total > 0) {
                Voting(
                    home = ((vote.vote1 ?: 0) * 100 / total),
                    draw = ((vote.voteX ?: 0) * 100 / total),
                    away = ((vote.vote2 ?: 0) * 100 / total),
                    total = total
                )
            } else null
        }
    } catch (e: Exception) {
        logWebClientError("fetching voting", e, mapOf("eventId" to eventId))
        null
    }

    protected suspend fun fetchEventDetails(eventId: Long): SofascoreEvent? = try {
        val response = webClient
            .get()
            .uri("/event/$eventId")
            .retrieve()
            .awaitBody<EventDetailsResponse>()

        logger.debug("Fetched event details for $eventId: status=${response.event?.status?.description}")
        response.event
    } catch (e: WebClientResponseException.NotFound) {
        logger.debug("Event $eventId not found (404)")
        null
    } catch (e: Exception) {
        logWebClientError("fetching event details", e, mapOf("eventId" to eventId))
        null
    }

    suspend fun getTeamEvents(teamId: Long): List<SofascoreEvent> = try {
        val response = webClient
            .get()
            .uri("/team/$teamId/events/last/0")
            .retrieve()
            .awaitBody<TeamEventsResponse>()

        logger.info("Fetched ${response.events?.size ?: 0} events for team $teamId")
        response.events?.sortedByDescending { it.startTimestamp } ?: emptyList()
    } catch (e: Exception) {
        logWebClientError("fetching team events", e, mapOf("teamId" to teamId))
        emptyList()
    }

    suspend fun getTournamentStandings(tournamentId: Long, seasonId: Long): StandingsResponse? = try {
        val response = webClient
            .get()
            .uri("/tournament/$tournamentId/season/$seasonId/standings/total")
            .retrieve()
            .awaitBody<StandingsResponse>()

        logger.info("Fetched standings for tournament $tournamentId, season $seasonId: ${response.standings?.size} groups")
        response
    } catch (e: WebClientResponseException.NotFound) {
        logger.debug("Standings not found (404) - tournamentId=$tournamentId, seasonId=$seasonId")
        null
    } catch (e: Exception) {
        logWebClientError("fetching tournament standings", e, mapOf("tournamentId" to tournamentId, "seasonId" to seasonId))
        null
    }

    protected suspend fun saveOddsHistory(eventId: Long, odds: Odds?, timestamp: Instant) {
        odds?.takeIf { it.home != null || it.draw != null || it.away != null }?.let {
            val latestOdds = withContext(Dispatchers.IO) {
                matchOddsHistoryRepository.findFirstByEventIdOrderByRecordedAtDesc(eventId)
            }

            if (latestOdds?.oddsHome != odds.home ||
                latestOdds?.oddsDraw != odds.draw ||
                latestOdds?.oddsAway != odds.away
            ) {
                val oddsHistory = jp.odds.entity.MatchOddsHistory().apply {
                    this.eventId = eventId
                    this.oddsHome = odds.home
                    this.oddsDraw = odds.draw
                    this.oddsAway = odds.away
                    this.recordedAt = timestamp
                }
                withContext(Dispatchers.IO) {
                    matchOddsHistoryRepository.save(oddsHistory)
                }
                logger.debug("Saved odds history for event $eventId (values changed)")
            } else {
                logger.debug("Skipped odds history for event $eventId (no changes)")
            }
        }
    }

    protected suspend fun saveVotesHistory(eventId: Long, voting: Voting?, timestamp: Instant) {
        voting?.takeIf { it.home != null || it.draw != null || it.away != null }?.let {
            val latestVotes = withContext(Dispatchers.IO) {
                matchVotesHistoryRepository.findFirstByEventIdOrderByRecordedAtDesc(eventId)
            }

            if (latestVotes?.votingHome != voting.home ||
                latestVotes?.votingDraw != voting.draw ||
                latestVotes?.votingAway != voting.away ||
                latestVotes?.votingTotal != voting.total
            ) {
                val votesHistory = jp.odds.entity.MatchVotesHistory().apply {
                    this.eventId = eventId
                    this.votingHome = voting.home
                    this.votingDraw = voting.draw
                    this.votingAway = voting.away
                    this.votingTotal = voting.total
                    this.recordedAt = timestamp
                }
                withContext(Dispatchers.IO) {
                    matchVotesHistoryRepository.save(votesHistory)
                }
                logger.debug("Saved votes history for event $eventId (values changed)")
            } else {
                logger.debug("Skipped votes history for event $eventId (no changes)")
            }
        }
    }

    suspend fun getOddsHistory(eventId: Long): List<jp.odds.entity.MatchOddsHistory> = withContext(Dispatchers.IO) {
        matchOddsHistoryRepository.findByEventIdOrderByRecordedAtAsc(eventId)
    }

    suspend fun getVotesHistory(eventId: Long): List<jp.odds.entity.MatchVotesHistory> = withContext(Dispatchers.IO) {
        matchVotesHistoryRepository.findByEventIdOrderByRecordedAtAsc(eventId)
    }

    protected fun convertScheduledEventToSofascoreEvent(scheduledEvent: ScheduledEvent): SofascoreEvent = SofascoreEvent(
        id = scheduledEvent.id,
        startTimestamp = scheduledEvent.startTimestamp,
        homeTeam = Team(
            id = scheduledEvent.homeTeam.id,
            name = scheduledEvent.homeTeam.name,
            country = scheduledEvent.homeTeam.country?.let { Country(it.name) }
        ),
        awayTeam = Team(
            id = scheduledEvent.awayTeam.id,
            name = scheduledEvent.awayTeam.name,
            country = scheduledEvent.awayTeam.country?.let { Country(it.name) }
        ),
        homeScore = scheduledEvent.homeScore?.let {
            Score(current = it.current, display = it.display)
        },
        awayScore = scheduledEvent.awayScore?.let {
            Score(current = it.current, display = it.display)
        },
        status = scheduledEvent.status,
        tournament = Tournament(
            id = scheduledEvent.tournament.id,
            name = scheduledEvent.tournament.name,
            category = Category(
                name = scheduledEvent.tournament.category.name,
                country = scheduledEvent.tournament.category.country?.let { Country(it.name) }
            )
        ),
        season = scheduledEvent.season,
        vote = null,
        eventFilters = scheduledEvent.eventFilters,
        odds = null,
        voting = null,
        homeFormScore = null,
        awayFormScore = null,
        lastUpdated = null,
        isTopLeague = null
    )

    protected fun mapDataToSofascoreEvent(
        eventId: Long,
        startTimestamp: Long,
        homeTeamId: Long,
        homeTeamName: String,
        awayTeamId: Long,
        awayTeamName: String,
        homeScore: Int?,
        awayScore: Int?,
        statusType: String,
        statusDescription: String,
        tournamentId: Long,
        tournamentName: String,
        categoryName: String,
        countryName: String?,
        seasonId: Long?,
        oddsHome: String?,
        oddsDraw: String?,
        oddsAway: String?,
        votingHome: Int?,
        votingDraw: Int?,
        votingAway: Int?,
        votingTotal: Int?,
        lastUpdated: Instant?,
        isTopLeague: Boolean?
    ): SofascoreEvent = SofascoreEvent(
        id = eventId,
        startTimestamp = startTimestamp,
        homeTeam = Team(
            id = homeTeamId,
            name = homeTeamName,
            country = null
        ),
        awayTeam = Team(
            id = awayTeamId,
            name = awayTeamName,
            country = null
        ),
        homeScore = homeScore?.let { Score(current = it) },
        awayScore = awayScore?.let { Score(current = it) },
        status = Status(
            type = statusType,
            description = statusDescription
        ),
        tournament = Tournament(
            id = tournamentId,
            name = tournamentName,
            category = Category(
                name = categoryName,
                country = countryName?.let { Country(it) }
            )
        ),
        season = seasonId?.let { Season(id = it, name = "") },
        vote = null,
        odds = if (oddsHome != null || oddsDraw != null || oddsAway != null) {
            Odds(
                home = oddsHome,
                draw = oddsDraw,
                away = oddsAway
            )
        } else null,
        voting = if (votingHome != null || votingDraw != null || votingAway != null) {
            Voting(
                home = votingHome,
                draw = votingDraw,
                away = votingAway,
                total = votingTotal
            )
        } else null,
        lastUpdated = lastUpdated?.epochSecond ?: 0,
        isTopLeague = isTopLeague
    )

    protected fun parseOdds(fractionalOdds: String): Double = try {
        val parts = fractionalOdds.split('/')
        if (parts.size == 2) {
            val numerator = parts[0].toDouble()
            val denominator = parts[1].toDouble()
            (numerator / denominator) + 1.0
        } else {
            0.0
        }
    } catch (_: Exception) {
        0.0
    }

    protected fun <T : MatchDataWithResult> extractWinningMatchData(matches: List<T>): List<MatchWinningData> {
        return matches.mapNotNull { match ->
            val homeScore = match.homeScore ?: return@mapNotNull null
            val awayScore = match.awayScore ?: return@mapNotNull null

            val winningVote: Int?
            val winningOdds: String?

            when {
                homeScore > awayScore -> {
                    winningVote = match.votingHome
                    winningOdds = match.oddsHome
                }
                awayScore > homeScore -> {
                    winningVote = match.votingAway
                    winningOdds = match.oddsAway
                }
                else -> {
                    winningVote = match.votingDraw
                    winningOdds = match.oddsDraw
                }
            }

            if (winningVote != null && winningOdds != null) {
                MatchWinningData(
                    tournamentId = match.tournamentId,
                    tournamentName = match.tournamentName,
                    vote = winningVote,
                    odds = parseOdds(winningOdds)
                )
            } else {
                null
            }
        }
    }

    protected fun calculateWinningStatistics(winningMatchData: List<MatchWinningData>): WinningMatchStatistics {
        if (winningMatchData.isEmpty()) {
            return WinningMatchStatistics(
                averageVote = 0.0,
                averageOdds = 0.0,
                totalMatches = 0
            )
        }

        return WinningMatchStatistics(
            averageVote = winningMatchData.map { it.vote }.average(),
            averageOdds = winningMatchData.map { it.odds }.average(),
            totalMatches = winningMatchData.size
        )
    }

    protected fun calculateStatisticsByLeague(winningMatchData: List<MatchWinningData>): WinningMatchStatisticsByLeague {
        val overall = calculateWinningStatistics(winningMatchData)

        val byLeague = winningMatchData
            .groupBy { it.tournamentId to it.tournamentName }
            .map { (tournamentInfo, matches) ->
                LeagueStatistics(
                    tournamentId = tournamentInfo.first,
                    tournamentName = tournamentInfo.second,
                    averageVote = matches.map { it.vote }.average(),
                    averageOdds = matches.map { it.odds }.average(),
                    totalMatches = matches.size
                )
            }
            .sortedByDescending { it.totalMatches }

        return WinningMatchStatisticsByLeague(
            overall = overall,
            byLeague = byLeague
        )
    }
}
