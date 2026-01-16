import {SofascoreEvent} from '../types';

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

  getTeamEvents: async (teamId: number): Promise<SofascoreEvent[]> => {
    const response = await fetch(`${API_BASE_URL}/team/${teamId}/events`);
    if (!response.ok) throw new Error('Failed to fetch team events');
    return response.json();
  },
};
