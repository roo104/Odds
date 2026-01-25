import {useEffect, useState} from 'react';
import {SofascoreEvent} from '../types';
import {footballApi} from '../services/api';
import './TeamDialog.css';

interface TeamDialogProps {
  event: SofascoreEvent;
  onClose: () => void;
}

function TeamDialog({ event, onClose }: TeamDialogProps) {
  const [homeTeamEvents, setHomeTeamEvents] = useState<SofascoreEvent[]>([]);
  const [awayTeamEvents, setAwayTeamEvents] = useState<SofascoreEvent[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadTeamEvents();
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
    return date.toLocaleDateString('en-US', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    });
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
        )}
      </div>
    </div>
  );
}

export default TeamDialog;
