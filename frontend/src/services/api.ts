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
    try {
      const response = await fetch(`${API_BASE_URL}/tournament/${tournamentId}/seasons`);
      console.log('getTournamentSeasons response status:', response.status, 'for tournament:', tournamentId);
      if (!response.ok) {
        console.warn('getTournamentSeasons failed:', response.status, response.statusText);
        return null;
      }
      const data = await response.json();
      console.log('getTournamentSeasons data:', data);
      return data;
    } catch (error) {
      console.error('getTournamentSeasons error:', error);
      return null;
    }
  },

  getTournamentStandings: async (tournamentId: number, seasonId: number): Promise<StandingsResponse | null> => {
    try {
      const response = await fetch(`${API_BASE_URL}/tournament/${tournamentId}/season/${seasonId}/standings`);
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
};
