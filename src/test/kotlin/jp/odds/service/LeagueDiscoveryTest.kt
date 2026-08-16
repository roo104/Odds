package jp.odds.service

import jp.odds.service.BaseSofascoreService.TrackedLeague
import jp.odds.service.response.model.DefaultUniqueTournamentsResponse
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule
import tools.jackson.module.kotlin.readValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * League discovery seeds from stored matches and widens with Sofascore's top-competitions list,
 * so no league ids are pinned in configuration.
 */
class LeagueDiscoveryTest {

    private val mapper = JsonMapper.builder()
        .addModule(kotlinModule())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()

    private fun stored(id: Long, unique: Boolean, name: String = "L$id") =
        TrackedLeague(id, name, isUniqueTournament = unique)

    @Test
    fun `top competitions payload yields league ids without any hardcoded list`() {
        // Trimmed from /config/default-unique-tournaments/DK/football.
        val response: DefaultUniqueTournamentsResponse = mapper.readValue(
            """
            {"uniqueTournaments":[
              {"name":"Danish Superliga","slug":"danish-superliga","id":39,
               "category":{"id":9,"name":"Denmark","slug":"denmark","country":{"name":"Denmark"}},
               "primaryColorHex":"#ffffff","userCount":120000},
              {"name":"Premier League","slug":"premier-league","id":17,
               "category":{"id":1,"name":"England","slug":"england","country":{"name":"England"}},
               "userCount":1932847},
              {"name":"UEFA Champions League","slug":"uefa-champions-league","id":7,
               "category":{"id":1465,"name":"Europe","slug":"europe"}}
            ]}
            """.trimIndent()
        )

        assertEquals(listOf(39, 17, 7), response.uniqueTournaments.map { it.id })
        assertEquals("Premier League", response.uniqueTournaments[1].name)
    }

    @Test
    fun `defaults fill in when nothing has been stored yet`() {
        val merged = BaseSofascoreService.mergeLeagues(
            stored = emptyList(),
            defaults = listOf(stored(17, unique = true), stored(8, unique = true))
        )

        assertEquals(listOf(17L, 8L), merged.map { it.tournamentId })
        assertTrue(merged.all { it.isUniqueTournament })
    }

    @Test
    fun `a league already stored is not polled twice`() {
        val merged = BaseSofascoreService.mergeLeagues(
            stored = listOf(stored(17, unique = true, name = "Premier League")),
            defaults = listOf(stored(17, unique = true), stored(8, unique = true))
        )

        assertEquals(2, merged.size)
        assertEquals(1, merged.count { it.tournamentId == 17L })
        assertEquals("Premier League", merged.first { it.tournamentId == 17L }.tournamentName)
    }

    @Test
    fun `defaults widen the stored set with newly promoted competitions`() {
        val merged = BaseSofascoreService.mergeLeagues(
            stored = listOf(stored(17, unique = true)),
            defaults = listOf(stored(17, unique = true), stored(679, unique = true))
        )

        assertTrue(merged.any { it.tournamentId == 679L }, "a top competition we do not yet track should be added")
    }

    @Test
    fun `season-scoped ids never dedupe against unique-tournament ids`() {
        // tournament.id 1 and unique-tournament id 1 are different leagues in different id spaces;
        // collapsing them would silently drop one.
        val merged = BaseSofascoreService.mergeLeagues(
            stored = listOf(stored(1, unique = false, name = "season-scoped 1")),
            defaults = listOf(stored(1, unique = true, name = "EURO"))
        )

        assertEquals(2, merged.size)
        assertEquals(setOf(false, true), merged.map { it.isUniqueTournament }.toSet())
    }

    @Test
    fun `top competitions are treated as top leagues`() {
        val merged = BaseSofascoreService.mergeLeagues(stored = emptyList(), defaults = listOf(stored(17, true)))

        assertTrue(merged.single().isTopLeague)
    }

    @Test
    fun `pinned extras are parsed as top unique tournaments`() {
        val extras = BaseSofascoreService.parseExtraTopLeagues("238:Liga Portugal Betclic, 37:Eredivisie")

        assertEquals(listOf(238L, 37L), extras.map { it.tournamentId })
        assertEquals("Liga Portugal Betclic", extras.first().tournamentName)
        assertTrue(extras.all { it.isTopLeague && it.isUniqueTournament })
    }

    @Test
    fun `a bare id needs no name and blank config yields nothing`() {
        assertEquals(listOf(238L), BaseSofascoreService.parseExtraTopLeagues("238").map { it.tournamentId })
        assertTrue(BaseSofascoreService.parseExtraTopLeagues("").isEmpty())
        assertTrue(BaseSofascoreService.parseExtraTopLeagues(" , ").isEmpty())
    }

    @Test
    fun `unparsable entries are dropped rather than failing the refresh`() {
        val extras = BaseSofascoreService.parseExtraTopLeagues("238:Liga Portugal,oops,,37:Eredivisie,238:dupe")

        assertEquals(listOf(238L, 37L), extras.map { it.tournamentId })
    }

    @Test
    fun `a pinned league stored as non-top is promoted`() {
        // Liga Portugal reaches the table only via an all-leagues refresh, which stores it non-top.
        val storedNonTop = TrackedLeague(238, "Liga Portugal Betclic", isTopLeague = false, isUniqueTournament = true)

        val leagues = BaseSofascoreService.applyExtraTopLeagues(
            stored = listOf(storedNonTop),
            extras = BaseSofascoreService.parseExtraTopLeagues("238:Liga Portugal Betclic")
        )

        assertEquals(1, leagues.size, "the pin must not add a second copy of a league we already store")
        assertTrue(leagues.single().isTopLeague)
    }

    @Test
    fun `a pinned league we have never stored is added`() {
        val leagues = BaseSofascoreService.applyExtraTopLeagues(
            stored = listOf(stored(17, unique = true, name = "Premier League")),
            extras = BaseSofascoreService.parseExtraTopLeagues("238:Liga Portugal Betclic")
        )

        assertEquals(setOf(17L, 238L), leagues.map { it.tournamentId }.toSet())
        assertTrue(leagues.first { it.tournamentId == 238L }.isTopLeague)
    }

    @Test
    fun `a pin never promotes a season-scoped id that happens to collide`() {
        // Season-scoped 238 is a different competition from unique-tournament 238.
        val seasonScoped = TrackedLeague(238, "some other competition", isTopLeague = false, isUniqueTournament = false)

        val leagues = BaseSofascoreService.applyExtraTopLeagues(
            stored = listOf(seasonScoped),
            extras = BaseSofascoreService.parseExtraTopLeagues("238:Liga Portugal Betclic")
        )

        assertEquals(2, leagues.size)
        assertTrue(leagues.first { !it.isUniqueTournament }.isTopLeague.not())
    }
}
