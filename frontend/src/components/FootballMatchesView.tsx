import {useEffect, useMemo, useState} from 'react';
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
  const [minOdds, setMinOdds] = useState(2.5);
  const [minVotePercent, setMinVotePercent] = useState(65);
  const [selectedCountries, setSelectedCountries] = useState<Set<string>>(new Set());
  const [selectedEvent, setSelectedEvent] = useState<SofascoreEvent | null>(null);
  const [refreshingMatchId, setRefreshingMatchId] = useState<number | null>(null);
  const [toasts, setToasts] = useState<ToastMessage[]>([]);
  const [includeAllLeagues, setIncludeAllLeagues] = useState(false);
  const [filterTopLeaguesOnly, setFilterTopLeaguesOnly] = useState(false);

  useEffect(() => {
    loadMatches(currentDate, false, includeAllLeagues);
  }, [currentDate, includeAllLeagues]);

  const showToast = (message: string, type: 'success' | 'error' | 'info') => {
    const id = Date.now();
    setToasts((prev) => [...prev, { id, message, type }]);
  };

  const removeToast = (id: number) => {
    setToasts((prev) => prev.filter((toast) => toast.id !== id));
  };

  const loadMatches = async (date: Date, forceRefresh: boolean = false, includeAll: boolean = includeAllLeagues) => {
    setLoading(true);
    try {
      const dateStr = date.toISOString().split('T')[0];
      const matches = forceRefresh
        ? await footballApi.refreshMatchesByDate(dateStr, includeAll)
        : await footballApi.getMatchesByDate(dateStr, includeAll);
      setAllMatches(matches);
      setSelectedCountries(new Set());
    } catch (error) {
      console.error('Failed to load matches:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleRefresh = () => {
    loadMatches(currentDate, true, includeAllLeagues);
  };

  const handleToggleAllLeagues = () => {
    setIncludeAllLeagues(!includeAllLeagues);
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

    if (filterTopLeaguesOnly) {
      filtered = filtered.filter(m => m.isTopLeague !== false);
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

    if (filterMatchCriteria) {
      matchesToConsider = matchesToConsider.filter(shouldHighlight);
    }

    if (filterTopLeaguesOnly) {
      matchesToConsider = matchesToConsider.filter(m => m.isTopLeague !== false);
    }

    return Array.from(
      new Set(
        matchesToConsider
          .map(m => m.tournament.category.country?.name)
          .filter((name): name is string => !!name)
      )
    ).sort();
  }, [allMatches, filterNotStarted, filterMatchCriteria, filterTopLeaguesOnly, minOdds, minVotePercent]);

  // Clear selected countries when filter changes and they're no longer available
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

  const getCountryFlag = (countryName: string): string => {
    const countryFlags: Record<string, string> = {
      'England': '🏴󠁧󠁢󠁥󠁮󠁧󠁿',
      'Spain': '🇪🇸',
      'Germany': '🇩🇪',
      'Italy': '🇮🇹',
      'France': '🇫🇷',
      'Portugal': '🇵🇹',
      'Netherlands': '🇳🇱',
      'Belgium': '🇧🇪',
      'Turkey': '🇹🇷',
      'Scotland': '🏴󠁧󠁢󠁳󠁣󠁴󠁿',
      'Austria': '🇦🇹',
      'Switzerland': '🇨🇭',
      'Denmark': '🇩🇰',
      'Sweden': '🇸🇪',
      'Norway': '🇳🇴',
      'Poland': '🇵🇱',
      'Czech Republic': '🇨🇿',
      'Czechia': '🇨🇿',
      'Greece': '🇬🇷',
      'Croatia': '🇭🇷',
      'Serbia': '🇷🇸',
      'Ukraine': '🇺🇦',
      'Russia': '🇷🇺',
      'Brazil': '🇧🇷',
      'Argentina': '🇦🇷',
      'USA': '🇺🇸',
      'Mexico': '🇲🇽',
      'Japan': '🇯🇵',
      'South Korea': '🇰🇷',
      'Korea Republic': '🇰🇷',
      'Australia': '🇦🇺',
      'Saudi Arabia': '🇸🇦',
      'United Arab Emirates': '🇦🇪',
      'China': '🇨🇳',
      'India': '🇮🇳',
      'Egypt': '🇪🇬',
      'South Africa': '🇿🇦',
      'Morocco': '🇲🇦',
      'Algeria': '🇩🇿',
      'Tunisia': '🇹🇳',
      'Israel': '🇮🇱',
      'Canada': '🇨🇦',
      'Chile': '🇨🇱',
      'Colombia': '🇨🇴',
      'Uruguay': '🇺🇾',
      'Paraguay': '🇵🇾',
      'Ecuador': '🇪🇨',
      'Peru': '🇵🇪',
      'Venezuela': '🇻🇪',
      'Bolivia': '🇧🇴',
      'Romania': '🇷🇴',
      'Bulgaria': '🇧🇬',
      'Hungary': '🇭🇺',
      'Slovakia': '🇸🇰',
      'Slovenia': '🇸🇮',
      'Republic of Ireland': '🇮🇪',
      'Ireland': '🇮🇪',
      'Northern Ireland': '🇬🇧',
      'Wales': '🏴󠁧󠁢󠁷󠁬󠁳󠁿',
      'Finland': '🇫🇮',
      'Iceland': '🇮🇸',
      'Luxembourg': '🇱🇺',
      'Cyprus': '🇨🇾',
      'Malta': '🇲🇹',
      'Albania': '🇦🇱',
      'Bosnia and Herzegovina': '🇧🇦',
      'Montenegro': '🇲🇪',
      'North Macedonia': '🇲🇰',
      'Kosovo': '🇽🇰',
      'Lithuania': '🇱🇹',
      'Latvia': '🇱🇻',
      'Estonia': '🇪🇪',
      'Belarus': '🇧🇾',
      'Moldova': '🇲🇩',
      'Georgia': '🇬🇪',
      'Armenia': '🇦🇲',
      'Azerbaijan': '🇦🇿',
      'Kazakhstan': '🇰🇿',
      'Uzbekistan': '🇺🇿',
      'Qatar': '🇶🇦',
      'Kuwait': '🇰🇼',
      'Bahrain': '🇧🇭',
      'Oman': '🇴🇲',
      'Iraq': '🇮🇶',
      'Iran': '🇮🇷',
      'Lebanon': '🇱🇧',
      'Jordan': '🇯🇴',
      'Palestine': '🇵🇸',
      'Syria': '🇸🇾',
      'Vietnam': '🇻🇳',
      'Thailand': '🇹🇭',
      'Malaysia': '🇲🇾',
      'Singapore': '🇸🇬',
      'Indonesia': '🇮🇩',
      'Philippines': '🇵🇭',
      'New Zealand': '🇳🇿',
      'World': '🌍',
      'Europe': '🇪🇺',
    };
    return countryFlags[countryName] || '🌍';
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

      // If this match is currently selected in the dialog, update it too
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
    <div className="football-matches-view">
      <DateNavigation
        currentDate={currentDate}
        onDateChange={setCurrentDate}
        onPreviousDay={handlePreviousDay}
        onNextDay={handleNextDay}
        onToday={handleToday}
        onRefresh={handleRefresh}
        isRefreshing={loading}
        includeAllLeagues={includeAllLeagues}
        onToggleAllLeagues={handleToggleAllLeagues}
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
        filterTopLeaguesOnly={filterTopLeaguesOnly}
        setFilterTopLeaguesOnly={setFilterTopLeaguesOnly}
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
          api={footballApi}
          sport="FOOTBALL"
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
