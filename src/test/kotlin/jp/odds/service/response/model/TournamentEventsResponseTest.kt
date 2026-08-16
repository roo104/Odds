package jp.odds.service.response.model

import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule
import tools.jackson.module.kotlin.readValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Sofascore withdrew /sport/{sport}/scheduled-events/{date}, so fixtures now come from
 * /tournament/{id}/season/{seasonId}/events/{next|last}/{page}. That payload carries extra fields
 * we do not model and drops `eventFilters` - these tests pin both down.
 */
class TournamentEventsResponseTest {

    // Mirrors a real response, including fields we deliberately do not map (eventState, venue,
    // changes, roundInfo extras) and the nested category.country the country filters depend on.
    private val payload = """
    {
      "events": [
        {
          "eventState": {},
          "tournament": {
            "name": "Premier League",
            "slug": "premier-league",
            "category": {
              "name": "England",
              "slug": "england",
              "sport": {"name": "Football", "slug": "football", "id": 1},
              "priority": 10,
              "country": {"alpha2": "EN", "alpha3": "ENG", "name": "England", "slug": "england"},
              "id": 1,
              "flag": "england",
              "alpha2": "EN"
            },
            "uniqueTournament": {
              "name": "Premier League",
              "slug": "premier-league",
              "id": 17,
              "userCount": 1932847
            },
            "priority": 10,
            "id": 1
          },
          "season": {"name": "Premier League 26/27", "year": "26/27", "editor": false, "id": 96668},
          "roundInfo": {"round": 1},
          "customId": "abc",
          "status": {"code": 0, "description": "Not started", "type": "notstarted"},
          "venue": {"city": {"name": "London"}},
          "homeTeam": {
            "name": "Arsenal", "slug": "arsenal", "shortName": "Arsenal",
            "gender": "M", "sport": {"name": "Football", "slug": "football", "id": 1},
            "userCount": 2000000, "nameCode": "ARS", "national": false, "type": 0,
            "id": 42, "country": {"alpha2": "EN", "name": "England"},
            "teamColors": {"primary": "#cc0000", "secondary": "#ffffff", "text": "#ffffff"}
          },
          "awayTeam": {
            "name": "Coventry City", "slug": "coventry-city", "shortName": "Coventry",
            "gender": "M", "sport": {"name": "Football", "slug": "football", "id": 1},
            "userCount": 90000, "nameCode": "COV", "national": false, "type": 0,
            "id": 71, "country": {"alpha2": "EN", "name": "England"}
          },
          "homeScore": {},
          "awayScore": {},
          "time": {},
          "changes": {"changes": [], "changeTimestamp": 0},
          "hasGlobalHighlights": false,
          "crowdsourcingDataDisplayEnabled": false,
          "id": 14025123,
          "slug": "arsenal-coventry-city",
          "startTimestamp": 1787338800,
          "finalResultOnly": false,
          "feedLocked": true,
          "isEditor": false
        },
        {
          "tournament": {
            "name": "Premier League",
            "category": {"name": "England", "id": 1, "country": {"name": "England"}},
            "id": 1
          },
          "season": {"name": "Premier League 26/27", "year": "26/27", "id": 96668},
          "status": {"code": 100, "description": "Ended", "type": "finished"},
          "homeTeam": {"name": "Liverpool", "id": 44},
          "awayTeam": {"name": "Newcastle United", "id": 39},
          "homeScore": {"current": 2, "display": 2, "period1": 1, "period2": 1, "normaltime": 2},
          "awayScore": {"current": 1, "display": 1, "period1": 0, "period2": 1, "normaltime": 1},
          "winnerCode": 1,
          "id": 14025124,
          "startTimestamp": 1787943600
        }
      ]
    }
    """.trimIndent()

    // Spring Boot configures Jackson to tolerate unknown properties; mirror that here.
    private val mapper = JsonMapper.builder()
        .addModule(kotlinModule())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()

    @Test
    fun `parses the per-tournament events payload`() {
        val response: TournamentEventsResponse = mapper.readValue(payload)

        assertEquals(2, response.events.size)

        val upcoming = response.events[0]
        assertEquals(14025123L, upcoming.id)
        assertEquals(1787338800L, upcoming.startTimestamp)
        assertEquals("notstarted", upcoming.status.type)
        assertEquals("Arsenal", upcoming.homeTeam.name)
        assertEquals(42L, upcoming.homeTeam.id)
        assertEquals(1L, upcoming.tournament.id)
        assertEquals(96668L, upcoming.season?.id)
    }

    @Test
    fun `keeps the country the league filters rely on`() {
        val response: TournamentEventsResponse = mapper.readValue(payload)

        assertEquals("England", response.events[0].tournament.category.country?.name)
        assertEquals("England", response.events[1].tournament.category.country?.name)
    }

    @Test
    fun `maps scores on finished fixtures`() {
        val finished: TournamentEventsResponse = mapper.readValue(payload)
        val event = finished.events[1]

        assertEquals("finished", event.status.type)
        assertEquals(2, event.homeScore?.current)
        assertEquals(1, event.awayScore?.current)
    }

    @Test
    fun `eventFilters is absent so top-league status cannot come from the payload`() {
        val response: TournamentEventsResponse = mapper.readValue(payload)

        // The old scheduled-events feed set this; the replacement does not. Top-league status is
        // therefore seeded from the database instead of read off the event.
        assertTrue(response.events.all { it.eventFilters == null })
    }

    @Test
    fun `carries the stable league id that seeds later discovery`() {
        val response: TournamentEventsResponse = mapper.readValue(payload)

        // tournament.id is season-scoped; uniqueTournament.id is the stable league id we persist
        // so the next refresh can seed itself without re-deriving anything.
        assertEquals(1L, response.events[0].tournament.id)
        assertEquals(17, response.events[0].tournament.uniqueTournament?.id)
    }

    @Test
    fun `tolerates an empty page`() {
        val response: TournamentEventsResponse = mapper.readValue("""{"events": []}""")

        assertTrue(response.events.isEmpty())
        assertNull(response.hasNextPage)
    }

    @Test
    fun `parses the day's scheduled tournaments`() {
        // Trimmed from /sport/football/scheduled-tournaments/{date}/page/1.
        val scheduled: ScheduledTournamentsResponse = mapper.readValue(
            """
            {"scheduled":[{
              "tournament": {
                "name": "UEFA Champions League, Qualification",
                "slug": "uefa-champions-league-qualification",
                "category": {"name":"Europe","slug":"europe","priority":19,"id":1465,"flag":"europe"},
                "uniqueTournament": {"name":"UEFA Champions League","slug":"uefa-champions-league",
                                     "userCount":1123543,"id":7},
                "priority": 706,
                "id": 1339
              },
              "timezoneEventCount": {"23400": 5, "25200": 6, "28800": 7}
            }]}
            """.trimIndent()
        )

        val tournament = scheduled.scheduled.single().tournament
        // A qualification round is its own tournament beneath the league we actually track.
        assertEquals(1339L, tournament.id)
        assertEquals(7, tournament.uniqueTournament?.id)
    }
}
