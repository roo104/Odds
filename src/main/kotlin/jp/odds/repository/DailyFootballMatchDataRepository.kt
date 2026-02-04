package jp.odds.repository

import jp.odds.entity.DailyFootballMatchData
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface DailyFootballMatchDataRepository : JpaRepository<DailyFootballMatchData, Long> {
    fun findByEventId(eventId: Long): DailyFootballMatchData?
    fun findByEventIdIn(eventIds: Collection<Long>): List<DailyFootballMatchData>
    fun findByStartTimestampBetween(startTimestamp: Long, endTimestamp: Long): List<DailyFootballMatchData>
}
