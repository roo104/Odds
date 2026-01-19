package jp.odds.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class SofascoreEventsResponse(
    val events: List<SofascoreEvent>
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SofascoreEvent(
    val id: Long,
    val startTimestamp: Long,
    val homeTeam: Team,
    val awayTeam: Team,
    val homeScore: Score? = null,
    val awayScore: Score? = null,
    val status: Status,
    val tournament: Tournament,
    val vote: VoteData? = null,
    var odds: Odds? = null,
    var voting: Voting? = null,
    var homeFormScore: Int? = null,
    var awayFormScore: Int? = null,
    var lastUpdated: Long? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Team(
    val id: Long,
    val name: String,
    val country: Country? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Country(
    val name: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Score(
    val current: Int? = null,
    val display: Int? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Status(
    val type: String,
    val description: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Tournament(
    val id: Long,
    val name: String,
    val category: Category
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Category(
    val name: String,
    val country: Country? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OddsResponse(
    val markets: List<Market>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Market(
    val marketName: String,
    val choices: List<Choice>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Choice(
    val name: String,
    val fractionalValue: String? = null,
    val sourceId: Int? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Odds(
    val home: String? = null,
    val draw: String? = null,
    val away: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class VotingResponse(
    val vote: VoteData? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class VoteData(
    val vote1: Int? = null,
    val vote2: Int? = null,
    val voteX: Int? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Voting(
    val home: Int? = null,
    val draw: Int? = null,
    val away: Int? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EventDetailsResponse(
    val event: SofascoreEvent? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TeamEventsResponse(
    val previousEvent: SofascoreEvent? = null,
    val nextEvent: SofascoreEvent? = null,
    val events: List<SofascoreEvent>? = null
)
