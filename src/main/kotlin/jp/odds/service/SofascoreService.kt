package jp.odds.service

import jp.odds.dto.*
import jp.odds.entity.DailyMatchData
import jp.odds.repository.DailyMatchDataRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class SofascoreService(
    webClientBuilder: WebClient.Builder,
    private val dailyMatchDataRepository: DailyMatchDataRepository,
    @Value("\${odds.sofascore.allowed-leagues:}") private val allowedLeaguesConfig: String
) {
    private val logger = LoggerFactory.getLogger(SofascoreService::class.java)
    private val allowedLeagueTokensByCountry = parseAllowedLeagues(allowedLeaguesConfig)

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

            val filteredEvents = filterEventsByTopLeagues(response.events)
            logger.info("Filtered to ${filteredEvents.size} top league matches for $dateStr")

            // Fetch odds and voting for each event with delays (sequentially to avoid rate limiting)
            val now = java.time.Instant.now()
            filteredEvents.forEach { event ->
                kotlinx.coroutines.delay(500L) // 500ms delay between each event
                event.odds = fetchOddsForEvent(event.id)
                event.voting = fetchVotingForEvent(event.id)
                event.lastUpdated = now.epochSecond
            }

            // Save to database (will update existing records or insert new ones)
            saveMatchesToDatabase(date, filteredEvents)

            filteredEvents
        } catch (e: Exception) {
            logger.error("Error fetching football matches: ${e.message}", e)
            emptyList()
        }
    }

    private fun loadMatchesFromDatabase(matchDate: LocalDate): List<SofascoreEvent> {
        val dailyData = dailyMatchDataRepository.findByMatchDate(matchDate)
        logger.info("Loaded ${dailyData.size} matches from database for date $matchDate")

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
                        away = data.votingAway,
                        total = data.votingTotal
                    )
                } else null,
                lastUpdated = data.lastUpdated?.epochSecond ?: 0
            )
        }

        return filterEventsByTopLeagues(events)
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
                tournamentId = event.tournament.id,
                tournamentName = event.tournament.name,
                categoryName = event.tournament.category.name,
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

        dailyMatchDataRepository.saveAll(entities)
        logger.info("Saved ${entities.size} matches to database for date $matchDate")
    }

    private fun filterEventsByTopLeagues(events: List<SofascoreEvent>): List<SofascoreEvent> {
        if (events.isEmpty()) {
            return events
        }

        return events.filter { event ->
            val tournamentName = normalize(event.tournament.name)
            val countryName = normalize(event.tournament.category.country?.name ?: event.tournament.category.name)
            val allowedTokens = allowedLeagueTokensByCountry[countryName] ?: return@filter false
            allowedTokens.any { token -> tournamentName == token }
        }
    }

    private fun parseAllowedLeagues(config: String): Map<String, Set<String>> {
        if (config.isBlank()) {
            return defaultAllowedLeagueTokens()
        }

        val entries = config.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val parsed = mutableMapOf<String, MutableSet<String>>()

        entries.forEach { entry ->
            val parts = entry.split(":", limit = 2)
            if (parts.size != 2) {
                logger.warn("Ignoring invalid allowed league entry: '$entry' (expected Country:League)")
                return@forEach
            }
            val country = normalize(parts[0])
            val league = normalize(parts[1])
            if (country.isNotBlank() && league.isNotBlank()) {
                parsed.getOrPut(country) { mutableSetOf() }.add(league)
            }
        }

        if (parsed.isEmpty()) {
            logger.warn("No valid allowed leagues configured, using defaults")
            return defaultAllowedLeagueTokens()
        }

        return parsed
    }

    private fun defaultAllowedLeagueTokens(): Map<String, Set<String>> {
        return mapOf(
            "albania" to setOf("kategoria superiore", "albanian cup"),
            "andorra" to setOf("primera divisio", "copa constitucio"),
            "armenia" to setOf("premier league", "armenian cup"),
            "austria" to setOf("bundesliga", "austrian cup"),
            "azerbaijan" to setOf("premier league", "azerbaijan cup"),
            "belarus" to setOf("premier league", "belarusian cup"),
            "belgium" to setOf("jupiler pro league", "belgian cup"),
            "bosnia and herzegovina" to setOf("premier league", "bosnia and herzegovina cup"),
            "bulgaria" to setOf("parva liga", "bulgarian cup"),
            "croatia" to setOf("1. hnl", "prva hnl", "croatian cup"),
            "cyprus" to setOf("first division", "cypriot cup"),
            "czech republic" to setOf("first league", "1. liga", "czech cup"),
            "denmark" to setOf("superliga", "dbu pokalen"),
            "england" to setOf("premier league", "fa cup"),
            "estonia" to setOf("meistriliiga", "estonian cup"),
            "faroe islands" to setOf("premier league", "faroe islands cup"),
            "finland" to setOf("veikkausliiga", "finnish cup"),
            "france" to setOf("ligue 1", "coupe de france"),
            "georgia" to setOf("erovnuli liga", "georgian cup"),
            "germany" to setOf("bundesliga", "dfb pokal"),
            "gibraltar" to setOf("national league", "gibraltar cup"),
            "greece" to setOf("super league", "greek cup"),
            "hungary" to setOf("nb i", "hungarian cup"),
            "iceland" to setOf("besta deild", "icelandic cup"),
            "ireland" to setOf("premier division", "fai cup"),
            "israel" to setOf("premier league", "israel state cup"),
            "italy" to setOf("serie a", "coppa italia"),
            "kazakhstan" to setOf("premier league", "kazakhstan cup"),
            "kosovo" to setOf("superliga", "kosovar cup"),
            "latvia" to setOf("virsliga", "latvian cup"),
            "lithuania" to setOf("a lyga", "lithuanian cup"),
            "luxembourg" to setOf("national division", "luxembourg cup"),
            "malta" to setOf("premier league", "maltese cup"),
            "moldova" to setOf("super liga", "moldovan cup"),
            "montenegro" to setOf("first league", "montenegrin cup"),
            "netherlands" to setOf("eredivisie", "knvb beker"),
            "north macedonia" to setOf("first league", "macedonian cup"),
            "northern ireland" to setOf("premiership", "irish cup"),
            "norway" to setOf("eliteserien", "nm cupen"),
            "poland" to setOf("ekstraklasa", "polish cup"),
            "portugal" to setOf("primeira liga", "taca de portugal"),
            "romania" to setOf("liga i", "romanian cup"),
            "russia" to setOf("premier league", "russian cup"),
            "san marino" to setOf("campionato sammarinese", "coppa titano"),
            "scotland" to setOf("premiership", "scottish cup"),
            "serbia" to setOf("super liga", "serbian cup"),
            "slovakia" to setOf("super liga", "slovak cup"),
            "slovenia" to setOf("prvaliga", "slovenian cup"),
            "spain" to setOf("laliga", "copa del rey"),
            "sweden" to setOf("allsvenskan", "svenska cupen"),
            "switzerland" to setOf("super league", "swiss cup"),
            "turkey" to setOf("super lig", "turkish cup"),
            "ukraine" to setOf("premier league", "ukrainian cup"),
            "wales" to setOf("premier league", "welsh cup"),
            "argentina" to setOf("primera division", "liga profesional", "copa argentina"),
            "bolivia" to setOf("division profesional", "copa bolivia"),
            "brazil" to setOf("serie a", "brasileirao", "copa do brasil"),
            "chile" to setOf("primera division", "copa chile"),
            "colombia" to setOf("primera a", "copa colombia"),
            "ecuador" to setOf("liga pro", "copa ecuador"),
            "paraguay" to setOf("primera division", "copa paraguay"),
            "peru" to setOf("liga 1", "copa peru"),
            "uruguay" to setOf("primera division", "copa uruguay"),
            "venezuela" to setOf("primera division", "copa venezuela")
        )
    }

    private fun normalize(value: String?): String {
        val safeValue = value ?: ""
        val normalized = Normalizer.normalize(safeValue, Normalizer.Form.NFD)
        return normalized.replace("\\p{M}+".toRegex(), "").lowercase().trim()
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

    private suspend fun fetchEventDetails(eventId: Long): SofascoreEvent? {
        return try {
            val response = webClient
                .get()
                .uri("/event/$eventId")
                .retrieve()
                .awaitBody<EventDetailsResponse>()

            logger.debug("Fetched event details for $eventId: status=${response.event?.status?.description}")
            response.event
        } catch (e: org.springframework.web.reactive.function.client.WebClientResponseException.NotFound) {
            logger.debug("Event $eventId not found (404)")
            null
        } catch (e: Exception) {
            logger.warn("Could not fetch event details for $eventId: ${e.message}")
            null
        }
    }

    suspend fun refreshSingleMatch(eventId: Long, matchDate: LocalDate): SofascoreEvent {
        logger.info("Refreshing single match with eventId: $eventId")

        // Find the existing match in database to get basic info
        val existingData = dailyMatchDataRepository.findByMatchDate(matchDate)
            .find { it.eventId == eventId }
            ?: throw IllegalArgumentException("Match $eventId not found in database for date $matchDate")

        // Fetch fresh event details, odds and voting data
        kotlinx.coroutines.delay(500L)
        val eventDetails = fetchEventDetails(eventId)
        val odds = fetchOddsForEvent(eventId)
        val voting = fetchVotingForEvent(eventId)

        // Throw error if we couldn't fetch any fresh data
        if (odds == null && voting == null && eventDetails == null) {
            logger.warn("No data fetched for match $eventId")
            throw RuntimeException("Could not fetch fresh data for match $eventId")
        }

        // Update in database with fresh data
        val now = java.time.Instant.now()
        val tournamentIdFixes = mapOf<Long, Long>(
            33L to 23L  // Tournament 33 (volleyball) -> Tournament 23 (Serie A)
        )
        val refreshedTournamentId = eventDetails?.tournament?.id?.takeIf { it > 0 } ?: existingData.tournamentId
        val correctedTournamentId = if ((eventDetails?.tournament?.name ?: existingData.tournamentName) == "Serie A") {
            tournamentIdFixes[refreshedTournamentId] ?: refreshedTournamentId
        } else {
            refreshedTournamentId
        }
        val refreshedTournamentName = eventDetails?.tournament?.name?.takeIf { it.isNotBlank() } ?: existingData.tournamentName
        val refreshedCategoryName = eventDetails?.tournament?.category?.name?.takeIf { it.isNotBlank() } ?: existingData.categoryName

        val updatedData = existingData.copy(
            tournamentId = correctedTournamentId,
            tournamentName = refreshedTournamentName,
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
        dailyMatchDataRepository.save(updatedData)
        logger.info("Updated match $eventId - tournamentId: ${updatedData.tournamentId}, tournamentName: ${updatedData.tournamentName}")

        // Try to fetch and log standings only if we got fresh event details with tournament info
        if (eventDetails != null && updatedData.tournamentId > 0) {
            try {
                val seasonsResponse = getTournamentSeasons(updatedData.tournamentId)
                if (seasonsResponse?.seasons?.isNotEmpty() == true) {
                    val currentSeason = seasonsResponse.seasons[0]

                    // Skip if season name indicates non-football sport (volleyball, basketball, etc.)
                    val seasonName = currentSeason.name ?: ""
                    if (seasonName.contains("FIVB", ignoreCase = true) ||
                        seasonName.contains("Volleyball", ignoreCase = true) ||
                        seasonName.contains("Basketball", ignoreCase = true) ||
                        seasonName.contains("World Championship", ignoreCase = true)) {
                        logger.warn("Skipping standings for match $eventId: tournament ${updatedData.tournamentId} (${updatedData.tournamentName}) has non-football season: $seasonName")
                    } else {
                        logger.info("Found season for tournament ${updatedData.tournamentId}: ${currentSeason.name} (ID: ${currentSeason.id})")
                        val standings = getTournamentStandings(updatedData.tournamentId, currentSeason.id)
                        if (standings?.standings?.isNotEmpty() == true) {
                            val totalRows = standings.standings.sumOf { it.rows?.size ?: 0 }
                            logger.info("Fetched standings for tournament ${updatedData.tournamentId}: ${standings.standings.size} groups, $totalRows teams")
                        } else {
                            logger.info("No standings found for tournament ${updatedData.tournamentId}, season ${currentSeason.id}")
                        }
                    }
                } else {
                    logger.info("No seasons found for tournament ${updatedData.tournamentId}")
                }
            } catch (e: Exception) {
                logger.warn("Error fetching standings for tournament ${updatedData.tournamentId}: ${e.message}")
            }
        } else {
            logger.debug("Tournament ID is 0 for match $eventId, cannot fetch standings")
        }

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
            vote = null,
            odds = odds,
            voting = voting,
            lastUpdated = now.epochSecond
        )
    }

    suspend fun getTournamentSeasons(tournamentId: Long): TournamentSeasonsResponse? {
        return try {
            val response = webClient
                .get()
                .uri("/unique-tournament/$tournamentId/seasons")
                .retrieve()
                .awaitBody<TournamentSeasonsResponse>()

            logger.info("Fetched seasons for tournament $tournamentId: ${response.seasons?.size} seasons")
            response
        } catch (e: org.springframework.web.reactive.function.client.WebClientResponseException.NotFound) {
            logger.debug("Tournament $tournamentId not found (404)")
            null
        } catch (e: Exception) {
            logger.warn("Could not fetch seasons for tournament $tournamentId: ${e.message}")
            null
        }
    }

    suspend fun getTournamentStandings(tournamentId: Long, seasonId: Long): StandingsResponse? {
        return try {
            val response = webClient
                .get()
                .uri("/unique-tournament/$tournamentId/season/$seasonId/standings/total")
                .retrieve()
                .awaitBody<StandingsResponse>()

            logger.info("Fetched standings for tournament $tournamentId, season $seasonId: ${response.standings?.size} groups")
            response
        } catch (e: org.springframework.web.reactive.function.client.WebClientResponseException.NotFound) {
            logger.debug("Standings not found for tournament $tournamentId, season $seasonId (404)")
            null
        } catch (e: Exception) {
            logger.warn("Could not fetch standings for tournament $tournamentId, season $seasonId: ${e.message}")
            null
        }
    }

    suspend fun fixAllTournamentData(): Map<String, Any> {
        logger.info("Starting to fix all tournament data")
        val allRecords = dailyMatchDataRepository.findAll()
        var updated = 0
        var failed = 0

        allRecords.forEach { record ->
            try {
                kotlinx.coroutines.delay(500L) // Rate limiting
                val eventDetails = fetchEventDetails(record.eventId)
                if (eventDetails != null) {
                    val updatedRecord = record.copy(
                        tournamentId = eventDetails.tournament.id,
                        tournamentName = eventDetails.tournament.name,
                        categoryName = eventDetails.tournament.category.name
                    )
                    dailyMatchDataRepository.save(updatedRecord)
                    logger.info("Fixed record for event ${record.eventId}: ${eventDetails.tournament.name} (ID: ${eventDetails.tournament.id})")
                    updated++
                } else {
                    logger.warn("Could not fetch details for event ${record.eventId}")
                    failed++
                }
            } catch (e: Exception) {
                logger.error("Error fixing record for event ${record.eventId}: ${e.message}")
                failed++
            }
        }

        logger.info("Finished fixing tournament data: $updated updated, $failed failed")
        return mapOf("updated" to updated, "failed" to failed, "total" to allRecords.count())
    }

    fun fixKnownTournamentIds(): Map<String, Any> {
        logger.info("Starting to fix known tournament ID mismatches")
        val allRecords = dailyMatchDataRepository.findAll()
        var updated = 0

        // Map of incorrect tournament IDs to correct ones for known leagues
        val tournamentIdFixes = mapOf<Long, Long>(
            33L to 23L  // Tournament 33 (volleyball) -> Tournament 23 (Serie A)
        )

        allRecords.forEach { record ->
            if (record.tournamentName == "Serie A" && record.tournamentId in tournamentIdFixes.keys) {
                val correctId = tournamentIdFixes[record.tournamentId]!!
                val updatedRecord = record.copy(tournamentId = correctId)
                dailyMatchDataRepository.save(updatedRecord)
                logger.info("Fixed Serie A record for event ${record.eventId}: ${record.tournamentId} -> $correctId")
                updated++
            }
        }

        logger.info("Finished fixing known tournament IDs: $updated updated")
        return mapOf("updated" to updated, "total" to allRecords.count())
    }
}
