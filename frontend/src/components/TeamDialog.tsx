import {useEffect, useState} from 'react';
import {BetSelection, MatchHistoryResponse, SofascoreEvent, SportType, StandingsResponse} from '../types';
import MatchesApi, {betsApi} from '../services/api';
import {CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis} from 'recharts';
import './TeamDialog.css';

interface TeamDialogProps {
  event: SofascoreEvent;
  onClose: () => void;
  api: MatchesApi;
  sport: SportType;
}

function TeamDialog({ event, onClose, api, sport }: TeamDialogProps) {
  const [homeTeamEvents, setHomeTeamEvents] = useState<SofascoreEvent[]>([]);
  const [awayTeamEvents, setAwayTeamEvents] = useState<SofascoreEvent[]>([]);
  const [homeTeamPage, setHomeTeamPage] = useState(0);
  const [awayTeamPage, setAwayTeamPage] = useState(0);
  const [standings, setStandings] = useState<StandingsResponse | null>(null);
  const [matchHistory, setMatchHistory] = useState<MatchHistoryResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<'matches' | 'standings' | 'history'>('matches');
  const [betStatus, setBetStatus] = useState<{ type: 'success' | 'error'; message: string } | null>(null);
  const [betLoading, setBetLoading] = useState(false);

  const MATCHES_PER_PAGE = 5;

  useEffect(() => {
    setStandings(null); // Reset standings when event changes
    setMatchHistory(null); // Reset match history when event changes
    setHomeTeamPage(0); // Reset pagination when event changes
    setAwayTeamPage(0); // Reset pagination when event changes
    setBetStatus(null);
    loadTeamEvents();
    setActiveTab('matches');
  }, [event]);

  const loadTeamEvents = async () => {
    setLoading(true);
    try {
      const [home, away, history] = await Promise.all([
        api.getTeamEvents(event.homeTeam.id),
        api.getTeamEvents(event.awayTeam.id),
        api.getMatchHistory(event.id),
      ]);
      setHomeTeamEvents(home);
      setAwayTeamEvents(away);
      setMatchHistory(history);

      // Try to fetch league standings if it's a league tournament
      // Skip if tournament ID is 0 (invalid/not saved properly)
      console.log('Tournament ID:', event.tournament.id, 'Tournament name:', event.tournament.name, 'Category:', event.tournament.category.name);
      if (event.tournament.id > 0 && event.season?.id) {
        console.log('Fetching standings for tournament:', event.tournament.id, 'season:', event.season.id);
        const standingsData = await api.getTournamentStandings(event.tournament.id, event.season.id);
        console.log('Standings data for', event.tournament.name, ':', standingsData);
        setStandings(standingsData);
      } else {
        console.log('Skipping standings fetch - tournament ID is 0 or season not available');
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

  const formatHistoryTimestamp = (timestamp: number) => {
    const date = new Date(timestamp * 1000);
    const day = String(date.getDate()).padStart(2, '0');
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const year = date.getFullYear();
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    const seconds = String(date.getSeconds()).padStart(2, '0');
    return `${day}/${month}/${year} ${hours}:${minutes}:${seconds}`;
  };

  const formatOdds = (fractionalOdds: string | undefined) => {
    if (!fractionalOdds) return '-';
    try {
      const parts = fractionalOdds.split('/');
      if (parts.length === 2) {
        const decimal = parseInt(parts[0]) / parseInt(parts[1]) + 1;
        return decimal.toFixed(2);
      }
      return fractionalOdds;
    } catch {
      return fractionalOdds;
    }
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

  const homeTeamPaginated = homeTeamEvents.slice(homeTeamPage * MATCHES_PER_PAGE, (homeTeamPage + 1) * MATCHES_PER_PAGE);
  const awayTeamPaginated = awayTeamEvents.slice(awayTeamPage * MATCHES_PER_PAGE, (awayTeamPage + 1) * MATCHES_PER_PAGE);
  const homeTotalPages = Math.ceil(homeTeamEvents.length / MATCHES_PER_PAGE);
  const awayTotalPages = Math.ceil(awayTeamEvents.length / MATCHES_PER_PAGE);

  const homePoints = calculatePoints(homeTeamEvents.slice(0, 5), event.homeTeam.id);
  const awayPoints = calculatePoints(awayTeamEvents.slice(0, 5), event.awayTeam.id);

  const handlePlaceBet = async (selection: BetSelection) => {
    setBetLoading(true);
    setBetStatus(null);
    try {
      await betsApi.createBet({
        eventId: event.id,
        sport,
        selection,
        homeTeamName: event.homeTeam.name,
        awayTeamName: event.awayTeam.name,
        startTimestamp: event.startTimestamp,
        odds: null,
      });
      setBetStatus({ type: 'success', message: `Tagged bet: ${selection}` });
    } catch (error: any) {
      const message = error?.message || 'Failed to tag bet';
      setBetStatus({ type: 'error', message });
    } finally {
      setBetLoading(false);
    }
  };

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

        <div className="bet-section">
          <div className="bet-title">Tag a bet</div>
          <div className="bet-actions">
            <button
              type="button"
              className="bet-button"
              onClick={() => handlePlaceBet('HOME')}
              disabled={betLoading}
            >
              {event.homeTeam.name}
            </button>
            <button
              type="button"
              className="bet-button"
              onClick={() => handlePlaceBet('DRAW')}
              disabled={betLoading}
            >
              Draw
            </button>
            <button
              type="button"
              className="bet-button"
              onClick={() => handlePlaceBet('AWAY')}
              disabled={betLoading}
            >
              {event.awayTeam.name}
            </button>
          </div>
          {betStatus && (
            <div className={`bet-status ${betStatus.type}`}>
              {betStatus.message}
            </div>
          )}
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
              <button
                type="button"
                className={`dialog-tab ${activeTab === 'history' ? 'active' : ''}`}
                onClick={() => setActiveTab('history')}
                disabled={!matchHistory || (matchHistory.oddsHistory.length === 0 && matchHistory.votesHistory.length === 0)}
                title={(!matchHistory || (matchHistory.oddsHistory.length === 0 && matchHistory.votesHistory.length === 0)) ? 'History not available' : 'Odds and votes evolution'}
              >
                History
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
                      {homeTeamPaginated.map((match) => (
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
                  {homeTotalPages > 1 && (
                    <div className="pagination">
                      <button
                        onClick={() => setHomeTeamPage(prev => Math.max(0, prev - 1))}
                        disabled={homeTeamPage === 0}
                      >
                        Previous
                      </button>
                      <span>Page {homeTeamPage + 1} of {homeTotalPages}</span>
                      <button
                        onClick={() => setHomeTeamPage(prev => Math.min(homeTotalPages - 1, prev + 1))}
                        disabled={homeTeamPage >= homeTotalPages - 1}
                      >
                        Next
                      </button>
                    </div>
                  )}
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
                      {awayTeamPaginated.map((match) => (
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
                  {awayTotalPages > 1 && (
                    <div className="pagination">
                      <button
                        onClick={() => setAwayTeamPage(prev => Math.max(0, prev - 1))}
                        disabled={awayTeamPage === 0}
                      >
                        Previous
                      </button>
                      <span>Page {awayTeamPage + 1} of {awayTotalPages}</span>
                      <button
                        onClick={() => setAwayTeamPage(prev => Math.min(awayTotalPages - 1, prev + 1))}
                        disabled={awayTeamPage >= awayTotalPages - 1}
                      >
                        Next
                      </button>
                    </div>
                  )}
                </div>
              </div>
            ) : activeTab === 'standings' ? (
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
            ) : (
              <div className="history-section">
                <h3>Odds & Votes Evolution</h3>
                {matchHistory && matchHistory.oddsHistory.length > 0 && (
                  <div className="history-subsection">
                    <h4>Odds History</h4>
                    {matchHistory.oddsHistory.length > 1 && (
                      <div style={{ width: '100%', height: 300, marginBottom: '20px' }}>
                        <ResponsiveContainer width="100%" height="100%">
                          <LineChart
                            data={matchHistory.oddsHistory.map(point => ({
                              time: formatHistoryTimestamp(point.timestamp),
                              home: point.home ? parseFloat(formatOdds(point.home)) : null,
                              draw: point.draw ? parseFloat(formatOdds(point.draw)) : null,
                              away: point.away ? parseFloat(formatOdds(point.away)) : null,
                            }))}
                            margin={{ top: 5, right: 30, left: 20, bottom: 5 }}
                          >
                            <CartesianGrid strokeDasharray="3 3" />
                            <XAxis dataKey="time" />
                            <YAxis label={{ value: 'Odds', angle: -90, position: 'insideLeft' }} />
                            <Tooltip />
                            <Legend />
                            <Line type="monotone" dataKey="home" stroke="#8884d8" name={event.homeTeam.name} />
                            <Line type="monotone" dataKey="draw" stroke="#82ca9d" name="Draw" />
                            <Line type="monotone" dataKey="away" stroke="#ffc658" name={event.awayTeam.name} />
                          </LineChart>
                        </ResponsiveContainer>
                      </div>
                    )}
                    <table className="history-table">
                      <thead>
                        <tr>
                          <th>Time</th>
                          <th>{event.homeTeam.name}</th>
                          <th>Draw</th>
                          <th>{event.awayTeam.name}</th>
                        </tr>
                      </thead>
                      <tbody>
                        {matchHistory.oddsHistory.slice().reverse().map((point, idx) => (
                          <tr key={idx}>
                            <td>{formatHistoryTimestamp(point.timestamp)}</td>
                            <td>{formatOdds(point.home)}</td>
                            <td>{formatOdds(point.draw)}</td>
                            <td>{formatOdds(point.away)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
                {matchHistory && matchHistory.votesHistory.length > 0 && (
                  <div className="history-subsection">
                    <h4>Votes History</h4>
                    {matchHistory.votesHistory.length > 1 && (
                      <div style={{ width: '100%', height: 300, marginBottom: '20px' }}>
                        <ResponsiveContainer width="100%" height="100%">
                          <LineChart
                            data={matchHistory.votesHistory.map(point => ({
                              time: formatHistoryTimestamp(point.timestamp),
                              home: point.home ?? null,
                              draw: point.draw ?? null,
                              away: point.away ?? null,
                            }))}
                            margin={{ top: 5, right: 30, left: 20, bottom: 5 }}
                          >
                            <CartesianGrid strokeDasharray="3 3" />
                            <XAxis dataKey="time" />
                            <YAxis label={{ value: 'Votes (%)', angle: -90, position: 'insideLeft' }} domain={[0, 100]} />
                            <Tooltip />
                            <Legend />
                            <Line type="monotone" dataKey="home" stroke="#8884d8" name={event.homeTeam.name} />
                            <Line type="monotone" dataKey="draw" stroke="#82ca9d" name="Draw" />
                            <Line type="monotone" dataKey="away" stroke="#ffc658" name={event.awayTeam.name} />
                          </LineChart>
                        </ResponsiveContainer>
                      </div>
                    )}
                    <table className="history-table">
                      <thead>
                        <tr>
                          <th>Time</th>
                          <th>{event.homeTeam.name} (%)</th>
                          <th>Draw (%)</th>
                          <th>{event.awayTeam.name} (%)</th>
                          <th>Total Votes</th>
                        </tr>
                      </thead>
                      <tbody>
                        {matchHistory.votesHistory.slice().reverse().map((point, idx) => (
                          <tr key={idx}>
                            <td>{formatHistoryTimestamp(point.timestamp)}</td>
                            <td>{point.home ?? '-'}</td>
                            <td>{point.draw ?? '-'}</td>
                            <td>{point.away ?? '-'}</td>
                            <td>{point.total ?? '-'}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
                {matchHistory && matchHistory.oddsHistory.length === 0 && matchHistory.votesHistory.length === 0 && (
                  <p>No historical data available yet. Data will be recorded as odds and votes are refreshed.</p>
                )}
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}

export default TeamDialog;
