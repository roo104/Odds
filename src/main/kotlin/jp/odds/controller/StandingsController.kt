package jp.odds.controller

import jp.odds.service.FootballService
import jp.odds.service.response.model.StandingsResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/standings")
@CrossOrigin(origins = ["http://localhost:5173", "http://localhost:3000"])
class StandingsController(private val footballService: FootballService) {

    data class LeagueInfo(
        val tournamentName: String,
        val countryName: String,
        val tournamentId: Long,
        val seasonId: Long
    )

    @GetMapping("/countries")
    suspend fun getAvailableCountries(): ResponseEntity<List<String>> {
        val countries = footballService.getAvailableCountriesForCurrentYear()
        return ResponseEntity.ok(countries)
    }

    @GetMapping("/country/{country}")
    suspend fun getStandingsByCountry(
        @PathVariable country: String
    ): ResponseEntity<Map<String, StandingsResponse>> {
        val leagues = footballService.getTopLeaguesByCountry(country)

        val standings = leagues.mapNotNull { league ->
            val standing = footballService.getTournamentStandings(league.tournamentId, league.seasonId)
            if (standing != null) {
                "${league.countryName} - ${league.tournamentName}" to standing
            } else {
                null
            }
        }.toMap()

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
