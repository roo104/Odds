package jp.odds.repository

import jp.odds.entity.DailyMatchData
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface DailyMatchDataRepository : JpaRepository<DailyMatchData, Long> {
    fun existsByMatchDate(matchDate: LocalDate): Boolean
    fun findByMatchDate(matchDate: LocalDate): List<DailyMatchData>
    fun findByEventId(eventId: Long): DailyMatchData?
    fun findByEventIdIn(eventIds: Collection<Long>): List<DailyMatchData>
    fun findByStartTimestampBetween(startTimestamp: Long, endTimestamp: Long): List<DailyMatchData>
}
