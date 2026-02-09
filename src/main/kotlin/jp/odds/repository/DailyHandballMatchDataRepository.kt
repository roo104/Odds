package jp.odds.repository

import jp.odds.entity.DailyHandballMatchData
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface DailyHandballMatchDataRepository : JpaRepository<DailyHandballMatchData, Long> {
    fun findByEventId(eventId: Long): DailyHandballMatchData?
    fun findByEventIdIn(eventIds: Collection<Long>): List<DailyHandballMatchData>
    fun findByStartTimestampBetween(startTimestamp: Long, endTimestamp: Long): List<DailyHandballMatchData>

    @Query("""
        SELECT m FROM DailyHandballMatchData m
        WHERE (LOWER(m.statusType) = 'finished' OR LOWER(m.statusDescription) IN ('finished', 'ended', 'full time'))
        AND m.homeScore IS NOT NULL
        AND m.awayScore IS NOT NULL
        ORDER BY m.id DESC
    """)
    fun findFinishedMatches(pageable: Pageable): List<DailyHandballMatchData>

    @Query("""
        SELECT m FROM DailyHandballMatchData m
        WHERE (LOWER(m.statusType) = 'finished' OR LOWER(m.statusDescription) IN ('finished', 'ended', 'full time'))
        AND m.homeScore IS NOT NULL
        AND m.awayScore IS NOT NULL
        AND m.countryName = :countryName
        ORDER BY m.id DESC
    """)
    fun findFinishedMatchesByCountry(countryName: String, pageable: Pageable): List<DailyHandballMatchData>
}
