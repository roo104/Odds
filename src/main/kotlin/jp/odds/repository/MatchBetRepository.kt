package jp.odds.repository

import jp.odds.entity.MatchBet
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface MatchBetRepository : JpaRepository<MatchBet, Long>
