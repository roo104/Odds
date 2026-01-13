package jp.odds.service

import jp.odds.dto.*
import jp.odds.entity.DailyMatchData
import jp.odds.repository.DailyMatchDataRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class SofascoreService(
    webClientBuilder: WebClient.Builder,
    private val dailyMatchDataRepository: DailyMatchDataRepository
) {
    private val logger = LoggerFactory.getLogger(SofascoreService::class.java)

    private val webClient = webClientBuilder
        .baseUrl("https://api.sofascore.com/api/v1")
        .defaultHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36")
        .defaultHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
        .defaultHeader("Accept-Language", "en-US,en;q=0.9")
        .defaultHeader("Accept-Encoding", "gzip, deflate, br, zstd")
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

    suspend fun getTodayFootballMatches(): List<SofascoreEvent> {
        val tomorrow = LocalDate.now().plusDays(1)
        return getFootballMatchesByDate(tomorrow)
    }

    suspend fun getFootballMatchesByDate(date: LocalDate): List<SofascoreEvent> {
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)

        // Check if data for this date already exists in database
        if (dailyMatchDataRepository.existsByMatchDate(date)) {
            logger.info("Loading matches for $dateStr from database")
            return loadMatchesFromDatabase(date)
        }

        val uri = "/sport/football/scheduled-events/$dateStr"
        logger.info("Fetching football matches for date: $dateStr from URI: $uri")

        return try {
            val response = webClient
                .get()
                .uri(uri)
                .retrieve()
                .awaitBody<SofascoreEventsResponse>()

            logger.info("Successfully fetched ${response.events.size} matches")

            // Fetch odds and voting for each event with delays (sequentially to avoid rate limiting)
            response.events.forEach { event ->
                kotlinx.coroutines.delay(500L) // 500ms delay between each event
                event.odds = fetchOddsForEvent(event.id)
                event.voting = fetchVotingForEvent(event.id)
            }

            // Save to database
            saveMatchesToDatabase(date, response.events)

            response.events
        } catch (e: Exception) {
            logger.error("Error fetching football matches: ${e.message}", e)
            emptyList()
        }
    }

    private fun loadMatchesFromDatabase(matchDate: LocalDate): List<SofascoreEvent> {
        val dailyData = dailyMatchDataRepository.findByMatchDate(matchDate)
        logger.info("Loaded ${dailyData.size} matches from database for date $matchDate")

        return dailyData.map { data ->
            SofascoreEvent(
                id = data.eventId,
                startTimestamp = data.startTimestamp,
                homeTeam = Team(
                    id = data.homeTeamId,
                    name = data.homeTeamName,
                    country = null
                ),
                awayTeam = Team(
                    id = data.awayTeamId,
                    name = data.awayTeamName,
                    country = null
                ),
                homeScore = null,
                awayScore = null,
                status = Status(
                    type = data.statusType,
                    description = data.statusDescription
                ),
                tournament = Tournament(
                    id = 0,
                    name = data.tournamentName,
                    category = Category(
                        name = data.categoryName,
                        country = null
                    )
                ),
                vote = null,
                odds = if (data.oddsHome != null || data.oddsDraw != null || data.oddsAway != null) {
                    Odds(
                        home = data.oddsHome,
                        draw = data.oddsDraw,
                        away = data.oddsAway
                    )
                } else null,
                voting = if (data.votingHome != null || data.votingDraw != null || data.votingAway != null) {
                    Voting(
                        home = data.votingHome,
                        draw = data.votingDraw,
                        away = data.votingAway
                    )
                } else null
            )
        }
    }

    private fun saveMatchesToDatabase(matchDate: LocalDate, events: List<SofascoreEvent>) {
        val entities = events.map { event ->
            DailyMatchData(
                matchDate = matchDate,
                eventId = event.id,
                startTimestamp = event.startTimestamp,
                homeTeamId = event.homeTeam.id,
                homeTeamName = event.homeTeam.name,
                awayTeamId = event.awayTeam.id,
                awayTeamName = event.awayTeam.name,
                tournamentName = event.tournament.name,
                categoryName = event.tournament.category.name,
                oddsHome = event.odds?.home,
                oddsDraw = event.odds?.draw,
                oddsAway = event.odds?.away,
                votingHome = event.voting?.home,
                votingDraw = event.voting?.draw,
                votingAway = event.voting?.away,
                statusType = event.status.type,
                statusDescription = event.status.description
            )
        }

        dailyMatchDataRepository.saveAll(entities)
        logger.info("Saved ${entities.size} matches to database for date $matchDate")
    }

    suspend fun fetchOddsForEvent(eventId: Long): Odds? {
        return try {
            val oddsResponse = webClient
                .get()
                .uri("/event/$eventId/odds/1/all")
                .retrieve()
                .awaitBody<OddsResponse>()

            logger.debug("Fetched odds for event $eventId: ${oddsResponse.markets?.size} markets")

            // Try multiple market name variations
            val fullTimeMarket = oddsResponse.markets?.find {
                it.marketName.contains("Full time", ignoreCase = true) ||
                        it.marketName.contains("1X2", ignoreCase = true) ||
                        it.marketName.contains("Match winner", ignoreCase = true)
            }

            if (fullTimeMarket != null) {
                logger.debug("Found market: ${fullTimeMarket.marketName} with ${fullTimeMarket.choices?.size} choices")
            } else {
                logger.debug("No full time market found for event $eventId")
            }

            fullTimeMarket?.choices?.let { choices ->
                val odds = Odds(
                    home = choices.find { it.name == "1" }?.fractionalValue,
                    draw = choices.find { it.name == "X" }?.fractionalValue,
                    away = choices.find { it.name == "2" }?.fractionalValue
                )
                logger.debug("Extracted odds for event $eventId: $odds")
                odds
            }
        } catch (e: Exception) {
            logger.debug("Could not fetch odds for event $eventId: ${e.message}")
            null
        }
    }

    suspend fun fetchVotingForEvent(eventId: Long): Voting? {
        return try {
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
                        away = ((vote.vote2 ?: 0) * 100 / total)
                    )
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            logger.warn("Could not fetch voting for event $eventId: ${e.message}", e)
            null
        }
    }

    suspend fun getTeamEvents(teamId: Long): List<SofascoreEvent> {
        return try {
            val response = webClient
                .get()
                .uri("/team/$teamId/events/last/0")
                .retrieve()
                .awaitBody<TeamEventsResponse>()

            logger.info("Fetched ${response.events?.size ?: 0} events for team $teamId")
            response.events ?: emptyList()
        } catch (e: Exception) {
            logger.warn("Could not fetch events for team $teamId: ${e.message}")
            emptyList()
        }
    }

    private fun calculateFormPoints(matches: List<SofascoreEvent>, teamId: Long): Int {
        return matches.sumOf { event ->
            val homeScore = event.homeScore?.current
            val awayScore = event.awayScore?.current

            if (homeScore != null && awayScore != null) {
                val isHomeTeam = event.homeTeam.id == teamId
                when {
                    homeScore == awayScore -> 1 // Draw
                    (isHomeTeam && homeScore > awayScore) || (!isHomeTeam && awayScore > homeScore) -> 2 // Win
                    else -> 0 // Loss
                }
            } else {
                0
            }
        }
    }
}
