package jp.odds.service

import jp.odds.dto.LeagueStatistics
import jp.odds.dto.WinningMatchStatistics
import jp.odds.dto.WinningMatchStatisticsByLeague
import jp.odds.entity.DailyFootballMatchData
import jp.odds.repository.DailyFootballMatchDataRepository
import jp.odds.repository.MatchOddsHistoryRepository
import jp.odds.repository.MatchVotesHistoryRepository
import jp.odds.service.response.model.ScheduledEventsResponse
import jp.odds.service.response.model.SofascoreEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
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

        val uri = "/sport/football/scheduled-events/$dateStr"
        logger.info("Fetching football matches for date: $dateStr from URI: $uri (forceRefresh=${true})")

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

            val filteredEvents = if (includeAllLeagues) {
                events.map { event ->
                    event.copy(isTopLeague = isTopLeague(event))
                }
            } else {
                events.filter { isTopLeague(it) }.map { event ->
                    event.copy(isTopLeague = true)
                }
            }
            logger.info("Filtered to ${filteredEvents.size} ${if (includeAllLeagues) "total" else "top league"} matches for $dateStr")

            val now = Instant.now()
            filteredEvents.forEach { event ->
                event.odds = fetchOddsForEvent(event.id)
                event.voting = fetchVotingForEvent(event.id)
                event.lastUpdated = now.epochSecond

                saveOddsHistory(event.id, event.odds, now)
                saveVotesHistory(event.id, event.voting, now)
            }

            saveMatchesToDatabase(date, filteredEvents)

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

    private fun isTopLeague(event: SofascoreEvent): Boolean =
        event.eventFilters?.level?.contains("top-competitions") ?: false

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

    private suspend fun saveMatchesToDatabase(matchDate: LocalDate, events: List<SofascoreEvent>) {
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
            dailyFootballMatchDataRepository.saveAll(entities)
        }
        logger.info("Saved ${entities.size} matches to database for date $matchDate")
    }

    suspend fun getWinningMatchStatistics(): WinningMatchStatistics = withContext(Dispatchers.IO) {
        val pageable = PageRequest.of(0, 500)
        val finishedMatches = dailyFootballMatchDataRepository.findTop500FinishedMatches(pageable)

        val winningMatchData = finishedMatches.mapNotNull { match ->
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
                Pair(winningVote, parseOdds(winningOdds))
            } else {
                null
            }
        }

        if (winningMatchData.isEmpty()) {
            return@withContext WinningMatchStatistics(
                averageVote = 0.0,
                averageOdds = 0.0,
                totalMatches = 0
            )
        }

        val avgVote = winningMatchData.map { it.first }.average()
        val avgOdds = winningMatchData.map { it.second }.average()

        WinningMatchStatistics(
            averageVote = avgVote,
            averageOdds = avgOdds,
            totalMatches = winningMatchData.size
        )
    }

    suspend fun getWinningMatchStatisticsByLeague(countryName: String? = null, topLeaguesOnly: Boolean = false): WinningMatchStatisticsByLeague = withContext(Dispatchers.IO) {
        val pageable = PageRequest.of(0, 500)
        val finishedMatches = when {
            countryName != null && topLeaguesOnly -> dailyFootballMatchDataRepository.findTop500FinishedMatchesByCountryAndTopLeague(countryName, true, pageable)
            countryName != null -> dailyFootballMatchDataRepository.findTop500FinishedMatchesByCountry(countryName, pageable)
            topLeaguesOnly -> dailyFootballMatchDataRepository.findTop500FinishedMatchesByTopLeague(true, pageable)
            else -> dailyFootballMatchDataRepository.findTop500FinishedMatches(pageable)
        }

        data class MatchWinningData(
            val tournamentId: Long,
            val tournamentName: String,
            val vote: Int,
            val odds: Double
        )

        val winningMatchData = finishedMatches.mapNotNull { match ->
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

        val overall = if (winningMatchData.isEmpty()) {
            WinningMatchStatistics(
                averageVote = 0.0,
                averageOdds = 0.0,
                totalMatches = 0
            )
        } else {
            WinningMatchStatistics(
                averageVote = winningMatchData.map { it.vote }.average(),
                averageOdds = winningMatchData.map { it.odds }.average(),
                totalMatches = winningMatchData.size
            )
        }

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

        WinningMatchStatisticsByLeague(
            overall = overall,
            byLeague = byLeague
        )
    }

    private fun parseOdds(fractionalOdds: String): Double {
        return try {
            val parts = fractionalOdds.split('/')
            if (parts.size == 2) {
                val numerator = parts[0].toDouble()
                val denominator = parts[1].toDouble()
                (numerator / denominator) + 1.0
            } else {
                0.0
            }
        } catch (e: Exception) {
            0.0
        }
    }
}
