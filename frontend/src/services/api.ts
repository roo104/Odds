import {SofascoreEvent, StandingsResponse, TournamentSeasonsResponse} from '../types';

const API_BASE_URL = 'http://localhost:8080/api/football';

export const footballApi = {
  getTodayMatches: async (): Promise<SofascoreEvent[]> => {
    const response = await fetch(`${API_BASE_URL}/matches/today`);
    if (!response.ok) throw new Error('Failed to fetch today matches');
    return response.json();
  },

  getMatchesByDate: async (date: string): Promise<SofascoreEvent[]> => {
    const response = await fetch(`${API_BASE_URL}/matches/date/${date}`);
    if (!response.ok) throw new Error('Failed to fetch matches by date');
    return response.json();
  },

  refreshMatchesByDate: async (date: string): Promise<SofascoreEvent[]> => {
    const response = await fetch(`${API_BASE_URL}/matches/date/${date}/refresh`, {
      method: 'POST',
    });
    if (!response.ok) throw new Error('Failed to refresh matches by date');
    return response.json();
  },

  getTeamEvents: async (teamId: number): Promise<SofascoreEvent[]> => {
    const response = await fetch(`${API_BASE_URL}/team/${teamId}/events`);
    if (!response.ok) throw new Error('Failed to fetch team events');
    return response.json();
  },

  refreshSingleMatch: async (eventId: number, date: string): Promise<SofascoreEvent> => {
    const response = await fetch(`${API_BASE_URL}/matches/${eventId}/refresh?date=${date}`, {
      method: 'POST',
    });
    if (!response.ok) throw new Error('Failed to refresh single match');
    return response.json();
  },

  getTournamentSeasons: async (tournamentId: number): Promise<TournamentSeasonsResponse | null> => {
    const response = await fetch(`${API_BASE_URL}/tournament/${tournamentId}/seasons`);
    if (!response.ok) return null;
    return response.json();
  },

  getTournamentStandings: async (tournamentId: number, seasonId: number): Promise<StandingsResponse | null> => {
    const response = await fetch(`${API_BASE_URL}/tournament/${tournamentId}/season/${seasonId}/standings`);
    if (!response.ok) return null;
    return response.json();
  },
};
