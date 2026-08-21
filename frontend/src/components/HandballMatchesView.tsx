import {useEffect, useMemo, useState} from 'react';
import {claudeApi, handballApi, StoredMatchPrediction} from '../services/api';
import {SofascoreEvent} from '../types';
import MatchesTable from './MatchesTable';
import DateNavigation from './DateNavigation';
import FilterControls from './FilterControls';
import TeamDialog from './TeamDialog';
import MatchPredictionDialog from './MatchPredictionDialog';
import Toast from './Toast';
import {getCountryFlag} from '../utils/countryFlags';
import './FootballMatchesView.css';

interface ToastMessage {
  id: number;
  message: string;
  type: 'success' | 'error' | 'info';
}

interface HandballMatchesViewProps {
  currentDate: Date;
  onDateChange: (date: Date) => void;
}

function HandballMatchesView({ currentDate, onDateChange }: HandballMatchesViewProps) {
  const [allMatches, setAllMatches] = useState<SofascoreEvent[]>([]);
  const [loading, setLoading] = useState(false);
  const [filterNotStarted, setFilterNotStarted] = useState(false);
  const [filterLive, setFilterLive] = useState(false);
  const [filterMatchCriteria, setFilterMatchCriteria] = useState(false);
  const [minOdds, setMinOdds] = useState(2.5);
  const [minVotePercent, setMinVotePercent] = useState(65);
  const [selectedCountries, setSelectedCountries] = useState<Set<string>>(new Set());
  const [selectedEvent, setSelectedEvent] = useState<SofascoreEvent | null>(null);
  const [predictionEvent, setPredictionEvent] = useState<SofascoreEvent | null>(null);
  const [refreshingMatchId, setRefreshingMatchId] = useState<number | null>(null);
  const [predictions, setPredictions] = useState<Record<number, StoredMatchPrediction>>({});
  const [toasts, setToasts] = useState<ToastMessage[]>([]);

  useEffect(() => {
    loadMatches(currentDate, false);
    loadPredictions(currentDate);
  }, [currentDate]);

  // Predictions are stored per match rather than per day, so they survive a reload and a match
  // keeps the call Claude made for it until someone asks again.
  const loadPredictions = async (date: Date) => {
    try {
      const dateStr = date.toISOString().split('T')[0];
      setPredictions(await claudeApi.getPredictionsByDate('handball', dateStr));
    } catch (error) {
      console.error('Failed to load stored predictions:', error);
      setPredictions({});
    }
  };

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
        ? await handballApi.refreshMatchesByDate(dateStr, true)
        : await handballApi.getMatchesByDate(dateStr, true);
      setAllMatches(matches);
      setSelectedCountries(new Set());
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
        return Math.round(result * 100) / 100;
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

    if (filterLive) {
      filtered = filtered.filter(m => m.status.type === 'inprogress');
    }

    if (filterMatchCriteria) {
      filtered = filtered.filter(shouldHighlight);
    }

    if (selectedCountries.size > 0) {
      filtered = filtered.filter(m =>
        selectedCountries.has(m.tournament.category.country?.name || '')
      );
    }

    return filtered;
  };

  const availableCountries = useMemo(() => {
    let matchesToConsider = allMatches;

    if (filterNotStarted) {
      matchesToConsider = matchesToConsider.filter(m => m.status.description === 'Not started');
    }

    if (filterLive) {
      matchesToConsider = matchesToConsider.filter(m => m.status.type === 'inprogress');
    }

    if (filterMatchCriteria) {
      matchesToConsider = matchesToConsider.filter(shouldHighlight);
    }

    return Array.from(
      new Set(
        matchesToConsider
          .map(m => m.tournament.category.country?.name)
          .filter((name): name is string => !!name)
      )
    ).sort();
  }, [allMatches, filterNotStarted, filterLive, filterMatchCriteria, minOdds, minVotePercent]);

  useEffect(() => {
    if (selectedCountries.size > 0) {
      const availableSet = new Set(availableCountries);
      const stillAvailable = Array.from(selectedCountries).filter(c => availableSet.has(c));
      if (stillAvailable.length !== selectedCountries.size) {
        setSelectedCountries(new Set(stillAvailable));
      }
    }
  }, [availableCountries]);

  const toggleCountry = (country: string) => {
    setSelectedCountries(prev => {
      const newSet = new Set(prev);
      if (newSet.has(country)) {
        newSet.delete(country);
      } else {
        newSet.add(country);
      }
      return newSet;
    });
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
    onDateChange(newDate);
  };

  const handleNextDay = () => {
    const newDate = new Date(currentDate);
    newDate.setDate(newDate.getDate() + 1);
    onDateChange(newDate);
  };

  const handleToday = () => {
    onDateChange(new Date());
  };

  const handleMatchClick = (event: SofascoreEvent) => {
    setSelectedEvent(event);
  };

  const handleRefreshMatch = async (eventId: number) => {
    setRefreshingMatchId(eventId);
    try {
      const dateStr = currentDate.toISOString().split('T')[0];
      const updatedMatch = await handballApi.refreshSingleMatch(eventId, dateStr);

      setAllMatches((prevMatches) =>
        prevMatches.map((match) =>
          match.id === eventId ? updatedMatch : match
        )
      );

      if (selectedEvent && selectedEvent.id === eventId) {
        setSelectedEvent(updatedMatch);
      }

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
    <div className="handball-matches-view">
      <DateNavigation
        currentDate={currentDate}
        onDateChange={onDateChange}
        onPreviousDay={handlePreviousDay}
        onNextDay={handleNextDay}
        onToday={handleToday}
        onRefresh={handleRefresh}
        isRefreshing={loading}
      />

      <FilterControls
        filterNotStarted={filterNotStarted}
        setFilterNotStarted={setFilterNotStarted}
        filterLive={filterLive}
        setFilterLive={setFilterLive}
        filterMatchCriteria={filterMatchCriteria}
        setFilterMatchCriteria={setFilterMatchCriteria}
        minOdds={minOdds}
        setMinOdds={setMinOdds}
        minVotePercent={minVotePercent}
        setMinVotePercent={setMinVotePercent}
      />

      {availableCountries.length > 0 && (
        <div className="country-filters">
          {availableCountries.map((country) => (
            <button
              key={country}
              className={`country-filter-button ${selectedCountries.has(country) ? 'active' : ''}`}
              onClick={() => toggleCountry(country)}
            >
              <span className="flag">{getCountryFlag(country)}</span>
              <span className="country-name">{country}</span>
            </button>
          ))}
        </div>
      )}

      <div className="matches-count">
        Showing {getFilteredMatches().length} matches
      </div>

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
                onPredictMatch={setPredictionEvent}
                shouldHighlight={shouldHighlight}
                parseOdds={parseOdds}
                refreshingMatchId={refreshingMatchId}
                predictions={predictions}
              />
            </details>
          ))}
        </div>
      )}

      {predictionEvent && (
        <MatchPredictionDialog
          event={predictionEvent}
          sport="handball"
          onPredicted={() => loadPredictions(currentDate)}
          onClose={() => setPredictionEvent(null)}
        />
      )}

      {selectedEvent && (
        <TeamDialog
          event={selectedEvent}
          api={handballApi}
          sport="Handball"
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

export default HandballMatchesView;
