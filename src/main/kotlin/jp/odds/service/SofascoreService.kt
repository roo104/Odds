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

    suspend fun getFootballMatchesByDate(date: LocalDate, forceRefresh: Boolean = false): List<SofascoreEvent> {
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)

        // Check if data for this date already exists in database
        if (!forceRefresh && dailyMatchDataRepository.existsByMatchDate(date)) {
            logger.info("Loading matches for $dateStr from database")
            return loadMatchesFromDatabase(date)
        }

        val uri = "/sport/football/scheduled-events/$dateStr"
        logger.info("Fetching football matches for date: $dateStr from URI: $uri (forceRefresh=$forceRefresh)")

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

            // Save to database (will update existing records or insert new ones)
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
                } else null,
                lastUpdated = data.lastUpdated?.epochSecond ?: 0
            )
        }
    }

    private fun saveMatchesToDatabase(matchDate: LocalDate, events: List<SofascoreEvent>) {
        val now = java.time.Instant.now()

        // Load existing records to preserve IDs for updates
        val existingRecords = dailyMatchDataRepository.findByMatchDate(matchDate)
            .associateBy { it.eventId }

        val entities = events.map { event ->
            val existing = existingRecords[event.id]
            DailyMatchData(
                id = existing?.id, // Preserve existing ID for update, null for insert
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
                statusDescription = event.status.description,
                lastUpdated = now
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
                        away = ((vote.vote2 ?: 0) * 100 / total),
                        total = total
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

    suspend fun refreshSingleMatch(eventId: Long, matchDate: LocalDate): SofascoreEvent {
        logger.info("Refreshing single match with eventId: $eventId")

        // Find the existing match in database to get basic info
        val existingData = dailyMatchDataRepository.findByMatchDate(matchDate)
            .find { it.eventId == eventId }
            ?: throw IllegalArgumentException("Match $eventId not found in database for date $matchDate")

        // Just fetch fresh odds and voting data for this event
        kotlinx.coroutines.delay(500L)
        val odds = fetchOddsForEvent(eventId)
        val voting = fetchVotingForEvent(eventId)

        // Throw error if we couldn't fetch any fresh data
        if (odds == null && voting == null) {
            logger.warn("No odds or voting data fetched for match $eventId")
            throw RuntimeException("Could not fetch fresh odds or voting data for match $eventId")
        }

        // Update in database with fresh data
        val now = java.time.Instant.now()
        val updatedData = existingData.copy(
            oddsHome = odds?.home,
            oddsDraw = odds?.draw,
            oddsAway = odds?.away,
            votingHome = voting?.home,
            votingDraw = voting?.draw,
            votingAway = voting?.away,
            lastUpdated = now
        )
        dailyMatchDataRepository.save(updatedData)
        logger.info("Updated odds and voting for match $eventId in database")

        // Reconstruct the event from database with updated data
        return SofascoreEvent(
            id = updatedData.eventId,
            startTimestamp = updatedData.startTimestamp,
            homeTeam = Team(
                id = updatedData.homeTeamId,
                name = updatedData.homeTeamName,
                country = null
            ),
            awayTeam = Team(
                id = updatedData.awayTeamId,
                name = updatedData.awayTeamName,
                country = null
            ),
            homeScore = null,
            awayScore = null,
            status = Status(
                type = updatedData.statusType,
                description = updatedData.statusDescription
            ),
            tournament = Tournament(
                id = 0,
                name = updatedData.tournamentName,
                category = Category(
                    name = updatedData.categoryName,
                    country = null
                )
            ),
            vote = null,
            odds = odds,
            voting = voting,
            lastUpdated = now.epochSecond
        )
    }
}
