package jp.odds.repository

import jp.odds.entity.MatchVotesHistory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface MatchVotesHistoryRepository : JpaRepository<MatchVotesHistory, Long> {
    fun findByEventIdOrderByRecordedAtAsc(eventId: Long): List<MatchVotesHistory>
    fun findFirstByEventIdOrderByRecordedAtDesc(eventId: Long): MatchVotesHistory?
}
