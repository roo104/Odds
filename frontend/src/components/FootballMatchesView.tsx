import {useEffect, useState} from 'react';
import {footballApi} from '../services/api';
import {SofascoreEvent} from '../types';
import MatchesTable from './MatchesTable';
import DateNavigation from './DateNavigation';
import FilterControls from './FilterControls';
import TeamDialog from './TeamDialog';
import './FootballMatchesView.css';

function FootballMatchesView() {
  const [allMatches, setAllMatches] = useState<SofascoreEvent[]>([]);
  const [currentDate, setCurrentDate] = useState(new Date());
  const [loading, setLoading] = useState(false);
  const [filterNotStarted, setFilterNotStarted] = useState(false);
  const [filterMatchCriteria, setFilterMatchCriteria] = useState(false);
  const [minOdds, setMinOdds] = useState(3.0);
  const [minVotePercent, setMinVotePercent] = useState(70);
  const [selectedEvent, setSelectedEvent] = useState<SofascoreEvent | null>(null);

  useEffect(() => {
    loadMatches(currentDate);
  }, [currentDate]);

  const loadMatches = async (date: Date) => {
    setLoading(true);
    try {
      const dateStr = date.toISOString().split('T')[0];
      const matches = await footballApi.getMatchesByDate(dateStr);
      setAllMatches(matches);
    } catch (error) {
      console.error('Failed to load matches:', error);
    } finally {
      setLoading(false);
    }
  };

  const parseOdds = (fractionalOdds?: string): number => {
    if (!fractionalOdds) return 0;
    try {
      const parts = fractionalOdds.split('/');
      if (parts.length === 2) {
        const numerator = parseFloat(parts[0]);
        const denominator = parseFloat(parts[1]);
        return (numerator / denominator) + 1.0;
      }
    } catch (e) {
      return 0;
    }
    return 0;
  };

  const shouldHighlight = (event: SofascoreEvent): boolean => {
    const { voting, odds } = event;
    if (!voting || !odds) return false;

    if ((voting.home || 0) > minVotePercent && parseOdds(odds.home) > minOdds) return true;
    if ((voting.draw || 0) > minVotePercent && parseOdds(odds.draw) > minOdds) return true;
    if ((voting.away || 0) > minVotePercent && parseOdds(odds.away) > minOdds) return true;

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

  return (
    <div className="football-matches-view">
      <DateNavigation
        currentDate={currentDate}
        onDateChange={setCurrentDate}
        onPreviousDay={handlePreviousDay}
        onNextDay={handleNextDay}
        onToday={handleToday}
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
                shouldHighlight={shouldHighlight}
                parseOdds={parseOdds}
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
    </div>
  );
}

export default FootballMatchesView;
