package jp.odds.service

import jp.odds.repository.MatchOddsHistoryRepository
import jp.odds.repository.MatchVotesHistoryRepository
import jp.odds.service.BaseSofascoreService.TrackedLeague
import kotlinx.coroutines.test.runTest
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.lang.reflect.Proxy
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Discovery asks Sofascore which tournaments play on a date, then pulls events only for the ones
 * we track. These tests pin that call order and the filtering, since the alternative - polling
 * every tracked league blindly - both misses sub-tournaments and wastes requests.
 */
class EventDiscoveryPathTest {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.now(zone)
    private val futureDate: LocalDate = today.plusDays(2)

    private fun kickoffOn(date: LocalDate, hour: Long) = date.atStartOfDay(zone).plusHours(hour).toEpochSecond()

    private fun scheduledPath(date: LocalDate, page: Int) =
        "/api/v1/sport/football/scheduled-tournaments/${date.format(DateTimeFormatter.ISO_LOCAL_DATE)}/page/$page"

    /** Two tournaments on the day: one under a tracked league, one we do not follow. */
    private val scheduledJson = """
        {"scheduled":[
          {"tournament":{
            "id": 1339, "name": "UEFA Champions League, Qualification",
            "category": {"id": 1465, "name": "Europe"},
            "uniqueTournament": {"id": 7, "name": "UEFA Champions League"}
          }},
          {"tournament":{
            "id": 55555, "name": "Some Untracked Cup",
            "category": {"id": 500, "name": "Nowhere"},
            "uniqueTournament": {"id": 88888, "name": "Untracked"}
          }}
        ]}
    """.trimIndent()

    private fun eventsJson(eventId: Long, startTimestamp: Long, tournamentId: Long) = """
        {"events":[{
          "id": $eventId,
          "startTimestamp": $startTimestamp,
          "status": {"code": 0, "description": "Not started", "type": "notstarted"},
          "homeTeam": {"id": 1, "name": "Home"},
          "awayTeam": {"id": 2, "name": "Away"},
          "tournament": {
            "id": $tournamentId, "name": "UEFA Champions League, Qualification",
            "category": {"id": 1465, "name": "Europe", "country": {"name": "Europe"}},
            "uniqueTournament": {"id": 7, "name": "UEFA Champions League"}
          },
          "season": {"id": 96518, "name": "26/27"}
        }]}
    """.trimIndent()

    private fun liveJson(startTimestamp: Long, uniqueTournamentId: Int) = """
        {"events":[{
          "id": 777001,
          "startTimestamp": $startTimestamp,
          "status": {"code": 6, "description": "1st half", "type": "inprogress"},
          "homeTeam": {"id": 3, "name": "Live Home"},
          "awayTeam": {"id": 4, "name": "Live Away"},
          "tournament": {
            "id": 1339, "name": "UEFA Champions League, Qualification",
            "category": {"id": 1465, "name": "Europe"},
            "uniqueTournament": {"id": $uniqueTournamentId, "name": "UEFA Champions League"}
          },
          "season": {"id": 96518, "name": "26/27"}
        }]}
    """.trimIndent()

    /** Repositories are untouched by discovery; a no-op proxy avoids a mocking dependency. */
    private inline fun <reified T : Any> stubRepository(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, _, _ -> null } as T

    private class TestService(
        builder: WebClient.Builder,
        odds: MatchOddsHistoryRepository,
        votes: MatchVotesHistoryRepository
    ) : BaseSofascoreService(builder, odds, votes) {
        override val sportSlug: String = "football"

        suspend fun discover(date: LocalDate, leagues: List<TrackedLeague>, includeAll: Boolean = false) =
            fetchEventsForDate(date, leagues, includeAll)
    }

    private fun serviceWithRouting(paths: MutableList<String>, body: (String) -> String): TestService {
        val builder = WebClient.builder().exchangeFunction { request ->
            val path = request.url().path
            paths += path
            Mono.just(
                ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(body(path))
                    .build()
            )
        }
        return TestService(builder, stubRepository(), stubRepository())
    }

    /** Routes the day's tournament list, one page of events, and an empty live feed. */
    private fun standardService(paths: MutableList<String>, date: LocalDate, eventTimestamp: Long): TestService =
        serviceWithRouting(paths) { path ->
            when {
                path == scheduledPath(date, 1) -> scheduledJson
                path.contains("/scheduled-tournaments/") -> """{"scheduled":[]}"""
                path.endsWith("/events/live") -> """{"events":[]}"""
                path == "/api/v1/tournament/1339/events/next/0" ||
                        path == "/api/v1/tournament/1339/events/last/0" ->
                    eventsJson(555001, eventTimestamp, 1339)
                else -> """{"events":[]}"""
            }
        }

    @Test
    fun `the day's tournament list is the first call`() = runTest {
        val paths = mutableListOf<String>()
        val service = standardService(paths, futureDate, kickoffOn(futureDate, 19))

        service.discover(futureDate, listOf(TrackedLeague(7, "UEFA Champions League", isUniqueTournament = true)))

        assertEquals(scheduledPath(futureDate, 1), paths.first(), "discovery must start from the date's tournaments")
    }

    @Test
    fun `events are pulled for the scheduled tournament, without a season id`() = runTest {
        val paths = mutableListOf<String>()
        val service = standardService(paths, futureDate, kickoffOn(futureDate, 19))

        val events = service.discover(
            futureDate,
            listOf(TrackedLeague(7, "UEFA Champions League", isUniqueTournament = true))
        )

        assertEquals(1, events.size)
        assertEquals(555001L, events.first().id)
        assertTrue(
            paths.any { it == "/api/v1/tournament/1339/events/next/0" },
            "events come from the scheduled tournament's own feed, got: $paths"
        )
        assertTrue(
            paths.none { it.contains("/season/") },
            "the feed defaults to the current season; no season lookup should happen, got: $paths"
        )
    }

    /** A qualification round is its own tournament, so matching has to fall back to its parent. */
    @Test
    fun `a sub-tournament is matched through its parent unique tournament`() = runTest {
        val paths = mutableListOf<String>()
        val service = standardService(paths, futureDate, kickoffOn(futureDate, 19))

        // We track unique tournament 7; the scheduled entry is tournament 1339 beneath it.
        val events = service.discover(
            futureDate,
            listOf(TrackedLeague(7, "UEFA Champions League", isUniqueTournament = true))
        )

        assertEquals(1, events.size, "a qualification round under a tracked league must still be collected")
        assertEquals(true, events.first().isTopLeague)
    }

    @Test
    fun `tournaments we do not track are never fetched`() = runTest {
        val paths = mutableListOf<String>()
        val service = standardService(paths, futureDate, kickoffOn(futureDate, 19))

        service.discover(futureDate, listOf(TrackedLeague(7, "UEFA Champions League", isUniqueTournament = true)))

        assertTrue(
            paths.none { it.contains("/tournament/55555/") },
            "an untracked tournament must not cost a request, got: $paths"
        )
    }

    @Test
    fun `including all leagues also fetches untracked tournaments but does not mark them top`() = runTest {
        val paths = mutableListOf<String>()
        val service = serviceWithRouting(paths) { path ->
            when {
                path == scheduledPath(futureDate, 1) -> scheduledJson
                path.contains("/scheduled-tournaments/") -> """{"scheduled":[]}"""
                path == "/api/v1/tournament/55555/events/next/0" ->
                    eventsJson(555002, kickoffOn(futureDate, 19), 55555)
                else -> """{"events":[]}"""
            }
        }

        val events = service.discover(futureDate, leagues = emptyList(), includeAll = true)

        assertTrue(paths.any { it == "/api/v1/tournament/55555/events/next/0" }, "got: $paths")
        assertEquals(1, events.size)
        assertEquals(false, events.first().isTopLeague, "an untracked league must not count as a top league")
    }

    @Test
    fun `events outside the requested day are discarded`() = runTest {
        val paths = mutableListOf<String>()
        // Kick-off a week past the requested date.
        val service = standardService(paths, futureDate, kickoffOn(futureDate, 19) + 7 * 24 * 3600)

        val events = service.discover(
            futureDate,
            listOf(TrackedLeague(7, "UEFA Champions League", isUniqueTournament = true))
        )

        assertTrue(events.isEmpty(), "only fixtures kicking off on $futureDate should be returned")
    }

    /**
     * A match in play is in neither paginated feed - `next` drops it at kick-off, `last` only adds
     * it once finished - so today's refresh has to consult the live feed as well.
     */
    @Test
    fun `matches in play today are picked up from the live feed`() = runTest {
        val paths = mutableListOf<String>()
        val service = serviceWithRouting(paths) { path ->
            when {
                path == scheduledPath(today, 1) -> scheduledJson
                path.contains("/scheduled-tournaments/") -> """{"scheduled":[]}"""
                path.endsWith("/events/live") -> liveJson(kickoffOn(today, 20), uniqueTournamentId = 7)
                // Both paginated feeds miss the in-play fixture, exactly as the real API does.
                else -> """{"events":[]}"""
            }
        }

        val events = service.discover(today, listOf(TrackedLeague(7, "UEFA Champions League", isUniqueTournament = true)))

        assertTrue(paths.any { it == "/api/v1/sport/football/events/live" }, "live feed must be consulted: $paths")
        assertEquals(1, events.size, "the in-play fixture should be recovered from the live feed")
        assertEquals(777001L, events.first().id)
    }

    @Test
    fun `live fixtures from untracked leagues are ignored`() = runTest {
        val paths = mutableListOf<String>()
        val service = serviceWithRouting(paths) { path ->
            when {
                path == scheduledPath(today, 1) -> scheduledJson
                path.contains("/scheduled-tournaments/") -> """{"scheduled":[]}"""
                // Live globally, but not a league we track.
                path.endsWith("/events/live") -> liveJson(kickoffOn(today, 20), uniqueTournamentId = 99999)
                else -> """{"events":[]}"""
            }
        }

        val events = service.discover(today, listOf(TrackedLeague(7, "UEFA Champions League", isUniqueTournament = true)))

        assertTrue(events.isEmpty(), "the live feed spans every competition; untracked ones must be dropped")
    }

    @Test
    fun `a future date consults neither the live nor the finished feed`() = runTest {
        val paths = mutableListOf<String>()
        val service = standardService(paths, futureDate, kickoffOn(futureDate, 19))

        service.discover(futureDate, listOf(TrackedLeague(7, "UEFA Champions League", isUniqueTournament = true)))

        assertTrue(paths.none { it.contains("/events/live") }, "nothing is in play on a future date: $paths")
        assertTrue(paths.none { it.contains("/events/last/") }, "future dates need only the upcoming feed: $paths")
    }

    @Test
    fun `a past date consults only the finished feed`() = runTest {
        val paths = mutableListOf<String>()
        val pastDate = today.minusDays(3)
        val service = standardService(paths, pastDate, kickoffOn(pastDate, 19))

        val events = service.discover(
            pastDate,
            listOf(TrackedLeague(7, "UEFA Champions League", isUniqueTournament = true))
        )

        assertEquals(1, events.size)
        assertTrue(paths.none { it.contains("/events/next/") }, "a past date has no upcoming fixtures: $paths")
        assertTrue(paths.none { it.contains("/events/live") }, "a past date has nothing in play: $paths")
    }
}
