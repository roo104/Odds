import {useEffect, useState} from 'react';
import {betsApi, footballApi, handballApi} from '../services/api';
import {BetsPageResponse, MatchBet, SofascoreEvent} from '../types';
import TeamDialog from './TeamDialog';
import './BetsView.css';
import './MatchesTable.css';

const PAGE_SIZE = 10;

function BetsView() {
  const [pageData, setPageData] = useState<BetsPageResponse | null>(null);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [refreshingId, setRefreshingId] = useState<number | null>(null);
  const [selectedBet, setSelectedBet] = useState<MatchBet | null>(null);
  const [selectedEvent, setSelectedEvent] = useState<SofascoreEvent | null>(null);
  const [loadingEvent, setLoadingEvent] = useState(false);
  const [editingOddsId, setEditingOddsId] = useState<number | null>(null);
  const [oddsInputValue, setOddsInputValue] = useState<string>('');

  useEffect(() => {
    loadBets(page);
  }, [page]);

  const loadBets = async (pageNumber: number) => {
    setLoading(true);
    setError(null);
    try {
      const data = await betsApi.getBets(pageNumber, PAGE_SIZE);
      setPageData(data);
    } catch (err: any) {
      setError(err?.message || 'Failed to load bets');
    } finally {
      setLoading(false);
    }
  };

  const handleRefresh = async (betId: number) => {
    setRefreshingId(betId);
    setError(null);
    try {
      const updatedBet = await betsApi.refreshBetScore(betId);
      setPageData((prev) => {
        if (!prev) return prev;
        return {
          ...prev,
          content: prev.content.map((bet) => (bet.id === betId ? updatedBet : bet)),
        };
      });
    } catch (err: any) {
      setError(err?.message || 'Failed to refresh bet score');
    } finally {
      setRefreshingId(null);
    }
  };

  const formatDateTime = (timestampSeconds: number) => {
    const date = new Date(timestampSeconds * 1000);
    return date.toLocaleString();
  };

  const formatSelection = (bet: MatchBet) => {
    if (bet.selection === 'HOME') return `${bet.homeTeamName} (Home)`;
    if (bet.selection === 'AWAY') return `${bet.awayTeamName} (Away)`;
    return 'Draw';
  };

  const getFinalScoreLabel = (bet: MatchBet) => {
    if (bet.finalHomeScore === null || bet.finalAwayScore === null) return '-';
    return `${bet.finalHomeScore} - ${bet.finalAwayScore}`;
  };

  const isWinningBet = (bet: MatchBet) => {
    if (bet.finalHomeScore === null || bet.finalAwayScore === null) return false;
    if (bet.finalHomeScore === bet.finalAwayScore) return bet.selection === 'DRAW';
    if (bet.finalHomeScore > bet.finalAwayScore) return bet.selection === 'HOME';
    return bet.selection === 'AWAY';
  };

  const getBetResultClass = (bet: MatchBet) => {
    if (bet.finalHomeScore === null || bet.finalAwayScore === null) return '';
    return isWinningBet(bet) ? 'winning-bet' : 'losing-bet';
  };

  const totalPages = pageData?.totalPages ?? 0;
  const bets = pageData?.content ?? [];

  const formatWinRatio = () => {
    if (!pageData?.stats) return '—';
    const { winRatio, wonBets, finishedBets } = pageData.stats;
    if (winRatio === null || finishedBets === 0) return '— (no finished bets)';
    return `${(winRatio * 100).toFixed(1)}% (${wonBets}/${finishedBets})`;
  };

  const handleRowClick = async (bet: MatchBet) => {
    setSelectedBet(bet);
    setLoadingEvent(true);
    setError(null);
    try {
      const api = bet.sport === 'Football' ? footballApi : handballApi;
      const date = new Date(bet.startTimestamp * 1000).toISOString().split('T')[0];
      const event = await api.refreshSingleMatch(bet.eventId, date);
      setSelectedEvent(event);
    } catch (err: any) {
      setError(err?.message || 'Failed to load match details');
    } finally {
      setLoadingEvent(false);
    }
  };

  const handleCloseDialog = () => {
    setSelectedEvent(null);
    setSelectedBet(null);
  };

  const handleOddsClick = (bet: MatchBet, e: React.MouseEvent) => {
    e.stopPropagation();
    setEditingOddsId(bet.id);
    setOddsInputValue(bet.odds ? bet.odds.toString() : '');
  };

  const handleOddsBlur = async (betId: number) => {
    const newOdds = oddsInputValue.trim() ? parseFloat(oddsInputValue) : null;

    if (oddsInputValue.trim() && (isNaN(newOdds!) || newOdds! <= 0)) {
      setError('Invalid odds value');
      setEditingOddsId(null);
      return;
    }

    try {
      const updatedBet = await betsApi.updateOdds(betId, newOdds);
      setPageData((prev) => {
        if (!prev) return prev;
        return {
          ...prev,
          content: prev.content.map((bet) => (bet.id === betId ? updatedBet : bet)),
        };
      });
    } catch (err: any) {
      setError(err?.message || 'Failed to update odds');
    } finally {
      setEditingOddsId(null);
    }
  };

  const handleOddsKeyDown = (e: React.KeyboardEvent, betId: number) => {
    if (e.key === 'Enter') {
      handleOddsBlur(betId);
    } else if (e.key === 'Escape') {
      setEditingOddsId(null);
    }
  };

  return (
    <div className="bets-view">
      <div className="bets-header">
        <div className="bets-title">
          <h2>Tagged Bets</h2>
          <span className="bets-count">
            {pageData ? `${pageData.totalElements} total` : '—'}
          </span>
        </div>
        <div className="bets-stats-container">
          <div className="bets-stats">
            <span className="stat-label">Win Ratio:</span>
            <span className="stat-value">{formatWinRatio()}</span>
          </div>
          {pageData?.stats.avgWinningOdds && (
            <div className="bets-stats">
              <span className="stat-label">Avg Winning Odds:</span>
              <span className="stat-value stat-positive">{pageData.stats.avgWinningOdds.toFixed(2)}</span>
            </div>
          )}
          {pageData?.stats.avgLosingOdds && (
            <div className="bets-stats">
              <span className="stat-label">Avg Losing Odds:</span>
              <span className="stat-value stat-negative">{pageData.stats.avgLosingOdds.toFixed(2)}</span>
            </div>
          )}
          {pageData?.stats.expectedValue !== null && pageData?.stats.expectedValue !== undefined && (
            <div className="bets-stats">
              <span className="stat-label">Expected Value:</span>
              <span className={`stat-value ${pageData.stats.expectedValue >= 0 ? 'stat-positive' : 'stat-negative'}`}>
                {pageData.stats.expectedValue >= 0 ? '+' : ''}{(pageData.stats.expectedValue * 100).toFixed(1)}%
              </span>
            </div>
          )}
          {pageData?.stats.actualProfit !== null && pageData?.stats.actualProfit !== undefined && (
            <div className="bets-stats">
              <span className="stat-label">Profit (units):</span>
              <span className={`stat-value ${pageData.stats.actualProfit >= 0 ? 'stat-positive' : 'stat-negative'}`}>
                {pageData.stats.actualProfit >= 0 ? '+' : ''}{pageData.stats.actualProfit.toFixed(2)}
              </span>
            </div>
          )}
        </div>
      </div>

      {loading ? (
        <div className="loading">Loading bets...</div>
      ) : error ? (
        <div className="bets-error">{error}</div>
      ) : bets.length === 0 ? (
        <div className="bets-empty">No bets tagged yet.</div>
      ) : (
        <div className="matches-table-container">
          <table className="matches-table">
            <thead>
              <tr>
                <th>Kickoff</th>
                <th>Sport</th>
                <th>Match</th>
                <th>Pick</th>
                <th>Odds</th>
                <th>Final</th>
                <th>Refresh</th>
              </tr>
            </thead>
            <tbody>
              {bets.map((bet: MatchBet) => (
                <tr key={bet.id} className={getBetResultClass(bet)} onClick={() => handleRowClick(bet)}>
                  <td>{formatDateTime(bet.startTimestamp)}</td>
                  <td>{bet.sport}</td>
                  <td>{bet.homeTeamName} vs {bet.awayTeamName}</td>
                  <td>{formatSelection(bet)}</td>
                  <td onClick={(e) => handleOddsClick(bet, e)} className="odds-cell">
                    {editingOddsId === bet.id ? (
                      <input
                        type="number"
                        step="0.01"
                        min="1"
                        value={oddsInputValue}
                        onChange={(e) => setOddsInputValue(e.target.value)}
                        onBlur={() => handleOddsBlur(bet.id)}
                        onKeyDown={(e) => handleOddsKeyDown(e, bet.id)}
                        className="odds-edit-input"
                        autoFocus
                        onClick={(e) => e.stopPropagation()}
                      />
                    ) : (
                      <span className="odds-display">{bet.odds ? bet.odds.toFixed(2) : '-'}</span>
                    )}
                  </td>
                  <td>{getFinalScoreLabel(bet)}</td>
                  <td>
                    <button
                      type="button"
                      className="refresh-match-button"
                      onClick={(e) => {
                        e.stopPropagation();
                        handleRefresh(bet.id);
                      }}
                      disabled={refreshingId === bet.id}
                    >
                      {refreshingId === bet.id ? '↻' : '⟳'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div className="pagination">
        <button
          onClick={() => setPage((prev) => Math.max(0, prev - 1))}
          disabled={page === 0}
        >
          Previous
        </button>
        <span>
          Page {page + 1} of {Math.max(totalPages, 1)}
        </span>
        <button
          onClick={() => setPage((prev) => Math.min(Math.max(totalPages - 1, 0), prev + 1))}
          disabled={page >= totalPages - 1 || totalPages === 0}
        >
          Next
        </button>
      </div>

      {selectedEvent && selectedBet && (
        <TeamDialog
          event={selectedEvent}
          api={selectedBet.sport === 'Football' ? footballApi : handballApi}
          sport={selectedBet.sport}
          onClose={handleCloseDialog}
        />
      )}

      {loadingEvent && (
        <div className="dialog-overlay">
          <div className="loading">Loading match details...</div>
        </div>
      )}
    </div>
  );
}

export default BetsView;
