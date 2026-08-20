package jp.odds.controller

import jp.odds.dto.ClaudeAskRequest
import jp.odds.dto.MatchPredictionRequest
import jp.odds.dto.SetClaudeProviderRequest
import jp.odds.service.claude.ClaudeService
import jp.odds.service.claude.MatchPredictionService
import org.slf4j.LoggerFactory
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/claude")
@CrossOrigin(origins = ["http://localhost:5173", "http://localhost:3000"])
class ClaudeController(
    private val claudeService: ClaudeService,
    private val matchPredictionService: MatchPredictionService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/status")
    fun status() = claudeService.status()

    @PutMapping("/provider")
    fun setProvider(@RequestBody request: SetClaudeProviderRequest): ResponseEntity<Any> = try {
        ResponseEntity.ok(claudeService.setProvider(request.provider))
    } catch (e: IllegalArgumentException) {
        ResponseEntity.badRequest().body(mapOf("error" to e.message))
    }

    /** Failures come back as a readable message rather than a stack trace, since the UI shows them. */
    @PostMapping("/ask")
    suspend fun ask(@RequestBody request: ClaudeAskRequest): ResponseEntity<Any> =
        handled("Claude request failed") { claudeService.ask(request) }

    /** Prediction for one match, built from its odds, public vote and live statistics. */
    @PostMapping("/predict")
    suspend fun predict(@RequestBody request: MatchPredictionRequest): ResponseEntity<Any> =
        handled("Match prediction failed") { matchPredictionService.predict(request) }

    /**
     * The latest stored prediction per match kicking off on a date, so the matches table can show
     * on hover what Claude already said without asking it again.
     */
    @GetMapping("/predictions")
    suspend fun predictions(
        @RequestParam sport: String,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate
    ): ResponseEntity<Any> =
        handled("Stored prediction lookup failed") { matchPredictionService.storedPredictions(sport, date) }

    private suspend fun handled(logMessage: String, block: suspend () -> Any): ResponseEntity<Any> = try {
        ResponseEntity.ok(block())
    } catch (e: IllegalArgumentException) {
        ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Invalid request")))
    } catch (e: Exception) {
        log.error(logMessage, e)
        ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(mapOf("error" to (e.message ?: e.javaClass.simpleName)))
    }
}
