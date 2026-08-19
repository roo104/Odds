package jp.odds.service

import jp.odds.dto.ProfitabilityResponse
import jp.odds.dto.WinningMatchStatistics
import jp.odds.dto.WinningMatchStatisticsByLeague
import jp.odds.entity.DailyFootballMatchData
import jp.odds.entity.SportType
import jp.odds.repository.DailyFootballMatchDataRepository
import jp.odds.repository.MatchOddsHistoryRepository
import jp.odds.repository.MatchVotesHistoryRepository
import jp.odds.service.response.model.SofascoreEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class FootballService(
    webClientBuilder: WebClient.Builder,
    private val dailyFootballMatchDataRepository: DailyFootballMatchDataRepository,
    matchOddsHistoryRepository: MatchOddsHistoryRepository,
    matchVotesHistoryRepository: MatchVotesHistoryRepository
) : BaseSofascoreService(webClientBuilder, matchOddsHistoryRepository, matchVotesHistoryRepository) {

    private companion object {
        const val LEAGUE_SEED_LOOKBACK_DAYS = 400L
    }

    override val sport: SportType = SportType.Football

    suspend fun getTodayMatches(): List<SofascoreEvent> {
        val tomorrow = LocalDate.now().plusDays(1)
        return getMatchesByDate(tomorrow)
    }

    suspend fun getMatchesByDate(date: LocalDate, forceRefresh: Boolean = false, includeAllLeagues: Boolean = false): List<SofascoreEvent> {
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)

        if (!forceRefresh) {
            logger.info("Loading football matches for $dateStr from database")
            return loadMatchesFromDatabase(date)
        }

        return try {
            // Leagues we already collect, widened with the config-pinned extras and Sofascore's
            // current top competitions so a new league (or an empty database) still gets picked up.
            val leagues = discoveryLeagues(loadTrackedLeagues(onlyTopLeagues = !includeAllLeagues))
            if (leagues.isEmpty()) {
                logger.warn("No football leagues to poll for $dateStr - database empty and top-competitions lookup failed")
                return emptyList()
            }
            logger.info("Fetching football matches for date: $dateStr across ${leagues.size} tracked leagues")

            val filteredEvents = fetchEventsForDate(date, leagues, includeAllLeagues)
            logger.info("Fetched ${filteredEvents.size} ${if (includeAllLeagues) "total" else "top league"} matches for $dateStr")

            val now = Instant.now()
            val eventStatistics = mutableMapOf<Long, jp.odds.service.response.model.EventStatistics?>()
            
            filteredEvents.forEach { event ->
                event.odds = fetchOddsForEvent(event.id)
                event.voting = fetchVotingForEvent(event.id)
                event.lastUpdated = now.epochSecond

                saveOddsHistory(event.id, event.odds, now)
                saveVotesHistory(event.id, event.voting, now)
                
                val isMatchFinished = event.status.type.lowercase() == "finished" ||
                        event.status.description.lowercase() in listOf("finished", "ended", "full time")
                if (isMatchFinished) {
                    eventStatistics[event.id] = fetchEventStatistics(event.id)
                }
            }

            saveMatchesToDatabase(date, filteredEvents, eventStatistics)

            filteredEvents
        } catch (e: Exception) {
            logger.error("Error fetching football matches: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun refreshSingleMatch(eventId: Long): SofascoreEvent {
        logger.info("Refreshing single football match with eventId: $eventId")

        val existingData = withContext(Dispatchers.IO) {
            dailyFootballMatchDataRepository.findByEventId(eventId)
        } ?: throw IllegalArgumentException("Match $eventId not found in database")

        val eventDetails = fetchEventDetails(eventId)
        val odds = fetchOddsForEvent(eventId)
        val voting = fetchVotingForEvent(eventId)
        val now = Instant.now()

        saveOddsHistory(eventId, odds, now)
        saveVotesHistory(eventId, voting, now)

        val isMatchFinished = eventDetails?.status?.type?.lowercase() == "finished" ||
                eventDetails?.status?.description?.lowercase() in listOf("finished", "ended", "full time")
        val statistics = if (isMatchFinished) fetchEventStatistics(eventId) else null
        // Sofascore drops the clock the moment a match ends, so a null reading off a fetch that
        // did land means "no longer live"; only a failed fetch leaves the last one standing.
        val liveClock = readLiveClock(eventDetails)

        if (odds == null && voting == null && eventDetails == null) {
            logger.warn("No data fetched for match $eventId")
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
            homeYellowCards = statistics?.homeYellowCards ?: existingData.homeYellowCards,
            homeRedCards = statistics?.homeRedCards ?: existingData.homeRedCards,
            awayYellowCards = statistics?.awayYellowCards ?: existingData.awayYellowCards,
            awayRedCards = statistics?.awayRedCards ?: existingData.awayRedCards,
            oddsHome = odds?.home,
            oddsDraw = odds?.draw,
            oddsAway = odds?.away,
            votingHome = voting?.home,
            votingDraw = voting?.draw,
            votingAway = voting?.away,
            votingTotal = voting?.total,
            statusType = eventDetails?.status?.type ?: existingData.statusType,
            statusDescription = eventDetails?.status?.description ?: existingData.statusDescription,
            liveElapsedMinutes = if (eventDetails != null) liveClock?.elapsedMinutes else existingData.liveElapsedMinutes,
            liveMinutesRemaining = if (eventDetails != null) liveClock?.minutesRemaining else existingData.liveMinutesRemaining,
            lastUpdated = now
        )
        withContext(Dispatchers.IO) {
            dailyFootballMatchDataRepository.save(updatedData)
        }
        logger.info("Updated match $eventId - tournamentId: ${updatedData.tournamentId}, tournamentName: ${updatedData.tournamentName}")

        return mapDataToSofascoreEvent(
            eventId = updatedData.eventId,
            startTimestamp = updatedData.startTimestamp,
            homeTeamId = updatedData.homeTeamId,
            homeTeamName = updatedData.homeTeamName,
            awayTeamId = updatedData.awayTeamId,
            awayTeamName = updatedData.awayTeamName,
            homeScore = updatedData.homeScore,
            awayScore = updatedData.awayScore,
            statusType = updatedData.statusType,
            statusDescription = updatedData.statusDescription,
            tournamentId = updatedData.tournamentId,
            tournamentName = updatedData.tournamentName,
            categoryName = updatedData.categoryName,
            countryName = updatedData.countryName,
            seasonId = updatedData.seasonId,
            oddsHome = odds?.home,
            oddsDraw = odds?.draw,
            oddsAway = odds?.away,
            votingHome = voting?.home,
            votingDraw = voting?.draw,
            votingAway = voting?.away,
            votingTotal = voting?.total,
            lastUpdated = now,
            isTopLeague = updatedData.isTopLeague
        )
    }

    /**
     * Leagues to poll, seeded from what we have already collected. The old scheduled-events feed
     * tagged each event with `eventFilters.level`, which the per-tournament feed does not carry -
     * so a league's top-league status now comes from the row we stored for it.
     */
    private suspend fun loadTrackedLeagues(onlyTopLeagues: Boolean): List<TrackedLeague> =
        withContext(Dispatchers.IO) {
            val since = LocalDate.now().minusDays(LEAGUE_SEED_LOOKBACK_DAYS)
            dailyFootballMatchDataRepository.findTrackedLeagues(onlyTopLeagues, since).map { row ->
                val uniqueTournamentId = (row.getOrNull(4) as Number?)?.toLong()
                TrackedLeague(
                    // Prefer the stable league id; rows written before it was stored only have the
                    // season-scoped one, which the day's tournament list also carries.
                    tournamentId = uniqueTournamentId ?: (row[0] as Number).toLong(),
                    tournamentName = row[1] as String,
                    // MySQL hands MAX() over a BOOLEAN column back as a Boolean, but the aggregate
                    // is numeric on other engines - accept either rather than blowing up on a cast.
                    isTopLeague = when (val flag = row.getOrNull(3)) {
                        is Boolean -> flag
                        is Number -> flag.toInt() != 0
                        else -> true
                    },
                    isUniqueTournament = uniqueTournamentId != null
                )
            }
        }

    private suspend fun loadMatchesFromDatabase(matchDate: LocalDate): List<SofascoreEvent> {
        val startOfDay = matchDate.atStartOfDay(java.time.ZoneId.systemDefault()).toEpochSecond()
        val endOfDay = matchDate.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toEpochSecond()

        val dailyData = withContext(Dispatchers.IO) {
            dailyFootballMatchDataRepository.findByStartTimestampBetween(startOfDay, endOfDay)
        }
        logger.info("Loaded ${dailyData.size} matches from database for date $matchDate (timestamp range: $startOfDay - $endOfDay)")

        return dailyData.map { data ->
            mapDataToSofascoreEvent(
                eventId = data.eventId,
                startTimestamp = data.startTimestamp,
                homeTeamId = data.homeTeamId,
                homeTeamName = data.homeTeamName,
                awayTeamId = data.awayTeamId,
                awayTeamName = data.awayTeamName,
                homeScore = data.homeScore,
                awayScore = data.awayScore,
                statusType = data.statusType,
                statusDescription = data.statusDescription,
                tournamentId = data.tournamentId,
                tournamentName = data.tournamentName,
                categoryName = data.categoryName,
                countryName = data.countryName,
                seasonId = data.seasonId,
                oddsHome = data.oddsHome,
                oddsDraw = data.oddsDraw,
                oddsAway = data.oddsAway,
                votingHome = data.votingHome,
                votingDraw = data.votingDraw,
                votingAway = data.votingAway,
                votingTotal = data.votingTotal,
                lastUpdated = data.lastUpdated,
                isTopLeague = data.isTopLeague
            )
        }
    }

    private suspend fun saveMatchesToDatabase(
        matchDate: LocalDate, 
        events: List<SofascoreEvent>,
        eventStatistics: Map<Long, jp.odds.service.response.model.EventStatistics?> = emptyMap()
    ) {
        val now = Instant.now()

        val existingRecords = if (events.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                dailyFootballMatchDataRepository.findByEventIdIn(events.map { it.id })
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
            val stats = eventStatistics[event.id]
            val clock = readLiveClock(event)
            DailyFootballMatchData(
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
                uniqueTournamentId = event.tournament.uniqueTournamentId,
                seasonId = event.season?.id,
                categoryName = event.tournament.category.name,
                countryName = event.tournament.category.country?.name,
                homeScore = event.homeScore?.current,
                awayScore = event.awayScore?.current,
                homeYellowCards = stats?.homeYellowCards,
                homeRedCards = stats?.homeRedCards,
                awayYellowCards = stats?.awayYellowCards,
                awayRedCards = stats?.awayRedCards,
                oddsHome = event.odds?.home,
                oddsDraw = event.odds?.draw,
                oddsAway = event.odds?.away,
                votingHome = event.voting?.home,
                votingDraw = event.voting?.draw,
                votingAway = event.voting?.away,
                votingTotal = event.voting?.total,
                statusType = event.status.type,
                statusDescription = event.status.description,
                liveElapsedMinutes = clock?.elapsedMinutes,
                liveMinutesRemaining = clock?.minutesRemaining,
                lastUpdated = now,
                isTopLeague = event.isTopLeague ?: true
            )
        }

        withContext(Dispatchers.IO) {
            dailyFootballMatchDataRepository.saveAll(entities)
        }
        logger.info("Saved ${entities.size} matches to database for date $matchDate")
    }

    suspend fun getWinningMatchStatistics(): WinningMatchStatistics = withContext(Dispatchers.IO) {
        val pageable = PageRequest.of(0, 1000)
        val finishedMatches = dailyFootballMatchDataRepository.findFinishedMatches(pageable)
        val winningMatchData = extractWinningMatchData(finishedMatches)
        calculateWinningStatistics(winningMatchData)
    }

    suspend fun getWinningMatchStatisticsByLeague(countryName: String? = null, topLeaguesOnly: Boolean = false): WinningMatchStatisticsByLeague = withContext(Dispatchers.IO) {
        val pageable = PageRequest.of(0, 1000)
        val finishedMatches = when {
            countryName != null && topLeaguesOnly -> dailyFootballMatchDataRepository.findFinishedMatchesByCountryAndTopLeague(countryName, true, pageable)
            countryName != null -> dailyFootballMatchDataRepository.findFinishedMatchesByCountry(countryName, pageable)
            topLeaguesOnly -> dailyFootballMatchDataRepository.findFinishedMatchesByTopLeague(true, pageable)
            else -> dailyFootballMatchDataRepository.findFinishedMatches(pageable)
        }

        val winningMatchData = extractWinningMatchData(finishedMatches)
        calculateStatisticsByLeague(winningMatchData)
    }

    suspend fun getProfitableThresholds(countryName: String? = null, topLeaguesOnly: Boolean = false): ProfitabilityResponse = withContext(Dispatchers.IO) {
        val pageable = PageRequest.of(0, 1000)
        val finishedMatches = when {
            countryName != null && topLeaguesOnly -> dailyFootballMatchDataRepository.findFinishedMatchesByCountryAndTopLeague(countryName, true, pageable)
            countryName != null -> dailyFootballMatchDataRepository.findFinishedMatchesByCountry(countryName, pageable)
            topLeaguesOnly -> dailyFootballMatchDataRepository.findFinishedMatchesByTopLeague(true, pageable)
            else -> dailyFootballMatchDataRepository.findFinishedMatches(pageable)
        }

        val bettingData = extractBettingData(finishedMatches)
        calculateProfitableThresholds(bettingData, if (countryName != null) finishedMatches else null)
    }

    data class LeagueInfo(
        val tournamentName: String,
        val countryName: String,
        val tournamentId: Long,
        val seasonId: Long
    )

    suspend fun getTopLeaguesForCurrentYear(): List<LeagueInfo> = withContext(Dispatchers.IO) {
        dailyFootballMatchDataRepository.findTopLeaguesForCurrentYear().map { row ->
            LeagueInfo(
                tournamentName = row[0] as String,
                countryName = row[1] as String,
                tournamentId = (row[2] as Number).toLong(),
                seasonId = (row[3] as Number).toLong()
            )
        }
    }

    suspend fun getAvailableCountriesForCurrentYear(): List<String> = withContext(Dispatchers.IO) {
        dailyFootballMatchDataRepository.findAvailableCountriesForCurrentYear()
    }

    suspend fun getTopLeaguesByCountry(countryName: String): List<LeagueInfo> = withContext(Dispatchers.IO) {
        dailyFootballMatchDataRepository.findTopLeaguesByCountry(countryName).map { row ->
            LeagueInfo(
                tournamentName = row[0] as String,
                countryName = row[1] as String,
                tournamentId = (row[2] as Number).toLong(),
                seasonId = (row[3] as Number).toLong()
            )
        }
    }
}
