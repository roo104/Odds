import {
    BetSelection,
    BetsPageResponse,
    MatchBet,
    MatchHistoryResponse,
    MatchStatisticsResponse,
    SofascoreEvent,
    SportType,
    StandingsResponse
} from '../types';

interface WinningMatchStatistics {
  averageVote: number;
  averageOdds: number;
  totalMatches: number;
}

interface LeagueStatistics {
  tournamentId: number;
  tournamentName: string;
  averageVote: number;
  averageOdds: number;
  totalMatches: number;
}

interface WinningMatchStatisticsByLeague {
  overall: WinningMatchStatistics;
  byLeague: LeagueStatistics[];
}

export interface LeagueProfitability {
  tournamentId: number | null;
  tournamentName: string | null;
  minVoteThreshold: number | null;
  totalMatches: number;
  matchesAboveThreshold: number;
  roi: number | null;
  favoriteWins: number;
  averageFavoriteWinOdds: number | null;
}

export interface MatchBettingDetail {
  homeTeamName: string;
  awayTeamName: string;
  homeScore: number;
  awayScore: number;
  oddsHome: number | null;
  oddsDraw: number | null;
  oddsAway: number | null;
  votingHome: number | null;
  votingDraw: number | null;
  votingAway: number | null;
  favoriteVote: number | null;
  favoriteOdds: number | null;
  favoriteWon: boolean | null;
  tournamentName: string;
}

export interface ProfitabilityResponse {
  overall: LeagueProfitability | null;
  byLeague: LeagueProfitability[];
  matches: MatchBettingDetail[] | null;
}

interface MatchesApi {
  getTodayMatches: () => Promise<SofascoreEvent[]>;
  getMatchesByDate: (date: string, includeAllLeagues?: boolean) => Promise<SofascoreEvent[]>;
  refreshMatchesByDate: (date: string, includeAllLeagues?: boolean) => Promise<SofascoreEvent[]>;
  getTeamEvents: (teamId: number) => Promise<SofascoreEvent[]>;
  refreshSingleMatch: (eventId: number, date: string) => Promise<SofascoreEvent>;
  getTournamentStandings: (tournamentId: number, seasonId: number) => Promise<StandingsResponse | null>;
  getMatchHistory: (eventId: number) => Promise<MatchHistoryResponse>;
  getMatchStatistics: (eventId: number) => Promise<MatchStatisticsResponse>;
  getWinningMatchStatistics?: () => Promise<WinningMatchStatistics>;
  getWinningMatchStatisticsByLeague?: (country?: string, topLeaguesOnly?: boolean) => Promise<WinningMatchStatisticsByLeague>;
  getProfitableThresholds?: (country?: string, topLeaguesOnly?: boolean) => Promise<ProfitabilityResponse>;
}

export default MatchesApi

const FOOTBALL_API_BASE_URL = 'http://localhost:8080/api/football';
const HANDBALL_API_BASE_URL = 'http://localhost:8080/api/handball';
const BETS_API_BASE_URL = 'http://localhost:8080/api/bets';
const STANDINGS_API_BASE_URL = 'http://localhost:8080/api/standings';

interface CreateBetRequest {
  eventId: number;
  sport: SportType;
  selection: BetSelection;
  homeTeamName: string;
  awayTeamName: string;
  startTimestamp: number;
  odds: number | null;
}

export const footballApi: MatchesApi = {
  getTodayMatches: async (): Promise<SofascoreEvent[]> => {
    const response = await fetch(`${FOOTBALL_API_BASE_URL}/matches/today`);
    if (!response.ok) throw new Error('Failed to fetch today matches');
    return response.json();
  },

  getMatchesByDate: async (date: string, includeAllLeagues: boolean = false): Promise<SofascoreEvent[]> => {
    const url = `${FOOTBALL_API_BASE_URL}/matches/date/${date}${includeAllLeagues ? '?includeAllLeagues=true' : ''}`;
    const response = await fetch(url);
    if (!response.ok) throw new Error('Failed to fetch matches by date');
    return response.json();
  },

  refreshMatchesByDate: async (date: string, includeAllLeagues: boolean = false): Promise<SofascoreEvent[]> => {
    const url = `${FOOTBALL_API_BASE_URL}/matches/date/${date}/refresh${includeAllLeagues ? '?includeAllLeagues=true' : ''}`;
    const response = await fetch(url, {
      method: 'POST',
    });
    if (!response.ok) throw new Error('Failed to refresh matches by date');
    return response.json();
  },

  getTeamEvents: async (teamId: number): Promise<SofascoreEvent[]> => {
    const response = await fetch(`${FOOTBALL_API_BASE_URL}/team/${teamId}/events`);
    if (!response.ok) throw new Error('Failed to fetch team events');
    return response.json();
  },

  refreshSingleMatch: async (eventId: number, date: string): Promise<SofascoreEvent> => {
    const response = await fetch(`${FOOTBALL_API_BASE_URL}/matches/${eventId}/refresh?date=${date}`, {
      method: 'POST',
    });
    if (!response.ok) throw new Error('Failed to refresh single match');
    return response.json();
  },

  getTournamentStandings: async (tournamentId: number, seasonId: number): Promise<StandingsResponse | null> => {
    try {
      const response = await fetch(`${FOOTBALL_API_BASE_URL}/tournament/${tournamentId}/season/${seasonId}/standings`);
      console.log('getTournamentStandings response status:', response.status, 'for tournament:', tournamentId, 'season:', seasonId);
      if (!response.ok) {
        console.warn('getTournamentStandings failed:', response.status, response.statusText);
        return null;
      }
      const data = await response.json();
      console.log('getTournamentStandings data:', data);
      return data;
    } catch (error) {
      console.error('getTournamentStandings error:', error);
      return null;
    }
  },

  getMatchHistory: async (eventId: number): Promise<MatchHistoryResponse> => {
    const response = await fetch(`${FOOTBALL_API_BASE_URL}/matches/${eventId}/history`);
    if (!response.ok) throw new Error('Failed to fetch match history');
    return response.json();
  },

  getMatchStatistics: async (eventId: number): Promise<MatchStatisticsResponse> => {
    const response = await fetch(`${FOOTBALL_API_BASE_URL}/matches/${eventId}/statistics`);
    if (!response.ok) throw new Error('Failed to fetch match statistics');
    return response.json();
  },

  getWinningMatchStatistics: async (): Promise<WinningMatchStatistics> => {
    const response = await fetch(`${FOOTBALL_API_BASE_URL}/statistics/winning-matches`);
    if (!response.ok) throw new Error('Failed to fetch winning match statistics');
    return response.json();
  },

  getWinningMatchStatisticsByLeague: async (country?: string, topLeaguesOnly?: boolean): Promise<WinningMatchStatisticsByLeague> => {
    const params = new URLSearchParams();
    if (country) params.append('country', country);
    if (topLeaguesOnly) params.append('topLeaguesOnly', 'true');
    const url = `${FOOTBALL_API_BASE_URL}/statistics/winning-matches-by-league${params.toString() ? '?' + params.toString() : ''}`;
    const response = await fetch(url);
    if (!response.ok) throw new Error('Failed to fetch winning match statistics by league');
    return response.json();
  },

  getProfitableThresholds: async (country?: string, topLeaguesOnly?: boolean): Promise<ProfitabilityResponse> => {
    const params = new URLSearchParams();
    if (country) params.append('country', country);
    if (topLeaguesOnly) params.append('topLeaguesOnly', 'true');
    const url = `${FOOTBALL_API_BASE_URL}/statistics/profitable-thresholds${params.toString() ? '?' + params.toString() : ''}`;
    const response = await fetch(url);
    if (!response.ok) throw new Error('Failed to fetch profitable thresholds');
    return response.json();
  },
};

export const handballApi: MatchesApi = {
  getTodayMatches: async (): Promise<SofascoreEvent[]> => {
    const response = await fetch(`${HANDBALL_API_BASE_URL}/matches/today`);
    if (!response.ok) throw new Error('Failed to fetch today matches');
    return response.json();
  },

  getMatchesByDate: async (date: string, includeAllLeagues: boolean = false): Promise<SofascoreEvent[]> => {
    const url = `${HANDBALL_API_BASE_URL}/matches/date/${date}${includeAllLeagues ? '?includeAllLeagues=true' : ''}`;
    const response = await fetch(url);
    if (!response.ok) throw new Error('Failed to fetch matches by date');
    return response.json();
  },

  refreshMatchesByDate: async (date: string, includeAllLeagues: boolean = false): Promise<SofascoreEvent[]> => {
    const url = `${HANDBALL_API_BASE_URL}/matches/date/${date}/refresh${includeAllLeagues ? '?includeAllLeagues=true' : ''}`;
    const response = await fetch(url, {
      method: 'POST',
    });
    if (!response.ok) throw new Error('Failed to refresh matches by date');
    return response.json();
  },

  getTeamEvents: async (teamId: number): Promise<SofascoreEvent[]> => {
    const response = await fetch(`${HANDBALL_API_BASE_URL}/team/${teamId}/events`);
    if (!response.ok) throw new Error('Failed to fetch team events');
    return response.json();
  },

  refreshSingleMatch: async (eventId: number, date: string): Promise<SofascoreEvent> => {
    const response = await fetch(`${HANDBALL_API_BASE_URL}/matches/${eventId}/refresh?date=${date}`, {
      method: 'POST',
    });
    if (!response.ok) throw new Error('Failed to refresh single match');
    return response.json();
  },

  getTournamentStandings: async (tournamentId: number, seasonId: number): Promise<StandingsResponse | null> => {
    try {
      const response = await fetch(`${HANDBALL_API_BASE_URL}/tournament/${tournamentId}/season/${seasonId}/standings`);
      console.log('getTournamentStandings response status:', response.status, 'for tournament:', tournamentId, 'season:', seasonId);
      if (!response.ok) {
        console.warn('getTournamentStandings failed:', response.status, response.statusText);
        return null;
      }
      const data = await response.json();
      console.log('getTournamentStandings data:', data);
      return data;
    } catch (error) {
      console.error('getTournamentStandings error:', error);
      return null;
    }
  },

  getMatchHistory: async (eventId: number): Promise<MatchHistoryResponse> => {
    const response = await fetch(`${HANDBALL_API_BASE_URL}/matches/${eventId}/history`);
    if (!response.ok) throw new Error('Failed to fetch match history');
    return response.json();
  },

  getMatchStatistics: async (eventId: number): Promise<MatchStatisticsResponse> => {
    const response = await fetch(`${HANDBALL_API_BASE_URL}/matches/${eventId}/statistics`);
    if (!response.ok) throw new Error('Failed to fetch match statistics');
    return response.json();
  },

  getWinningMatchStatistics: async (): Promise<WinningMatchStatistics> => {
    const response = await fetch(`${HANDBALL_API_BASE_URL}/statistics/winning-matches`);
    if (!response.ok) throw new Error('Failed to fetch winning match statistics');
    return response.json();
  },

  getWinningMatchStatisticsByLeague: async (country?: string, topLeaguesOnly?: boolean): Promise<WinningMatchStatisticsByLeague> => {
    const params = new URLSearchParams();
    if (country) params.append('country', country);
    if (topLeaguesOnly) params.append('topLeaguesOnly', 'true');
    const url = `${HANDBALL_API_BASE_URL}/statistics/winning-matches-by-league${params.toString() ? '?' + params.toString() : ''}`;
    const response = await fetch(url);
    if (!response.ok) throw new Error('Failed to fetch winning match statistics by league');
    return response.json();
  },

  getProfitableThresholds: async (country?: string): Promise<ProfitabilityResponse> => {
    const params = new URLSearchParams();
    if (country) params.append('country', country);
    const url = `${HANDBALL_API_BASE_URL}/statistics/profitable-thresholds${params.toString() ? '?' + params.toString() : ''}`;
    const response = await fetch(url);
    if (!response.ok) throw new Error('Failed to fetch profitable thresholds');
    return response.json();
  },
};

export const standingsApi = {
  getAvailableCountries: async (): Promise<string[]> => {
    const response = await fetch(`${STANDINGS_API_BASE_URL}/countries`);
    if (!response.ok) throw new Error('Failed to fetch available countries');
    return response.json();
  },

  getStandingsByCountry: async (country: string): Promise<Record<string, StandingsResponse>> => {
    const response = await fetch(`${STANDINGS_API_BASE_URL}/country/${encodeURIComponent(country)}`);
    if (!response.ok) throw new Error('Failed to fetch standings by country');
    return response.json();
  },

  getTournamentStandings: async (tournamentId: number, seasonId: number): Promise<StandingsResponse | null> => {
    try {
      const response = await fetch(`${STANDINGS_API_BASE_URL}/tournament/${tournamentId}/season/${seasonId}`);
      if (!response.ok) return null;
      return response.json();
    } catch (error) {
      console.error('getTournamentStandings error:', error);
      return null;
    }
  },
};

export const betsApi = {
  createBet: async (payload: CreateBetRequest): Promise<MatchBet> => {
    const response = await fetch(BETS_API_BASE_URL, {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify(payload),
    });
    if (!response.ok) {
      const errorText = await response.text();
      console.error('Failed to create bet:', response.status, errorText);
      throw new Error(`Failed to create bet: ${response.status} - ${errorText}`);
    }
    return response.json();
  },

  getBets: async (page: number, size: number): Promise<BetsPageResponse> => {
    const response = await fetch(`${BETS_API_BASE_URL}?page=${page}&size=${size}`);
    if (!response.ok) throw new Error('Failed to fetch bets');
    return response.json();
  },

  refreshBetScore: async (betId: number): Promise<MatchBet> => {
    const response = await fetch(`${BETS_API_BASE_URL}/${betId}/refresh`, {
      method: 'POST',
    });
    if (!response.ok) throw new Error('Failed to refresh bet score');
    return response.json();
  },

  deleteBet: async (betId: number): Promise<void> => {
    const response = await fetch(`${BETS_API_BASE_URL}/${betId}`, {
      method: 'DELETE',
    });
    if (!response.ok) throw new Error('Failed to delete bet');
  },

  updateOdds: async (betId: number, odds: number | null): Promise<MatchBet> => {
    const response = await fetch(`${BETS_API_BASE_URL}/${betId}/odds`, {
      method: 'PATCH',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({ odds }),
    });
    if (!response.ok) throw new Error('Failed to update odds');
    return response.json();
  },
};
