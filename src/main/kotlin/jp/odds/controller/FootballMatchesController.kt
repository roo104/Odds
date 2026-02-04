package jp.odds.controller

import jp.odds.dto.*
import jp.odds.service.SofascoreService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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
    suspend fun getMatchesByDate(
        @PathVariable date: String,
        @RequestParam(defaultValue = "false") includeAllLeagues: Boolean
    ): List<SofascoreEvent> = sofascoreService.getFootballMatchesByDate(LocalDate.parse(date), includeAllLeagues = includeAllLeagues)

    @PostMapping("/matches/date/{date}/refresh")
    suspend fun refreshMatchesByDate(
        @PathVariable date: String,
        @RequestParam(defaultValue = "false") includeAllLeagues: Boolean
    ): List<SofascoreEvent> {
        val localDate = LocalDate.parse(date)
        return sofascoreService.getFootballMatchesByDate(localDate, forceRefresh = true, includeAllLeagues = includeAllLeagues)
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
    suspend fun getTournamentSeasons(@PathVariable tournamentId: Long): ResponseEntity<TournamentSeasonsResponse> {
        val result = sofascoreService.getTournamentSeasons(tournamentId)
        return if (result != null) {
            ResponseEntity.ok(result)
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/tournament/{tournamentId}/season/{seasonId}/standings")
    suspend fun getTournamentStandings(
        @PathVariable tournamentId: Long,
        @PathVariable seasonId: Long
    ): ResponseEntity<StandingsResponse> {
        val result = sofascoreService.getTournamentStandings(tournamentId, seasonId)
        return if (result != null) {
            ResponseEntity.ok(result)
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/matches/{eventId}/history")
    suspend fun getMatchHistory(@PathVariable eventId: Long): MatchHistoryResponse {
        val oddsHistory = sofascoreService.getOddsHistory(eventId)
        val votesHistory = sofascoreService.getVotesHistory(eventId)

        return MatchHistoryResponse(
            oddsHistory = oddsHistory.map { odds ->
                OddsHistoryPoint(
                    timestamp = odds.recordedAt.epochSecond,
                    home = odds.oddsHome,
                    draw = odds.oddsDraw,
                    away = odds.oddsAway
                )
            },
            votesHistory = votesHistory.map { votes ->
                VotesHistoryPoint(
                    timestamp = votes.recordedAt.epochSecond,
                    home = votes.votingHome,
                    draw = votes.votingDraw,
                    away = votes.votingAway,
                    total = votes.votingTotal
                )
            }
        )
    }
}
