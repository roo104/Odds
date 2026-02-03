import {useEffect, useState} from 'react';
import {SofascoreEvent, StandingsResponse} from '../types';
import {footballApi} from '../services/api';
import './TeamDialog.css';

interface TeamDialogProps {
  event: SofascoreEvent;
  onClose: () => void;
}

function TeamDialog({ event, onClose }: TeamDialogProps) {
  const [homeTeamEvents, setHomeTeamEvents] = useState<SofascoreEvent[]>([]);
  const [awayTeamEvents, setAwayTeamEvents] = useState<SofascoreEvent[]>([]);
  const [standings, setStandings] = useState<StandingsResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<'matches' | 'standings'>('matches');

  useEffect(() => {
    setStandings(null); // Reset standings when event changes
    loadTeamEvents();
    setActiveTab('matches');
  }, [event]);

  const loadTeamEvents = async () => {
    setLoading(true);
    try {
      const [home, away] = await Promise.all([
        footballApi.getTeamEvents(event.homeTeam.id),
        footballApi.getTeamEvents(event.awayTeam.id),
      ]);
      setHomeTeamEvents(home.slice(0, 5));
      setAwayTeamEvents(away.slice(0, 5));

      // Try to fetch league standings if it's a league tournament
      // Skip if tournament ID is 0 (invalid/not saved properly)
      console.log('Tournament ID:', event.tournament.id, 'Tournament name:', event.tournament.name, 'Category:', event.tournament.category.name);
      if (event.tournament.id > 0) {
        // First get the seasons for this tournament
        console.log('Fetching seasons for tournament:', event.tournament.id);
        const seasonsResponse = await footballApi.getTournamentSeasons(event.tournament.id);
        console.log('Seasons response:', seasonsResponse);
        if (seasonsResponse && seasonsResponse.seasons && seasonsResponse.seasons.length > 0) {
          // Use the most recent season (first in the list)
          const currentSeason = seasonsResponse.seasons[0];
          console.log('Using season:', currentSeason.name, '(ID:', currentSeason.id, ')');

          // Skip if season name indicates non-football sport
          const seasonName = currentSeason.name || '';
          if (seasonName.toLowerCase().includes('fivb') ||
              seasonName.toLowerCase().includes('volleyball') ||
              seasonName.toLowerCase().includes('basketball') ||
              seasonName.toLowerCase().includes('world championship')) {
            console.log('Skipping standings - non-football season:', seasonName);
            setStandings(null);
          } else {
            const standingsData = await footballApi.getTournamentStandings(event.tournament.id, currentSeason.id);
            console.log('Standings data for', event.tournament.name, ':', standingsData);
            setStandings(standingsData);
          }
        } else {
          console.log('No seasons found for tournament');
        }
      } else {
        console.log('Skipping standings fetch - tournament ID is 0');
      }
    } catch (error) {
      console.error('Failed to load team events:', error);
    } finally {
      setLoading(false);
    }
  };

  const calculatePoints = (matches: SofascoreEvent[], teamId: number): number => {
    return matches.reduce((points, match) => {
      const homeScore = match.homeScore?.current;
      const awayScore = match.awayScore?.current;

      if (homeScore !== undefined && awayScore !== undefined) {
        const isHomeTeam = match.homeTeam.id === teamId;
        if (homeScore === awayScore) return points + 1; // Draw
        if ((isHomeTeam && homeScore > awayScore) || (!isHomeTeam && awayScore > homeScore)) {
          return points + 2; // Win
        }
      }
      return points; // Loss
    }, 0);
  };

  const formatDateTime = (timestamp: number) => {
    const date = new Date(timestamp * 1000);
    const day = String(date.getDate()).padStart(2, '0');
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const year = date.getFullYear();
    return `${day}/${month}/${year}`;
  };

  const getMatchResult = (match: SofascoreEvent, teamId: number): 'win' | 'loss' | 'draw' | null => {
    const homeScore = match.homeScore?.current;
    const awayScore = match.awayScore?.current;

    if (homeScore === undefined || awayScore === undefined) return null;

    if (homeScore === awayScore) return 'draw';
    const isHomeTeam = match.homeTeam.id === teamId;
    const won = (isHomeTeam && homeScore > awayScore) || (!isHomeTeam && awayScore > homeScore);
    return won ? 'win' : 'loss';
  };

  const homePoints = calculatePoints(homeTeamEvents, event.homeTeam.id);
  const awayPoints = calculatePoints(awayTeamEvents, event.awayTeam.id);

  return (
    <div className="dialog-overlay" onClick={onClose}>
      <div className="dialog-content" onClick={(e) => e.stopPropagation()}>
        <div className="dialog-header">
          <h2>
            Previous Matches: {event.homeTeam.name} vs {event.awayTeam.name}
          </h2>
          <button className="close-button" onClick={onClose}>
            ×
          </button>
        </div>

        {event.voting && (
          <div className="voting-section">
            <h3>Voting Results {event.voting.total ? `(${event.voting.total})` : ''}</h3>
            <div className="voting-labels-row">
              <span className="voting-label-top">{event.homeTeam.name} ({event.voting.home || 0}%)</span>
              <span className="voting-label-top">Draw ({event.voting.draw || 0}%)</span>
              <span className="voting-label-top">{event.awayTeam.name} ({event.voting.away || 0}%)</span>
            </div>
            <div className="voting-bar-container">
              <div
                className="voting-bar home-vote"
                style={{ width: `${event.voting.home || 0}%` }}
              />
              <div
                className="voting-bar draw-vote"
                style={{ width: `${event.voting.draw || 0}%` }}
              />
              <div
                className="voting-bar away-vote"
                style={{ width: `${event.voting.away || 0}%` }}
              />
            </div>
          </div>
        )}

        {loading ? (
          <div className="loading">Loading team events...</div>
        ) : (
          <>
            <div className="dialog-tabs">
              <button
                type="button"
                className={`dialog-tab ${activeTab === 'matches' ? 'active' : ''}`}
                onClick={() => setActiveTab('matches')}
              >
                Recent Matches
              </button>
              <button
                type="button"
                className={`dialog-tab ${activeTab === 'standings' ? 'active' : ''}`}
                onClick={() => setActiveTab('standings')}
                disabled={!standings?.standings?.length}
                title={!standings?.standings?.length ? 'Standings not available' : 'League standings'}
              >
                Standings
              </button>
            </div>

            {activeTab === 'matches' ? (
              <div className="dialog-body">
                <div className="team-section">
                  <h4>
                    {event.homeTeam.name} - Recent Matches (Points: {homePoints})
                  </h4>
                  <table className="team-events-table">
                    <thead>
                      <tr>
                        <th>Date</th>
                        <th>Home</th>
                        <th>Away</th>
                        <th>Score</th>
                        <th>Tournament</th>
                      </tr>
                    </thead>
                    <tbody>
                      {homeTeamEvents.map((match) => (
                        <tr
                          key={match.id}
                          className={`result-${getMatchResult(match, event.homeTeam.id)}`}
                        >
                          <td>{formatDateTime(match.startTimestamp)}</td>
                          <td>{match.homeTeam.name}</td>
                          <td>{match.awayTeam.name}</td>
                          <td>
                            {match.homeScore?.current ?? '-'} - {match.awayScore?.current ?? '-'}
                          </td>
                          <td>{match.tournament.name}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                <div className="team-section">
                  <h4>
                    {event.awayTeam.name} - Recent Matches (Points: {awayPoints})
                  </h4>
                  <table className="team-events-table">
                    <thead>
                      <tr>
                        <th>Date</th>
                        <th>Home</th>
                        <th>Away</th>
                        <th>Score</th>
                        <th>Tournament</th>
                      </tr>
                    </thead>
                    <tbody>
                      {awayTeamEvents.map((match) => (
                        <tr
                          key={match.id}
                          className={`result-${getMatchResult(match, event.awayTeam.id)}`}
                        >
                          <td>{formatDateTime(match.startTimestamp)}</td>
                          <td>{match.homeTeam.name}</td>
                          <td>{match.awayTeam.name}</td>
                          <td>
                            {match.homeScore?.current ?? '-'} - {match.awayScore?.current ?? '-'}
                          </td>
                          <td>{match.tournament.name}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            ) : (
              <div className="standings-section">
                <h3>League Standings</h3>
                {standings?.standings?.map((group, idx) => (
                  <div key={idx}>
                    {group.name && <h4>{group.name}</h4>}
                    <table className="standings-table">
                      <thead>
                        <tr>
                          <th>Pos</th>
                          <th>Team</th>
                          <th>P</th>
                          <th>W</th>
                          <th>D</th>
                          <th>L</th>
                          <th>GF</th>
                          <th>GA</th>
                          <th>GD</th>
                          <th>Pts</th>
                        </tr>
                      </thead>
                      <tbody>
                        {group.rows?.map((row) => {
                          const isHomeTeam = row.team.id === event.homeTeam.id;
                          const isAwayTeam = row.team.id === event.awayTeam.id;
                          const rowClass = isHomeTeam || isAwayTeam ? 'highlight-team' : '';
                          return (
                            <tr key={row.team.id} className={rowClass}>
                              <td>{row.position}</td>
                              <td>{row.team.name}</td>
                              <td>{row.matches}</td>
                              <td>{row.wins}</td>
                              <td>{row.draws}</td>
                              <td>{row.losses}</td>
                              <td>{row.scoresFor}</td>
                              <td>{row.scoresAgainst}</td>
                              <td>{row.scoresFor - row.scoresAgainst}</td>
                              <td><strong>{row.points}</strong></td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                ))}
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}

export default TeamDialog;
