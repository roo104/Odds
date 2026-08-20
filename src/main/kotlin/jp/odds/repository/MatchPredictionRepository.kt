package jp.odds.repository

import jp.odds.entity.MatchPrediction
import jp.odds.entity.SportType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface MatchPredictionRepository : JpaRepository<MatchPrediction, Long> {

    fun findFirstByEventIdOrderByCreatedAtDesc(eventId: Long): MatchPrediction?

    /**
     * Every prediction for the matches of a day, oldest first - the caller keeps the last one per
     * event. A day holds a handful of predictions, so filtering to the latest in SQL would buy
     * nothing over reading them all.
     */
    fun findBySportAndStartTimestampBetweenOrderByCreatedAtAsc(
        sport: SportType,
        from: Long,
        to: Long
    ): List<MatchPrediction>
}
