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
class FootballMatchesController(private val sofascoreService: SofascoreService) {

    @GetMapping("/matches/today")
    suspend fun getTodayMatches(): List<SofascoreEvent> = sofascoreService.getTodayFootballMatches()

    @GetMapping("/matches/date/{date}")
    suspend fun getMatchesByDate(@PathVariable date: String): List<SofascoreEvent> = sofascoreService.getFootballMatchesByDate(LocalDate.parse(date))

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
            sofascoreService.refreshSingleMatch(eventId)
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message, e)
        } catch (e: Exception) {
            throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Failed to refresh match $eventId: ${e.message}",
                e
            )
        }
    }

    @GetMapping("/tournament/{tournamentId}/seasons")
    suspend fun getTournamentSeasons(@PathVariable tournamentId: Long): TournamentSeasonsResponse? = sofascoreService.getTournamentSeasons(tournamentId)

    @GetMapping("/tournament/{tournamentId}/season/{seasonId}/standings")
    suspend fun getTournamentStandings(
        @PathVariable tournamentId: Long,
        @PathVariable seasonId: Long
    ): StandingsResponse? = sofascoreService.getTournamentStandings(tournamentId, seasonId)
}
