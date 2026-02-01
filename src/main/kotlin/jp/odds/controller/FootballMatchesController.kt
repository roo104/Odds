package jp.odds.controller

import jp.odds.dto.SofascoreEvent
import jp.odds.dto.StandingsResponse
import jp.odds.dto.TournamentSeasonsResponse
import jp.odds.service.SofascoreService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate

@RestController
@RequestMapping("/api/football")
@CrossOrigin(origins = ["http://localhost:5173", "http://localhost:3000"])
class FootballMatchesController(
    private val sofascoreService: SofascoreService
) {

    @GetMapping("/matches/today")
    suspend fun getTodayMatches(): List<SofascoreEvent> {
        return sofascoreService.getTodayFootballMatches()
    }

    @GetMapping("/matches/date/{date}")
    suspend fun getMatchesByDate(@PathVariable date: String): List<SofascoreEvent> {
        val localDate = LocalDate.parse(date)
        return sofascoreService.getFootballMatchesByDate(localDate)
    }

    @PostMapping("/matches/date/{date}/refresh")
    suspend fun refreshMatchesByDate(@PathVariable date: String): List<SofascoreEvent> {
        val localDate = LocalDate.parse(date)
        return sofascoreService.getFootballMatchesByDate(localDate, forceRefresh = true)
    }

    @GetMapping("/team/{teamId}/events")
    suspend fun getTeamEvents(@PathVariable teamId: Long): List<SofascoreEvent> {
        return sofascoreService.getTeamEvents(teamId)
    }

    @PostMapping("/matches/{eventId}/refresh")
    suspend fun refreshSingleMatch(
        @PathVariable eventId: Long,
        @RequestParam date: String
    ): SofascoreEvent {
        return try {
            val localDate = LocalDate.parse(date)
            sofascoreService.refreshSingleMatch(eventId, localDate)
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message, e)
        } catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to refresh match $eventId: ${e.message}", e)
        }
    }

    @GetMapping("/tournament/{tournamentId}/seasons")
    suspend fun getTournamentSeasons(@PathVariable tournamentId: Long): TournamentSeasonsResponse? {
        return sofascoreService.getTournamentSeasons(tournamentId)
    }

    @GetMapping("/tournament/{tournamentId}/season/{seasonId}/standings")
    suspend fun getTournamentStandings(
        @PathVariable tournamentId: Long,
        @PathVariable seasonId: Long
    ): StandingsResponse? {
        return sofascoreService.getTournamentStandings(tournamentId, seasonId)
    }

    @PostMapping("/matches/fix-tournament-data")
    suspend fun fixTournamentData(): Map<String, Any> {
        return sofascoreService.fixAllTournamentData()
    }

    @PostMapping("/matches/fix-known-tournament-ids")
    fun fixKnownTournamentIds(): Map<String, Any> {
        return sofascoreService.fixKnownTournamentIds()
    }
}
