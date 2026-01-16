import {SofascoreEvent} from '../types';
import './MatchesTable.css';

interface MatchesTableProps {
  matches: SofascoreEvent[];
  onMatchClick: (event: SofascoreEvent) => void;
  onRefreshMatch: (eventId: number) => void;
  shouldHighlight: (event: SofascoreEvent) => boolean;
  parseOdds: (fractionalOdds?: string) => number;
  refreshingMatchId: number | null;
}

function MatchesTable({ matches, onMatchClick, onRefreshMatch, shouldHighlight, parseOdds, refreshingMatchId }: MatchesTableProps) {
  const formatDateTime = (timestamp: number) => {
    const date = new Date(timestamp * 1000);
    return date.toLocaleString('en-US', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  const formatOdds = (fractionalOdds?: string): string => {
    if (!fractionalOdds) return '-';
    const decimal = parseOdds(fractionalOdds);
    return decimal > 0 ? decimal.toFixed(2) : '-';
  };

  const handleRefreshClick = (e: React.MouseEvent, eventId: number) => {
    e.stopPropagation();
    onRefreshMatch(eventId);
  };

  return (
    <div className="matches-table-container">
      <table className="matches-table">
        <thead>
          <tr>
            <th>Date & Time</th>
            <th>Home Team</th>
            <th>Away Team</th>
            <th>Score</th>
            <th>Home Odds</th>
            <th>Draw Odds</th>
            <th>Away Odds</th>
            <th>Home Vote %</th>
            <th>Draw Vote %</th>
            <th>Away Vote %</th>
            <th>Status</th>
            <th>Tournament</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {matches.map((match) => (
            <tr
              key={match.id}
              onClick={() => onMatchClick(match)}
              className={shouldHighlight(match) ? 'highlight-row' : ''}
            >
              <td>{formatDateTime(match.startTimestamp)}</td>
              <td>{match.homeTeam.name}</td>
              <td>{match.awayTeam.name}</td>
              <td>
                {match.homeScore?.current ?? '-'} - {match.awayScore?.current ?? '-'}
              </td>
              <td>{formatOdds(match.odds?.home)}</td>
              <td>{formatOdds(match.odds?.draw)}</td>
              <td>{formatOdds(match.odds?.away)}</td>
              <td>{match.voting?.home ? `${match.voting.home}%` : '-'}</td>
              <td>{match.voting?.draw ? `${match.voting.draw}%` : '-'}</td>
              <td>{match.voting?.away ? `${match.voting.away}%` : '-'}</td>
              <td>{match.status.description}</td>
              <td>{match.tournament.name}</td>
              <td>
                <button
                  className="refresh-match-button"
                  onClick={(e) => handleRefreshClick(e, match.id)}
                  disabled={refreshingMatchId === match.id}
                  title="Refresh this match"
                >
                  {refreshingMatchId === match.id ? '↻' : '⟳'}
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default MatchesTable;
