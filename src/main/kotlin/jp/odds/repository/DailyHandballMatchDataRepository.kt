package jp.odds.repository

import jp.odds.entity.DailyHandballMatchData
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface DailyHandballMatchDataRepository : JpaRepository<DailyHandballMatchData, Long> {
    fun findByEventId(eventId: Long): DailyHandballMatchData?
    fun findByEventIdIn(eventIds: Collection<Long>): List<DailyHandballMatchData>
    fun findByStartTimestampBetween(startTimestamp: Long, endTimestamp: Long): List<DailyHandballMatchData>

    @Query("SELECT m FROM DailyHandballMatchData m WHERE m.statusType = 'finished' AND m.homeScore IS NOT NULL AND m.awayScore IS NOT NULL")
    fun findAllFinishedMatches(): List<DailyHandballMatchData>
}
