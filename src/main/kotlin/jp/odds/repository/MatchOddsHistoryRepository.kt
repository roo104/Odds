package jp.odds.repository

import jp.odds.entity.MatchOddsHistory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface MatchOddsHistoryRepository : JpaRepository<MatchOddsHistory, Long> {
    fun findByEventIdOrderByRecordedAtAsc(eventId: Long): List<MatchOddsHistory>
}
