package jp.odds.repository

import jp.odds.entity.DailyMatchData
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Repository
interface DailyMatchDataRepository : JpaRepository<DailyMatchData, Long> {
    fun findByMatchDate(matchDate: LocalDate): List<DailyMatchData>
    fun existsByMatchDate(matchDate: LocalDate): Boolean

    @Transactional
    fun deleteByMatchDate(matchDate: LocalDate): Int
}
