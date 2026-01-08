package jp.odds.controller

import jp.odds.dto.SofascoreEvent
import jp.odds.service.SofascoreService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/football")
class FootballMatchesController(
    private val sofascoreService: SofascoreService
) {
    
    @GetMapping("/matches/today")
    suspend fun getTodayMatches(): List<SofascoreEvent> {
        return sofascoreService.getTodayFootballMatches()
    }
}
