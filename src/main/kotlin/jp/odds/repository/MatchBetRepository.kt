package jp.odds.repository

import jp.odds.entity.BetSelection
import jp.odds.entity.MatchBet
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface MatchBetRepository : JpaRepository<MatchBet, Long> {
    fun findByEventIdAndSelection(eventId: Long, selection: BetSelection): Optional<MatchBet>
}
