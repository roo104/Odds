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
}
