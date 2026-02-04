package jp.odds.service

import jp.odds.entity.DailyHandballMatchData
import jp.odds.entity.DailyMatchData
import jp.odds.repository.DailyHandballMatchDataRepository
import jp.odds.repository.DailyMatchDataRepository
import jp.odds.repository.MatchOddsHistoryRepository
import jp.odds.repository.MatchVotesHistoryRepository
import jp.odds.service.response.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBody
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class SofascoreService(
    webClientBuilder: WebClient.Builder,
    private val dailyMatchDataRepository: DailyMatchDataRepository,
    private val dailyHandballMatchDataRepository: DailyHandballMatchDataRepository,
    private val matchOddsHistoryRepository: MatchOddsHistoryRepository,
    private val matchVotesHistoryRepository: MatchVotesHistoryRepository
) {
    private val logger = LoggerFactory.getLogger(SofascoreService::class.java)

    private val webClient = webClientBuilder
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

    suspend fun getTodayHandballMatches(): List<SofascoreEvent> {
        val tomorrow = LocalDate.now().plusDays(1)
        return getHandballMatchesByDate(tomorrow)
    }

    suspend fun getFootballMatchesByDate(date: LocalDate, forceRefresh: Boolean = false, includeAllLeagues: Boolean = false): List<SofascoreEvent> {
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)

        // Check if data for this date already exists in database
        if (!forceRefresh) {
            logger.info("Loading matches for $dateStr from database")
            return loadMatchesFromDatabase(date)
        }

        val uri = "/sport/football/scheduled-events/$dateStr"
        logger.info("Fetching football matches for date: $dateStr from URI: $uri (forceRefresh=${true})")

        return try {
            val response = webClient
                .get()
                .uri(uri)
                .retrieve()
                .awaitBody<ScheduledEventsResponse>()

            logger.info("Successfully fetched ${response.events.size} matches")

            // Convert ScheduledEvent to SofascoreEvent
            val events = response.events.map { scheduledEvent ->
                convertScheduledEventToSofascoreEvent(scheduledEvent)
            }

            val filteredEvents = if (includeAllLeagues) {
                events.map { event ->
                    event.copy(isTopLeague = isTopLeague(event))
                }
            } else {
                filterEventsByTopLeagues(events).map { event ->
                    event.copy(isTopLeague = true)
                }
            }
            logger.info("Filtered to ${filteredEvents.size} ${if (includeAllLeagues) "total" else "top league"} matches for $dateStr")

            // Fetch odds and voting for each event with delays (sequentially to avoid rate limiting)
            val now = Instant.now()
            filteredEvents.forEach { event ->
                kotlinx.coroutines.delay(500L) // 500ms delay between each event
                event.odds = fetchOddsForEvent(event.id)
                event.voting = fetchVotingForEvent(event.id)
                event.lastUpdated = now.epochSecond

                // Save historical odds and votes
                saveOddsHistory(event.id, event.odds, now)
                saveVotesHistory(event.id, event.voting, now)
            }

            // Save to database (will update existing records or insert new ones)
            saveMatchesToDatabase(date, filteredEvents)

            filteredEvents
        } catch (e: Exception) {
            logger.error("Error fetching football matches: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getHandballMatchesByDate(date: LocalDate, forceRefresh: Boolean = false): List<SofascoreEvent> {
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)

        if (!forceRefresh) {
            logger.info("Loading handball matches for $dateStr from database")
            return loadHandballMatchesFromDatabase(date)
        }

        val uri = "/sport/handball/scheduled-events/$dateStr"
        logger.info("Fetching handball matches for date: $dateStr from URI: $uri (forceRefresh=${true})")

        return try {
            val response = webClient
                .get()
                .uri(uri)
                .retrieve()
                .awaitBody<ScheduledEventsResponse>()

            logger.info("Successfully fetched ${response.events.size} matches")

            val events = response.events.map { scheduledEvent ->
                convertScheduledEventToSofascoreEvent(scheduledEvent)
            }

            val now = Instant.now()
            events.forEach { event ->
                kotlinx.coroutines.delay(500L)
                event.odds = fetchOddsForEvent(event.id)
                event.voting = fetchVotingForEvent(event.id)
                event.lastUpdated = now.epochSecond

                saveOddsHistory(event.id, event.odds, now)
                saveVotesHistory(event.id, event.voting, now)
            }

            saveHandballMatchesToDatabase(date, events)

            events
        } catch (e: Exception) {
            logger.error("Error fetching handball matches: ${e.message}", e)
            emptyList()
        }
    }

    private suspend fun loadMatchesFromDatabase(matchDate: LocalDate): List<SofascoreEvent> {
        // Convert LocalDate to timestamp range (start of day to end of day) in system timezone
        val startOfDay = matchDate.atStartOfDay(java.time.ZoneId.systemDefault()).toEpochSecond()
        val endOfDay = matchDate.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toEpochSecond()

        val dailyData = withContext(Dispatchers.IO) {
            dailyMatchDataRepository.findByStartTimestampBetween(startOfDay, endOfDay)
        }
        logger.info("Loaded ${dailyData.size} matches from database for date $matchDate (timestamp range: $startOfDay - $endOfDay)")

        val events = dailyData.map { data ->
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
                homeScore = data.homeScore?.let { Score(current = it) },
                awayScore = data.awayScore?.let { Score(current = it) },
                status = Status(
                    type = data.statusType,
                    description = data.statusDescription
                ),
                tournament = Tournament(
                    id = data.tournamentId,
                    name = data.tournamentName,
                    category = Category(
                        name = data.categoryName,
                        country = data.countryName?.let { Country(it) }
                    )
                ),
                season = data.seasonId?.let { Season(id = it, name = "") },
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
                        away = data.votingAway,
                        total = data.votingTotal
                    )
                } else null,
                lastUpdated = data.lastUpdated?.epochSecond ?: 0,
                isTopLeague = data.isTopLeague
            )
        }

        return events
    }

    private suspend fun loadHandballMatchesFromDatabase(matchDate: LocalDate): List<SofascoreEvent> {
        val startOfDay = matchDate.atStartOfDay(java.time.ZoneId.systemDefault()).toEpochSecond()
        val endOfDay = matchDate.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toEpochSecond()

        val dailyData = withContext(Dispatchers.IO) {
            dailyHandballMatchDataRepository.findByStartTimestampBetween(startOfDay, endOfDay)
        }
        logger.info("Loaded ${dailyData.size} handball matches from database for date $matchDate (timestamp range: $startOfDay - $endOfDay)")

        val events = dailyData.map { data ->
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
                homeScore = data.homeScore?.let { Score(current = it) },
                awayScore = data.awayScore?.let { Score(current = it) },
                status = Status(
                    type = data.statusType,
                    description = data.statusDescription
                ),
                tournament = Tournament(
                    id = data.tournamentId,
                    name = data.tournamentName,
                    category = Category(
                        name = data.categoryName,
                        country = data.countryName?.let { Country(it) }
                    )
                ),
                season = data.seasonId?.let { Season(id = it, name = "") },
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
                        away = data.votingAway,
                        total = data.votingTotal
                    )
                } else null,
                lastUpdated = data.lastUpdated?.epochSecond ?: 0
            )
        }

        return events
    }

    private fun isTopLeague(event: SofascoreEvent): Boolean =
        event.eventFilters?.level?.contains("top-competitions") ?: false

    private suspend fun saveMatchesToDatabase(matchDate: LocalDate, events: List<SofascoreEvent>) {
        val now = Instant.now()

        // Load existing records to preserve IDs for updates, keyed by event_id.
        val existingRecords = if (events.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                dailyMatchDataRepository.findByEventIdIn(events.map { it.id })
                    .associateBy { it.eventId }
            }
        } else {
            emptyMap()
        }

        val entities = events.mapNotNull { event ->
            val existing = existingRecords[event.id]
            if (existing?.matchDate?.isBefore(matchDate) == true) {
                logger.info(
                    "Skipping update for event ${event.id} on $matchDate; earlier match_date exists: ${existing.matchDate}"
                )
                return@mapNotNull null
            }
            DailyMatchData(
                id = existing?.id, // Preserve existing ID for update, null for insert
                matchDate = existing?.matchDate ?: matchDate,
                eventId = event.id,
                startTimestamp = event.startTimestamp,
                homeTeamId = event.homeTeam.id,
                homeTeamName = event.homeTeam.name,
                awayTeamId = event.awayTeam.id,
                awayTeamName = event.awayTeam.name,
                tournamentId = event.tournament.id,
                tournamentName = event.tournament.name,
                seasonId = event.season?.id,
                categoryName = event.tournament.category.name,
                countryName = event.tournament.category.country?.name,
                homeScore = event.homeScore?.current,
                awayScore = event.awayScore?.current,
                oddsHome = event.odds?.home,
                oddsDraw = event.odds?.draw,
                oddsAway = event.odds?.away,
                votingHome = event.voting?.home,
                votingDraw = event.voting?.draw,
                votingAway = event.voting?.away,
                votingTotal = event.voting?.total,
                statusType = event.status.type,
                statusDescription = event.status.description,
                lastUpdated = now,
                isTopLeague = isTopLeague(event)
            )
        }

        withContext(Dispatchers.IO) {
            dailyMatchDataRepository.saveAll(entities)
        }
        logger.info("Saved ${entities.size} matches to database for date $matchDate")
    }

    private suspend fun saveHandballMatchesToDatabase(matchDate: LocalDate, events: List<SofascoreEvent>) {
        val now = Instant.now()

        val existingRecords = if (events.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                dailyHandballMatchDataRepository.findByEventIdIn(events.map { it.id })
                    .associateBy { it.eventId }
            }
        } else {
            emptyMap()
        }

        val entities = events.mapNotNull { event ->
            val existing = existingRecords[event.id]
            if (existing?.matchDate?.isBefore(matchDate) == true) {
                logger.info(
                    "Skipping update for handball event ${event.id} on $matchDate; earlier match_date exists: ${existing.matchDate}"
                )
                return@mapNotNull null
            }
            DailyHandballMatchData(
                id = existing?.id,
                matchDate = existing?.matchDate ?: matchDate,
                eventId = event.id,
                startTimestamp = event.startTimestamp,
                homeTeamId = event.homeTeam.id,
                homeTeamName = event.homeTeam.name,
                awayTeamId = event.awayTeam.id,
                awayTeamName = event.awayTeam.name,
                tournamentId = event.tournament.id,
                tournamentName = event.tournament.name,
                seasonId = event.season?.id,
                categoryName = event.tournament.category.name,
                countryName = event.tournament.category.country?.name,
                homeScore = event.homeScore?.current,
                awayScore = event.awayScore?.current,
                oddsHome = event.odds?.home,
                oddsDraw = event.odds?.draw,
                oddsAway = event.odds?.away,
                votingHome = event.voting?.home,
                votingDraw = event.voting?.draw,
                votingAway = event.voting?.away,
                votingTotal = event.voting?.total,
                statusType = event.status.type,
                statusDescription = event.status.description,
                lastUpdated = now
            )
        }

        withContext(Dispatchers.IO) {
            dailyHandballMatchDataRepository.saveAll(entities)
        }
        logger.info("Saved ${entities.size} handball matches to database for date $matchDate")
    }

    private fun filterEventsByTopLeagues(events: List<SofascoreEvent>): List<SofascoreEvent> {
        return events.filter { event -> isTopLeague(event) }
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
                } else null
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
            response.events?.sortedByDescending { it.startTimestamp } ?: emptyList()
        } catch (e: Exception) {
            logger.warn("Could not fetch events for team $teamId: ${e.message}")
            emptyList()
        }
    }

    private suspend fun fetchEventDetails(eventId: Long): SofascoreEvent? {
        return try {
            val response = webClient
                .get()
                .uri("/event/$eventId")
                .retrieve()
                .awaitBody<EventDetailsResponse>()

            logger.debug("Fetched event details for $eventId: status=${response.event?.status?.description}")
            response.event
        } catch (_: WebClientResponseException.NotFound) {
            logger.debug("Event $eventId not found (404)")
            null
        } catch (e: Exception) {
            logger.warn("Could not fetch event details for $eventId: ${e.message}")
            null
        }
    }

    suspend fun refreshSingleMatch(eventId: Long): SofascoreEvent {
        logger.info("Refreshing single match with eventId: $eventId")

        // Find the existing match in database to get basic info
        val existingData = withContext(Dispatchers.IO) {
            dailyMatchDataRepository.findByEventId(eventId)
        } ?: throw IllegalArgumentException("Match $eventId not found in database")

        // Fetch fresh event details, odds and voting data
        kotlinx.coroutines.delay(500L)
        val eventDetails = fetchEventDetails(eventId)
        val odds = fetchOddsForEvent(eventId)
        val voting = fetchVotingForEvent(eventId)
        val now = Instant.now()

        // Save historical odds and votes
        saveOddsHistory(eventId, odds, now)
        saveVotesHistory(eventId, voting, now)

        // Throw error if we couldn't fetch any fresh data
        if (odds == null && voting == null && eventDetails == null) {
            logger.warn("No data fetched for match $eventId")
            throw RuntimeException("Could not fetch fresh data for match $eventId")
        }

        // Update in database with fresh data
        val refreshedTournamentId = eventDetails?.tournament?.id?.takeIf { it > 0 } ?: existingData.tournamentId
        val refreshedTournamentName =
            eventDetails?.tournament?.name?.takeIf { it.isNotBlank() } ?: existingData.tournamentName
        val refreshedCategoryName =
            eventDetails?.tournament?.category?.name?.takeIf { it.isNotBlank() } ?: existingData.categoryName
        val refreshedSeasonId = eventDetails?.season?.id

        val updatedData = existingData.copy(
            tournamentId = refreshedTournamentId,
            tournamentName = refreshedTournamentName,
            seasonId = refreshedSeasonId ?: existingData.seasonId,
            categoryName = refreshedCategoryName,
            homeScore = eventDetails?.homeScore?.current,
            awayScore = eventDetails?.awayScore?.current,
            oddsHome = odds?.home,
            oddsDraw = odds?.draw,
            oddsAway = odds?.away,
            votingHome = voting?.home,
            votingDraw = voting?.draw,
            votingAway = voting?.away,
            votingTotal = voting?.total,
            statusType = eventDetails?.status?.type ?: existingData.statusType,
            statusDescription = eventDetails?.status?.description ?: existingData.statusDescription,
            lastUpdated = now
        )
        withContext(Dispatchers.IO) {
            dailyMatchDataRepository.save(updatedData)
        }
        logger.info("Updated match $eventId - tournamentId: ${updatedData.tournamentId}, tournamentName: ${updatedData.tournamentName}")

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
            homeScore = eventDetails?.homeScore,
            awayScore = eventDetails?.awayScore,
            status = Status(
                type = updatedData.statusType,
                description = updatedData.statusDescription
            ),
            tournament = Tournament(
                id = updatedData.tournamentId,
                name = updatedData.tournamentName,
                category = Category(
                    name = updatedData.categoryName,
                    country = null
                )
            ),
            season = updatedData.seasonId?.let { Season(id = it, name = "") },
            vote = null,
            odds = odds,
            voting = voting,
            lastUpdated = now.epochSecond,
            isTopLeague = updatedData.isTopLeague
        )
    }

    suspend fun refreshHandballSingleMatch(eventId: Long): SofascoreEvent {
        logger.info("Refreshing single handball match with eventId: $eventId")

        val existingData = withContext(Dispatchers.IO) {
            dailyHandballMatchDataRepository.findByEventId(eventId)
        } ?: throw IllegalArgumentException("Match $eventId not found in database")

        kotlinx.coroutines.delay(500L)
        val eventDetails = fetchEventDetails(eventId)
        val odds = fetchOddsForEvent(eventId)
        val voting = fetchVotingForEvent(eventId)
        val now = Instant.now()

        saveOddsHistory(eventId, odds, now)
        saveVotesHistory(eventId, voting, now)

        if (odds == null && voting == null && eventDetails == null) {
            logger.warn("No data fetched for handball match $eventId")
            throw RuntimeException("Could not fetch fresh data for match $eventId")
        }

        val refreshedTournamentId = eventDetails?.tournament?.id?.takeIf { it > 0 } ?: existingData.tournamentId
        val refreshedTournamentName =
            eventDetails?.tournament?.name?.takeIf { it.isNotBlank() } ?: existingData.tournamentName
        val refreshedCategoryName =
            eventDetails?.tournament?.category?.name?.takeIf { it.isNotBlank() } ?: existingData.categoryName
        val refreshedSeasonId = eventDetails?.season?.id

        val updatedData = existingData.copy(
            tournamentId = refreshedTournamentId,
            tournamentName = refreshedTournamentName,
            seasonId = refreshedSeasonId ?: existingData.seasonId,
            categoryName = refreshedCategoryName,
            homeScore = eventDetails?.homeScore?.current,
            awayScore = eventDetails?.awayScore?.current,
            oddsHome = odds?.home,
            oddsDraw = odds?.draw,
            oddsAway = odds?.away,
            votingHome = voting?.home,
            votingDraw = voting?.draw,
            votingAway = voting?.away,
            votingTotal = voting?.total,
            statusType = eventDetails?.status?.type ?: existingData.statusType,
            statusDescription = eventDetails?.status?.description ?: existingData.statusDescription,
            lastUpdated = now
        )
        withContext(Dispatchers.IO) {
            dailyHandballMatchDataRepository.save(updatedData)
        }
        logger.info("Updated handball match $eventId - tournamentId: ${updatedData.tournamentId}, tournamentName: ${updatedData.tournamentName}")

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
            homeScore = eventDetails?.homeScore,
            awayScore = eventDetails?.awayScore,
            status = Status(
                type = updatedData.statusType,
                description = updatedData.statusDescription
            ),
            tournament = Tournament(
                id = updatedData.tournamentId,
                name = updatedData.tournamentName,
                category = Category(
                    name = updatedData.categoryName,
                    country = null
                )
            ),
            season = updatedData.seasonId?.let { Season(id = it, name = "") },
            vote = null,
            odds = odds,
            voting = voting,
            lastUpdated = now.epochSecond
        )
    }

    suspend fun getTournamentStandings(tournamentId: Long, seasonId: Long): StandingsResponse? {
        return try {
            val response = webClient
                .get()
                .uri("/tournament/$tournamentId/season/$seasonId/standings/total")
                .retrieve()
                .awaitBody<StandingsResponse>()

            logger.info("Fetched standings for tournament $tournamentId, season $seasonId: ${response.standings?.size} groups")
            response
        } catch (_: WebClientResponseException.NotFound) {
            logger.warn("Standings not found for tournament $tournamentId, season $seasonId (404)")
            null
        } catch (e: Exception) {
            logger.warn("Could not fetch standings for tournament $tournamentId, season $seasonId: ${e.message}")
            null
        }
    }

    private suspend fun saveOddsHistory(eventId: Long, odds: Odds?, timestamp: Instant) {
        odds?.takeIf { it.home != null || it.draw != null || it.away != null }?.let {
            // Check if the latest odds entry has the same values
            val latestOdds = withContext(Dispatchers.IO) {
                matchOddsHistoryRepository.findFirstByEventIdOrderByRecordedAtDesc(eventId)
            }

            // Only save if values have changed
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

    private suspend fun saveVotesHistory(eventId: Long, voting: Voting?, timestamp: Instant) {
        voting?.takeIf { it.home != null || it.draw != null || it.away != null }?.let {
            // Check if the latest votes entry has the same values
            val latestVotes = withContext(Dispatchers.IO) {
                matchVotesHistoryRepository.findFirstByEventIdOrderByRecordedAtDesc(eventId)
            }

            // Only save if values have changed
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

    suspend fun getOddsHistory(eventId: Long): List<jp.odds.entity.MatchOddsHistory> {
        return withContext(Dispatchers.IO) {
            matchOddsHistoryRepository.findByEventIdOrderByRecordedAtAsc(eventId)
        }
    }

    suspend fun getVotesHistory(eventId: Long): List<jp.odds.entity.MatchVotesHistory> {
        return withContext(Dispatchers.IO) {
            matchVotesHistoryRepository.findByEventIdOrderByRecordedAtAsc(eventId)
        }
    }

    private fun convertScheduledEventToSofascoreEvent(scheduledEvent: ScheduledEvent): SofascoreEvent {
        return SofascoreEvent(
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
    }
}
