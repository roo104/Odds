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

  const formatLastUpdated = (timestamp?: number): string => {
    if (!timestamp) return '-';
    const date = new Date(timestamp * 1000);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);

    if (diffMins < 1) return 'Just now';
    if (diffMins < 60) return `${diffMins}m ago`;

    const diffHours = Math.floor(diffMins / 60);
    if (diffHours < 24) return `${diffHours}h ago`;

    const diffDays = Math.floor(diffHours / 24);
    return `${diffDays}d ago`;
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
            <th>Last Updated</th>
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
              <td className="last-updated-cell" title={match.lastUpdated ? new Date(match.lastUpdated * 1000).toLocaleString() : ''}>
                {formatLastUpdated(match.lastUpdated)}
              </td>
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
