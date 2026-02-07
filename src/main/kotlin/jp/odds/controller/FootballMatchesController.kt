package jp.odds.controller

import jp.odds.dto.*
import jp.odds.service.FootballService
import jp.odds.service.response.model.SofascoreEvent
import jp.odds.service.response.model.StandingsResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate

@RestController
@RequestMapping("/api/football")
@CrossOrigin(origins = ["http://localhost:5173", "http://localhost:3000"])
class FootballMatchesController(private val footballService: FootballService) {

    @GetMapping("/matches/today")
    suspend fun getTodayMatches(): List<SofascoreEvent> = footballService.getTodayMatches()

    @GetMapping("/matches/date/{date}")
    suspend fun getMatchesByDate(
        @PathVariable date: LocalDate,
        @RequestParam(defaultValue = "false") includeAllLeagues: Boolean
    ): List<SofascoreEvent> = footballService.getMatchesByDate(date, includeAllLeagues = includeAllLeagues)

    @PostMapping("/matches/date/{date}/refresh")
    suspend fun refreshMatchesByDate(
        @PathVariable date: LocalDate,
        @RequestParam(defaultValue = "false") includeAllLeagues: Boolean
    ): List<SofascoreEvent> = footballService.getMatchesByDate(date, forceRefresh = true, includeAllLeagues = includeAllLeagues)

    @GetMapping("/team/{teamId}/events")
    suspend fun getTeamEvents(@PathVariable teamId: Long): List<SofascoreEvent> = footballService.getTeamEvents(teamId)

    @PostMapping("/matches/{eventId}/refresh")
    suspend fun refreshSingleMatch(@PathVariable eventId: Long, @RequestParam date: String): SofascoreEvent = try {
        footballService.refreshSingleMatch(eventId)
    } catch (e: IllegalArgumentException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message, e)
    } catch (e: Exception) {
        throw ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Failed to refresh match $eventId: ${e.message}",
            e
        )
    }

    @GetMapping("/tournament/{tournamentId}/season/{seasonId}/standings")
    suspend fun getTournamentStandings(@PathVariable tournamentId: Long, @PathVariable seasonId: Long): ResponseEntity<StandingsResponse> =
        footballService.getTournamentStandings(tournamentId, seasonId)?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()

    @GetMapping("/matches/{eventId}/history")
    suspend fun getMatchHistory(@PathVariable eventId: Long): MatchHistoryResponse = MatchHistoryResponse(
        oddsHistory = footballService.getOddsHistory(eventId).map { odds ->
            OddsHistoryPoint(
                timestamp = odds.recordedAt.epochSecond,
                home = odds.oddsHome,
                draw = odds.oddsDraw,
                away = odds.oddsAway
            )
        },
        votesHistory = footballService.getVotesHistory(eventId).map { votes ->
            VotesHistoryPoint(
                timestamp = votes.recordedAt.epochSecond,
                home = votes.votingHome,
                draw = votes.votingDraw,
                away = votes.votingAway,
                total = votes.votingTotal
            )
        }
    )

    @GetMapping("/statistics/winning-matches")
    suspend fun getWinningMatchStatistics(): WinningMatchStatistics = footballService.getWinningMatchStatistics()

    @GetMapping("/statistics/winning-matches-by-league")
    suspend fun getWinningMatchStatisticsByLeague(
        @RequestParam(required = false) country: String?
    ): WinningMatchStatisticsByLeague = footballService.getWinningMatchStatisticsByLeague(country)
}
