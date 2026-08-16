package jp.odds.repository

import jp.odds.entity.DailyFootballMatchData
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface DailyFootballMatchDataRepository : JpaRepository<DailyFootballMatchData, Long> {
    fun findByEventId(eventId: Long): DailyFootballMatchData?
    fun findByEventIdIn(eventIds: Collection<Long>): List<DailyFootballMatchData>
    fun findByStartTimestampBetween(startTimestamp: Long, endTimestamp: Long): List<DailyFootballMatchData>

    @Query("""
        SELECT m FROM DailyFootballMatchData m
        WHERE (LOWER(m.statusType) = 'finished' OR LOWER(m.statusDescription) IN ('finished', 'ended', 'full time'))
        AND m.homeScore IS NOT NULL
        AND m.awayScore IS NOT NULL
        ORDER BY m.id DESC
    """)
    fun findFinishedMatches(pageable: org.springframework.data.domain.Pageable): List<DailyFootballMatchData>

    @Query("""
        SELECT m FROM DailyFootballMatchData m
        WHERE (LOWER(m.statusType) = 'finished' OR LOWER(m.statusDescription) IN ('finished', 'ended', 'full time'))
        AND m.homeScore IS NOT NULL
        AND m.awayScore IS NOT NULL
        AND m.countryName = :countryName
        ORDER BY m.id DESC
    """)
    fun findFinishedMatchesByCountry(countryName: String, pageable: org.springframework.data.domain.Pageable): List<DailyFootballMatchData>

    @Query("""
        SELECT m FROM DailyFootballMatchData m
        WHERE (LOWER(m.statusType) = 'finished' OR LOWER(m.statusDescription) IN ('finished', 'ended', 'full time'))
        AND m.homeScore IS NOT NULL
        AND m.awayScore IS NOT NULL
        AND m.isTopLeague = :isTopLeague
        ORDER BY m.id DESC
    """)
    fun findFinishedMatchesByTopLeague(isTopLeague: Boolean, pageable: org.springframework.data.domain.Pageable): List<DailyFootballMatchData>

    @Query("""
        SELECT m FROM DailyFootballMatchData m
        WHERE (LOWER(m.statusType) = 'finished' OR LOWER(m.statusDescription) IN ('finished', 'ended', 'full time'))
        AND m.homeScore IS NOT NULL
        AND m.awayScore IS NOT NULL
        AND m.countryName = :countryName
        AND m.isTopLeague = :isTopLeague
        ORDER BY m.id DESC
    """)
    fun findFinishedMatchesByCountryAndTopLeague(countryName: String, isTopLeague: Boolean, pageable: org.springframework.data.domain.Pageable): List<DailyFootballMatchData>

    @Query(value = """
        SELECT tournament_name, country_name, tournament_id, season_id
        FROM daily_football_match_data
        WHERE is_top_league = true
        AND country_name IS NOT NULL
        AND EXTRACT(YEAR FROM match_date) = EXTRACT(YEAR FROM CURRENT_DATE)
        GROUP BY tournament_name, country_name, tournament_id, season_id
        ORDER BY country_name
    """, nativeQuery = true)
    fun findTopLeaguesForCurrentYear(): List<Array<Any>>

    /**
     * Seeds match discovery: every league we have already recorded, with the newest season id we
     * hold as a fallback for when the live season lookup fails. [onlyTopLeagues] passed as false
     * widens discovery to every league in the table.
     */
    @Query(value = """
        SELECT tournament_id,
               tournament_name,
               MAX(season_id) AS season_id,
               MAX(CASE WHEN is_top_league THEN 1 ELSE 0 END) AS is_top_league,
               MAX(unique_tournament_id) AS unique_tournament_id
        FROM daily_football_match_data
        WHERE (:onlyTopLeagues = false OR is_top_league = true)
        AND match_date >= :since
        GROUP BY tournament_id, tournament_name
        ORDER BY MAX(match_date) DESC
    """, nativeQuery = true)
    fun findTrackedLeagues(onlyTopLeagues: Boolean, since: java.time.LocalDate): List<Array<Any?>>

    @Query(value = """
        SELECT DISTINCT country_name
        FROM daily_football_match_data
        WHERE is_top_league = true
        AND country_name IS NOT NULL
        AND EXTRACT(YEAR FROM match_date) = EXTRACT(YEAR FROM CURRENT_DATE)
        ORDER BY country_name
    """, nativeQuery = true)
    fun findAvailableCountriesForCurrentYear(): List<String>

    @Query(value = """
        SELECT tournament_name, country_name, tournament_id, season_id
        FROM daily_football_match_data
        WHERE is_top_league = true
        AND country_name = :countryName
        AND EXTRACT(YEAR FROM match_date) = EXTRACT(YEAR FROM CURRENT_DATE)
        GROUP BY tournament_name, country_name, tournament_id, season_id
        ORDER BY tournament_name
    """, nativeQuery = true)
    fun findTopLeaguesByCountry(countryName: String): List<Array<Any>>
}
