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

  return (
    <div className="bets-view">
      <div className="bets-header">
        <div className="bets-title">
          <h2>Tagged Bets</h2>
          <span className="bets-count">
            {pageData ? `${pageData.totalElements} total` : '—'}
          </span>
        </div>
        <div className="bets-stats">
          <span className="stat-label">Win Ratio:</span>
          <span className="stat-value">{formatWinRatio()}</span>
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
