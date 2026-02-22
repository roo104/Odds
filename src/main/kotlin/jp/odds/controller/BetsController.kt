package jp.odds.controller

import jp.odds.dto.BetsPageResponse
import jp.odds.dto.CreateBetRequest
import jp.odds.dto.MatchBetResponse
import jp.odds.dto.UpdateOddsRequest
import jp.odds.service.BetsService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/bets")
@CrossOrigin(origins = ["http://localhost:5173", "http://localhost:3000"])
class BetsController(private val betsService: BetsService) {

    @PostMapping
    suspend fun createBet(@RequestBody request: CreateBetRequest): MatchBetResponse = betsService.createBet(request)

    @GetMapping
    suspend fun listBets(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): BetsPageResponse = betsService.listBets(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startTimestamp")))

    @PatchMapping("/{betId}/odds")
    suspend fun updateOdds(@PathVariable betId: Long, @RequestBody request: UpdateOddsRequest): MatchBetResponse =
        betsService.updateOdds(betId, request.odds)

    @DeleteMapping("/{betId}")
    suspend fun deleteBet(@PathVariable betId: Long): ResponseEntity<Void> {
        return if (betsService.deleteBet(betId)) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @PostMapping("/{betId}/refresh")
    suspend fun refreshBetScore(@PathVariable betId: Long): MatchBetResponse = betsService.refreshBetScore(betId)
}
