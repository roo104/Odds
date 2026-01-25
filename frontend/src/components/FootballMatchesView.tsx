import {useEffect, useState} from 'react';
import {footballApi} from '../services/api';
import {SofascoreEvent} from '../types';
import MatchesTable from './MatchesTable';
import DateNavigation from './DateNavigation';
import FilterControls from './FilterControls';
import TeamDialog from './TeamDialog';
import Toast from './Toast';
import './FootballMatchesView.css';

interface ToastMessage {
  id: number;
  message: string;
  type: 'success' | 'error' | 'info';
}

function FootballMatchesView() {
  const [allMatches, setAllMatches] = useState<SofascoreEvent[]>([]);
  const [currentDate, setCurrentDate] = useState(new Date());
  const [loading, setLoading] = useState(false);
  const [filterNotStarted, setFilterNotStarted] = useState(false);
  const [filterMatchCriteria, setFilterMatchCriteria] = useState(false);
  const [minOdds, setMinOdds] = useState(3.0);
  const [minVotePercent, setMinVotePercent] = useState(70);
  const [selectedEvent, setSelectedEvent] = useState<SofascoreEvent | null>(null);
  const [refreshingMatchId, setRefreshingMatchId] = useState<number | null>(null);
  const [toasts, setToasts] = useState<ToastMessage[]>([]);

  useEffect(() => {
    loadMatches(currentDate);
  }, [currentDate]);

  const showToast = (message: string, type: 'success' | 'error' | 'info') => {
    const id = Date.now();
    setToasts((prev) => [...prev, { id, message, type }]);
  };

  const removeToast = (id: number) => {
    setToasts((prev) => prev.filter((toast) => toast.id !== id));
  };

  const loadMatches = async (date: Date, forceRefresh: boolean = false) => {
    setLoading(true);
    try {
      const dateStr = date.toISOString().split('T')[0];
      const matches = forceRefresh
        ? await footballApi.refreshMatchesByDate(dateStr)
        : await footballApi.getMatchesByDate(dateStr);
      setAllMatches(matches);
    } catch (error) {
      console.error('Failed to load matches:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleRefresh = () => {
    loadMatches(currentDate, true);
  };

  const parseOdds = (fractionalOdds?: string): number => {
    if (!fractionalOdds) return 0;
    try {
      const parts = fractionalOdds.split('/');
      if (parts.length === 2) {
        const numerator = parseFloat(parts[0]);
        const denominator = parseFloat(parts[1]);
        const result = (numerator / denominator) + 1.0;
        return Math.round(result * 100) / 100; // Round to 2 decimal places
      }
    } catch (e) {
      return 0;
    }
    return 0;
  };

  const shouldHighlight = (event: SofascoreEvent): boolean => {
    const { voting, odds } = event;
    if (!voting || !odds) return false;

    const roundedMinOdds = Math.round(minOdds * 100) / 100;

    if ((voting.home || 0) >= minVotePercent && parseOdds(odds.home) >= roundedMinOdds) return true;
    if ((voting.draw || 0) >= minVotePercent && parseOdds(odds.draw) >= roundedMinOdds) return true;
    if ((voting.away || 0) >= minVotePercent && parseOdds(odds.away) >= roundedMinOdds) return true;

    return false;
  };

  const getFilteredMatches = () => {
    let filtered = allMatches;

    if (filterNotStarted) {
      filtered = filtered.filter(m => m.status.description === 'Not started');
    }

    if (filterMatchCriteria) {
      filtered = filtered.filter(shouldHighlight);
    }

    return filtered;
  };

  const groupedMatches = getFilteredMatches().reduce((acc, match) => {
    const category = match.tournament.category.name;
    if (!acc[category]) acc[category] = [];
    acc[category].push(match);
    return acc;
  }, {} as Record<string, SofascoreEvent[]>);

  const handlePreviousDay = () => {
    const newDate = new Date(currentDate);
    newDate.setDate(newDate.getDate() - 1);
    setCurrentDate(newDate);
  };

  const handleNextDay = () => {
    const newDate = new Date(currentDate);
    newDate.setDate(newDate.getDate() + 1);
    setCurrentDate(newDate);
  };

  const handleToday = () => {
    setCurrentDate(new Date());
  };

  const handleMatchClick = (event: SofascoreEvent) => {
    setSelectedEvent(event);
  };

  const handleRefreshMatch = async (eventId: number) => {
    setRefreshingMatchId(eventId);
    try {
      const dateStr = currentDate.toISOString().split('T')[0];
      const updatedMatch = await footballApi.refreshSingleMatch(eventId, dateStr);

      // Update the match in the allMatches array
      setAllMatches((prevMatches) =>
        prevMatches.map((match) =>
          match.id === eventId ? updatedMatch : match
        )
      );

      showToast('Match refreshed successfully', 'success');
    } catch (error: any) {
      console.error('Failed to refresh match:', error);
      const errorMessage = error.message || 'Failed to refresh match';
      showToast(errorMessage, 'error');
    } finally {
      setRefreshingMatchId(null);
    }
  };

  return (
    <div className="football-matches-view">
      <DateNavigation
        currentDate={currentDate}
        onDateChange={setCurrentDate}
        onPreviousDay={handlePreviousDay}
        onNextDay={handleNextDay}
        onToday={handleToday}
        onRefresh={handleRefresh}
        isRefreshing={loading}
      />

      <FilterControls
        filterNotStarted={filterNotStarted}
        setFilterNotStarted={setFilterNotStarted}
        filterMatchCriteria={filterMatchCriteria}
        setFilterMatchCriteria={setFilterMatchCriteria}
        minOdds={minOdds}
        setMinOdds={setMinOdds}
        minVotePercent={minVotePercent}
        setMinVotePercent={setMinVotePercent}
      />

      {loading ? (
        <div className="loading">Loading matches...</div>
      ) : (
        <div className="matches-content">
          {Object.entries(groupedMatches).map(([category, matches]) => (
            <details key={category} className="category-section" open>
              <summary>
                <h3>{category}</h3>
              </summary>
              <MatchesTable
                matches={matches}
                onMatchClick={handleMatchClick}
                onRefreshMatch={handleRefreshMatch}
                shouldHighlight={shouldHighlight}
                parseOdds={parseOdds}
                refreshingMatchId={refreshingMatchId}
              />
            </details>
          ))}
        </div>
      )}

      {selectedEvent && (
        <TeamDialog
          event={selectedEvent}
          onClose={() => setSelectedEvent(null)}
        />
      )}

      {toasts.map((toast) => (
        <Toast
          key={toast.id}
          message={toast.message}
          type={toast.type}
          onClose={() => removeToast(toast.id)}
        />
      ))}
    </div>
  );
}

export default FootballMatchesView;
