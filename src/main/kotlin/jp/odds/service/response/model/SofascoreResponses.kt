package jp.odds.service.response.model

data class ScheduledEventsResponse(
    val events: List<ScheduledEvent>
)

/**
 * Response of /tournament/{id}/season/{seasonId}/events/{next|last}/{page}.
 *
 * Sofascore withdrew the bulk /sport/{sport}/scheduled-events/{date} endpoint (it now 404s for
 * every sport and date), so match discovery walks the tracked tournaments instead. Events carry
 * the same shape as the old scheduled-events payload minus `eventFilters`.
 */
data class TournamentEventsResponse(
    val events: List<ScheduledEvent> = emptyList(),
    val hasNextPage: Boolean? = null
)

/**
 * Response of /sport/{sport}/scheduled-tournaments/{date}/page/{n} - the tournaments with fixtures
 * on a date. Sofascore's replacement for the withdrawn scheduled-events feed; it lists tournaments
 * rather than events, so each one's fixtures are fetched separately.
 */
data class ScheduledTournamentsResponse(
    val scheduled: List<ScheduledTournamentEntry> = emptyList()
)

data class ScheduledTournamentEntry(
    val tournament: ScheduledTournament
)

data class ScheduledEvent(
    val id: Long,
    val startTimestamp: Long,
    val status: Status,
    val winnerCode: Int? = null,
    val homeTeam: ScheduledTeam,
    val awayTeam: ScheduledTeam,
    val homeScore: DetailedScore? = null,
    val awayScore: DetailedScore? = null,
    val tournament: ScheduledTournament,
    val season: Season? = null,
    val roundInfo: RoundInfo? = null,
    val time: TimeInfo? = null,
    val eventFilters: EventFilter? = null
)

data class SofascoreEvent(
    val id: Long,
    val startTimestamp: Long,
    val homeTeam: Team,
    val awayTeam: Team,
    val homeScore: Score? = null,
    val awayScore: Score? = null,
    val status: Status,
    val tournament: Tournament,
    val season: Season? = null,
    val vote: VoteData? = null,
    val eventFilters: EventFilter? = null,
    var odds: Odds? = null,
    var voting: Voting? = null,
    var homeFormScore: Int? = null,
    var awayFormScore: Int? = null,
    var lastUpdated: Long? = null,
    var isTopLeague: Boolean? = null
)

data class Team(
    val id: Long,
    val name: String,
    val country: Country? = null
)

data class Country(
    val name: String?
)

data class Score(
    val current: Int? = null,
    val display: Int? = null
)

data class Status(
    val type: String,
    val description: String
)

data class Tournament(
    val id: Long,
    val name: String,
    val category: Category,
    /** Stable league id, unlike [id] which is scoped to a season. Keys the per-tournament feeds. */
    val uniqueTournamentId: Long? = null
)

/** Response of /config/default-unique-tournaments/{alpha2}/{sport} - Sofascore's top competitions. */
data class DefaultUniqueTournamentsResponse(
    val uniqueTournaments: List<UniqueTournament> = emptyList()
)

data class Category(
    val name: String,
    val country: Country? = null
)

data class OddsResponse(
    val markets: List<Market>? = null
)

data class Market(
    val marketName: String,
    val choices: List<Choice>? = null
)

data class Choice(
    val name: String,
    val fractionalValue: String? = null,
    val sourceId: Int? = null
)

data class Odds(
    val home: String? = null,
    val draw: String? = null,
    val away: String? = null
)

data class VotingResponse(
    val vote: VoteData? = null
)

data class VoteData(
    val vote1: Int? = null,
    val vote2: Int? = null,
    val voteX: Int? = null
)

data class Voting(
    val home: Int? = null,
    val draw: Int? = null,
    val away: Int? = null,
    val total: Int? = null
)

data class EventDetailsResponse(
    val event: SofascoreEvent? = null
)

data class TeamEventsResponse(
    val events: List<SofascoreEvent>? = null,
    val hasNextPage: Boolean = false
)

data class StandingsResponse(
    val standings: List<StandingGroup>? = null
)

data class StandingGroup(
    val rows: List<StandingRow>? = null,
    val name: String? = null
)

data class StandingRow(
    val team: Team,
    val position: Int? = null,
    val matches: Int? = null,
    val wins: Int? = null,
    val draws: Int? = null,
    val losses: Int? = null,
    val scoresFor: Int? = null,
    val scoresAgainst: Int? = null,
    val points: Int? = null
)

data class Season(
    val id: Long,
    val name: String,
    val year: String? = null
)

data class EventFilter(
    val category: List<String>? = null,
    val level: List<String>? = null,
    val gender: List<String>? = null
)

data class ScheduledTeam(
    val id: Long,
    val name: String,
    val slug: String? = null,
    val shortName: String? = null,
    val gender: String? = null,
    val sport: Sport? = null,
    val userCount: Int? = null,
    val nameCode: String? = null,
    val disabled: Boolean? = null,
    val national: Boolean? = null,
    val type: Int? = null,
    val country: CountryInfo? = null,
    val subTeams: List<Team>? = null,
    val teamColors: TeamColors? = null,
    val fieldTranslations: FieldTranslations? = null
)

data class Sport(
    val id: Int,
    val name: String,
    val slug: String? = null
)

data class CountryInfo(
    val alpha2: String? = null,
    val alpha3: String? = null,
    val name: String? = null,
    val slug: String? = null
)

data class TeamColors(
    val primary: String? = null,
    val secondary: String? = null,
    val text: String? = null
)

data class FieldTranslations(
    val nameTranslation: Map<String, String>? = null,
    val shortNameTranslation: Map<String, String>? = null
)

data class DetailedScore(
    val current: Int? = null,
    val display: Int? = null,
    val period1: Int? = null,
    val period2: Int? = null,
    val normaltime: Int? = null
)

data class ScheduledTournament(
    val id: Long,
    val name: String,
    val slug: String? = null,
    val category: ScheduledCategory,
    val uniqueTournament: UniqueTournament? = null,
    val priority: Int? = null,
    val fieldTranslations: FieldTranslations? = null
)

data class ScheduledCategory(
    val id: Int,
    val name: String,
    val slug: String? = null,
    val sport: Sport? = null,
    val country: CountryInfo? = null,
    val flag: String? = null,
    val alpha2: String? = null,
    val fieldTranslations: FieldTranslations? = null
)

data class UniqueTournament(
    val id: Int,
    val name: String,
    val slug: String? = null,
    val category: ScheduledCategory? = null,
    val userCount: Int? = null,
    val hasPerformanceGraphFeature: Boolean? = null,
    val country: Map<String, Any>? = null,
    val hasEventPlayerStatistics: Boolean? = null,
    val displayInverseHomeAwayTeams: Boolean? = null,
    val fieldTranslations: FieldTranslations? = null
)

data class RoundInfo(
    val round: Int? = null,
    val name: String? = null,
    val slug: String? = null
)

data class TimeInfo(
    val injuryTime1: Int? = null,
    val injuryTime2: Int? = null,
    val currentPeriodStartTimestamp: Long? = null
)

data class Changes(
    val changes: List<String>? = null,
    val changeTimestamp: Long? = null
)

data class SofascoreEventResponse(
    val event: SofascoreEventDetails
) {
    data class SofascoreEventDetails(
        val id: Long,
        val status: SofascoreStatusDetails,
        val homeScore: SofascoreScoreDetails?,
        val awayScore: SofascoreScoreDetails?
    )

    data class SofascoreStatusDetails(
        val code: Int?,
        val description: String?,
        val type: String?
    )

    data class SofascoreScoreDetails(
        val current: Int?
    )
}

data class EventStatisticsResponse(
    val statistics: List<StatisticsPeriod>? = null
)

data class StatisticsPeriod(
    val period: String? = null,
    val groups: List<StatisticsGroup>? = null
)

data class StatisticsGroup(
    val groupName: String? = null,
    val statisticsItems: List<StatisticsItem>? = null
)

data class StatisticsItem(
    val name: String? = null,
    val key: String? = null,
    val home: String? = null,
    val away: String? = null,
    val compareCode: Int? = null,
    val statisticsType: String? = null,
    val valueType: String? = null,
    val homeValue: Double? = null,
    val awayValue: Double? = null
)

data class EventStatistics(
    val homeYellowCards: Int? = null,
    val homeRedCards: Int? = null,
    val awayYellowCards: Int? = null,
    val awayRedCards: Int? = null
)
