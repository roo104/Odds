import {
    BetSelection,
    BetsPageResponse,
    MatchBet,
    MatchHistoryResponse,
    SofascoreEvent,
    SportType,
    StandingsResponse
} from '../types';

interface MatchesApi {
  getTodayMatches: () => Promise<SofascoreEvent[]>;
  getMatchesByDate: (date: string, includeAllLeagues?: boolean) => Promise<SofascoreEvent[]>;
  refreshMatchesByDate: (date: string, includeAllLeagues?: boolean) => Promise<SofascoreEvent[]>;
  getTeamEvents: (teamId: number) => Promise<SofascoreEvent[]>;
  refreshSingleMatch: (eventId: number, date: string) => Promise<SofascoreEvent>;
  getTournamentStandings: (tournamentId: number, seasonId: number) => Promise<StandingsResponse | null>;
  getMatchHistory: (eventId: number) => Promise<MatchHistoryResponse>;
}

export default MatchesApi

const FOOTBALL_API_BASE_URL = 'http://localhost:8080/api/football';
const HANDBALL_API_BASE_URL = 'http://localhost:8080/api/handball';
const BETS_API_BASE_URL = 'http://localhost:8080/api/bets';

interface CreateBetRequest {
  eventId: number;
  sport: SportType;
  selection: BetSelection;
  homeTeamName: string;
  awayTeamName: string;
  startTimestamp: number;
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
};
