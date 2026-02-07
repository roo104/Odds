package jp.odds.controller

import jp.odds.service.FootballService
import jp.odds.service.response.model.StandingsResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/standings")
@CrossOrigin(origins = ["http://localhost:5173", "http://localhost:3000"])
class StandingsController(private val footballService: FootballService) {

    data class LeagueStandingsRequest(
        val tournamentId: Long,
        val seasonId: Long,
        val leagueName: String
    )

    @GetMapping("/major-leagues")
    suspend fun getMajorLeaguesStandings(): ResponseEntity<Map<String, StandingsResponse?>> {
        // Major European Leagues with their tournament and season IDs
        val majorLeagues = listOf(
            LeagueStandingsRequest(17, 63217, "Premier League"),        // England
            LeagueStandingsRequest(8, 64205, "La Liga"),                 // Spain
            LeagueStandingsRequest(35, 64253, "Bundesliga"),             // Germany
            LeagueStandingsRequest(23, 64159, "Serie A"),                // Italy
            LeagueStandingsRequest(34, 64169, "Ligue 1")                 // France
        )

        val standings = majorLeagues.associate { league ->
            league.leagueName to footballService.getTournamentStandings(league.tournamentId, league.seasonId)
        }

        return ResponseEntity.ok(standings)
    }

    @GetMapping("/tournament/{tournamentId}/season/{seasonId}")
    suspend fun getTournamentStandings(
        @PathVariable tournamentId: Long,
        @PathVariable seasonId: Long
    ): ResponseEntity<StandingsResponse> =
        footballService.getTournamentStandings(tournamentId, seasonId)?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()
}
