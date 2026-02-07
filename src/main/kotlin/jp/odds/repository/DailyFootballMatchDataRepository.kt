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
        WHERE m.statusType = 'finished'
        AND m.homeScore IS NOT NULL
        AND m.awayScore IS NOT NULL
        ORDER BY m.id DESC
    """)
    fun findTop500FinishedMatches(pageable: org.springframework.data.domain.Pageable): List<DailyFootballMatchData>

    @Query("""
        SELECT m FROM DailyFootballMatchData m
        WHERE m.statusType = 'finished'
        AND m.homeScore IS NOT NULL
        AND m.awayScore IS NOT NULL
        AND m.countryName = :countryName
        ORDER BY m.id DESC
    """)
    fun findTop500FinishedMatchesByCountry(countryName: String, pageable: org.springframework.data.domain.Pageable): List<DailyFootballMatchData>
}
