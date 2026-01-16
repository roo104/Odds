package jp.odds.controller

import jp.odds.dto.SofascoreEvent
import jp.odds.service.SofascoreService
import org.springframework.web.bind.annotation.*
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

    @GetMapping("/team/{teamId}/events")
    suspend fun getTeamEvents(@PathVariable teamId: Long): List<SofascoreEvent> {
        return sofascoreService.getTeamEvents(teamId)
    }
}
