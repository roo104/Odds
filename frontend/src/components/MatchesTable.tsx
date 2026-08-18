import {SofascoreEvent} from '../types';
import './MatchesTable.css';
import * as React from "react";

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
    const day = String(date.getDate()).padStart(2, '0');
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const year = date.getFullYear();
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    return `${day}/${month}/${year} ${hours}:${minutes}`;
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

  const handleRowClick = (_e: React.MouseEvent, match: SofascoreEvent) => {
    // Don't trigger click if user is selecting text
    const selection = window.getSelection();
    if (selection && selection.toString().length > 0) {
      return;
    }
    onMatchClick(match);
  };

  const getHighestVote = (match: SofascoreEvent): 'home' | 'draw' | 'away' | null => {
    if (!match.voting) return null;
    const homeVote = match.voting.home ?? 0;
    const drawVote = match.voting.draw ?? 0;
    const awayVote = match.voting.away ?? 0;

    if (homeVote === 0 && drawVote === 0 && awayVote === 0) return null;

    const maxVote = Math.max(homeVote, drawVote, awayVote);
    if (homeVote === maxVote) return 'home';
    if (drawVote === maxVote) return 'draw';
    return 'away';
  };

  const hasOdds = (match: SofascoreEvent): boolean => {
    return !!match.odds;
  };

  const getMatchResult = (match: SofascoreEvent): 'home-win' | 'away-win' | 'draw' | null => {
    if (match.status.type !== 'finished') return null;
    const homeScore = match.homeScore?.current;
    const awayScore = match.awayScore?.current;
    if (homeScore === undefined || awayScore === undefined) return null;
    if (homeScore > awayScore) return 'home-win';
    if (awayScore > homeScore) return 'away-win';
    return 'draw';
  };

  const getTournamentFlag = (match: SofascoreEvent): string | null => {
    if (match.tournament.category.name === 'Europe') {
      return '🇪🇺';
    }
    return null;
  };

  return (
    <div className="matches-table-container">
      <table className="matches-table">
        <colgroup>
          <col className="col-datetime" />
          <col className="col-team" />
          <col className="col-team" />
          <col className="col-score" />
          <col className="col-odds" />
          <col className="col-odds" />
          <col className="col-odds" />
          <col className="col-vote" />
          <col className="col-vote" />
          <col className="col-vote" />
          <col className="col-status" />
          <col className="col-tournament" />
          <col className="col-updated" />
          <col className="col-actions" />
        </colgroup>
        <thead>
          <tr className="group-header-row">
            <th rowSpan={2}>Date &amp; Time</th>
            <th className="group-start" colSpan={2}>Teams</th>
            <th className="group-start" rowSpan={2}>Score</th>
            <th className="group-start" colSpan={3}>Odds</th>
            <th className="group-start" colSpan={3}>Vote %</th>
            <th className="group-start" rowSpan={2}>Status</th>
            <th className="group-start" rowSpan={2}>Tournament</th>
            <th className="group-start" rowSpan={2}>Last Updated</th>
            <th className="group-start" rowSpan={2}>Actions</th>
          </tr>
          <tr className="sub-header-row">
            <th className="group-start">Home</th>
            <th>Away</th>
            <th className="group-start">Home</th>
            <th>Draw</th>
            <th>Away</th>
            <th className="group-start">Home</th>
            <th>Draw</th>
            <th>Away</th>
          </tr>
        </thead>
        <tbody>
          {matches.map((match) => {
            const highestVote = getHighestVote(match);
            const matchResult = getMatchResult(match);

            return (
              <tr
                key={match.id}
                onClick={(e) => handleRowClick(e, match)}
                className={shouldHighlight(match) ? 'highlight-row' : ''}
              >
                <td>{formatDateTime(match.startTimestamp)}</td>
                <td className={`group-start ${matchResult === 'home-win' ? 'winner' : matchResult === 'away-win' ? 'loser' : matchResult === 'draw' ? 'draw' : ''}`}>
                  {match.homeTeam.name}
                </td>
                <td className={matchResult === 'away-win' ? 'winner' : matchResult === 'home-win' ? 'loser' : matchResult === 'draw' ? 'draw' : ''}>
                  {match.awayTeam.name}
                </td>
                <td className="group-start">
                  {match.homeScore?.current ?? '-'} - {match.awayScore?.current ?? '-'}
                </td>
                <td className={`group-start ${hasOdds(match) && highestVote === 'home' ? 'highest-value' : ''}`}>
                  {formatOdds(match.odds?.home)}
                </td>
                <td className={hasOdds(match) && highestVote === 'draw' ? 'highest-value' : ''}>
                  {formatOdds(match.odds?.draw)}
                </td>
                <td className={hasOdds(match) && highestVote === 'away' ? 'highest-value' : ''}>
                  {formatOdds(match.odds?.away)}
                </td>
                <td className={`group-start ${highestVote === 'home' ? 'highest-value' : ''}`}>
                  {match.voting?.home ? `${match.voting.home}%` : '-'}
                </td>
                <td className={highestVote === 'draw' ? 'highest-value' : ''}>
                  {match.voting?.draw ? `${match.voting.draw}%` : '-'}
                </td>
                <td className={highestVote === 'away' ? 'highest-value' : ''}>
                  {match.voting?.away ? `${match.voting.away}%` : '-'}
                </td>
                <td className="group-start">{match.status.description}</td>
                <td className="group-start">
                  {getTournamentFlag(match) && (
                    <span className="tournament-flag" aria-label="Europe">🇪🇺</span>
                  )}
                  <span className="tournament-name">{match.tournament.name}</span>
                </td>
                <td className="last-updated-cell group-start" title={match.lastUpdated ? formatDateTime(match.lastUpdated) : ''}>
                  {formatLastUpdated(match.lastUpdated)}
                </td>
                <td className="group-start">
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
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

export default MatchesTable;
