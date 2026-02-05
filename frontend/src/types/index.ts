export interface SofascoreEvent {
  id: number;
  startTimestamp: number;
  homeTeam: Team;
  awayTeam: Team;
  homeScore?: Score;
  awayScore?: Score;
  status: Status;
  tournament: Tournament;
  season?: Season;
  odds?: Odds;
  voting?: Voting;
  homeFormScore?: number;
  awayFormScore?: number;
  lastUpdated?: number;
  isTopLeague?: boolean;
}

export interface Team {
  id: number;
  name: string;
  country?: Country;
}

export interface Country {
  name: string;
}

export interface Score {
  current?: number;
  display?: number;
}

export interface Status {
  type: string;
  description: string;
}

export interface Tournament {
  id: number;
  name: string;
  category: Category;
}

export interface Category {
  name: string;
  country?: Country;
}

export interface Odds {
  home?: string;
  draw?: string;
  away?: string;
}

export interface Voting {
  home?: number;
  draw?: number;
  away?: number;
  total?: number;
}

export interface StandingsResponse {
  standings?: StandingGroup[];
}

export interface StandingGroup {
  rows?: StandingRow[];
  name?: string;
}

export interface StandingRow {
  team: Team;
  position: number;
  matches: number;
  wins: number;
  draws: number;
  losses: number;
  scoresFor: number;
  scoresAgainst: number;
  points: number;
}

export interface TournamentSeasonsResponse {
  seasons?: Season[];
}

export interface Season {
  id: number;
  name: string;
  year?: string;
}

export interface OddsHistoryPoint {
  timestamp: number;
  home?: string;
  draw?: string;
  away?: string;
}

export interface VotesHistoryPoint {
  timestamp: number;
  home?: number;
  draw?: number;
  away?: number;
  total?: number;
}

export interface MatchHistoryResponse {
  oddsHistory: OddsHistoryPoint[];
  votesHistory: VotesHistoryPoint[];
}

export type SportType = 'Football' | 'Handball';

export type BetSelection = 'HOME' | 'DRAW' | 'AWAY';

export interface MatchBet {
  id: number;
  eventId: number;
  sport: SportType;
  selection: BetSelection;
  homeTeamName: string;
  awayTeamName: string;
  startTimestamp: number;
  finalHomeScore: number | null;
  finalAwayScore: number | null;
  odds: number | null;
  createdAt: string;
}

export interface BetsPageResponse {
  content: MatchBet[];
  page: number;
  size: number;
  totalPages: number;
  totalElements: number;
  stats: BetStatistics;
}

export interface BetStatistics {
  totalBets: number;
  finishedBets: number;
  wonBets: number;
  lostBets: number;
  winRatio: number | null;
}
