package jp.odds.controller

import jp.odds.dto.MatchHistoryResponse
import jp.odds.dto.OddsHistoryPoint
import jp.odds.dto.VotesHistoryPoint
import jp.odds.service.SofascoreService
import jp.odds.service.response.model.SofascoreEvent
import jp.odds.service.response.model.StandingsResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate

@RestController
@RequestMapping("/api/handball")
@CrossOrigin(origins = ["http://localhost:5173", "http://localhost:3000"])
class HandballMatchesController(private val sofascoreService: SofascoreService) {

    @GetMapping("/matches/today")
    suspend fun getTodayMatches(): List<SofascoreEvent> = sofascoreService.getTodayHandballMatches()

    @GetMapping("/matches/date/{date}")
    suspend fun getMatchesByDate(
        @PathVariable date: LocalDate
    ): List<SofascoreEvent> = sofascoreService.getHandballMatchesByDate(date)

    @PostMapping("/matches/date/{date}/refresh")
    suspend fun refreshMatchesByDate(
        @PathVariable date: LocalDate
    ): List<SofascoreEvent> = sofascoreService.getHandballMatchesByDate(date, forceRefresh = true)

    @GetMapping("/team/{teamId}/events")
    suspend fun getTeamEvents(@PathVariable teamId: Long): List<SofascoreEvent> = sofascoreService.getTeamEvents(teamId)

    @PostMapping("/matches/{eventId}/refresh")
    suspend fun refreshSingleMatch(@PathVariable eventId: Long, @RequestParam date: String): SofascoreEvent = try {
        sofascoreService.refreshHandballSingleMatch(eventId)
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
        sofascoreService.getTournamentStandings(tournamentId, seasonId)?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()

    @GetMapping("/matches/{eventId}/history")
    suspend fun getMatchHistory(@PathVariable eventId: Long): MatchHistoryResponse = MatchHistoryResponse(
        oddsHistory = sofascoreService.getOddsHistory(eventId).map { odds ->
            OddsHistoryPoint(
                timestamp = odds.recordedAt.epochSecond,
                home = odds.oddsHome,
                draw = odds.oddsDraw,
                away = odds.oddsAway
            )
        },
        votesHistory = sofascoreService.getVotesHistory(eventId).map { votes ->
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
