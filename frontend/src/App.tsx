import {useEffect, useRef, useState} from 'react';
import FootballMatchesView from './components/FootballMatchesView';
import HandballMatchesView from './components/HandballMatchesView';
import BetsView from './components/BetsView';
import StandingsView from './components/StandingsView';
import StatisticsView from './components/StatisticsView';
import SidePanel from './components/SidePanel';
import ConfigMenu, {ConfigSettings} from './components/ConfigMenu';
import {footballApi, handballApi} from './services/api';
import './App.css';

function App() {
  const [sport, setSport] = useState<'football' | 'handball'>('football');
  const [activeView, setActiveView] = useState<'matches' | 'bets' | 'standings' | 'statistics' | 'config'>('matches');
  const [sharedDate, setSharedDate] = useState(new Date());
  const [isAutoRefreshing, setIsAutoRefreshing] = useState(false);
  const [currentConfig, setCurrentConfig] = useState<ConfigSettings | null>(null);
  const intervalRef = useRef<NodeJS.Timeout | null>(null);
  const currentDateRef = useRef<Date>(new Date());

  useEffect(() => {
    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
      }
    };
  }, []);

  const handleStartAutoRefresh = async (config: ConfigSettings) => {
    setCurrentConfig(config);
    setIsAutoRefreshing(true);

    // Set initial date to start date
    currentDateRef.current = new Date(config.startDate);
    setSharedDate(new Date(config.startDate));

    // Make initial API call
    await makeRefreshCall(config.startDate, config.sport, config.majorLeaguesOnly);

    // Start interval
    intervalRef.current = setInterval(async () => {
      const nextDate = new Date(currentDateRef.current);
      nextDate.setDate(nextDate.getDate() + 1);

      // Check if we've exceeded end date
      if (nextDate > config.endDate) {
        // Stop auto-refresh
        console.log('Reached end date, stopping auto-refresh');
        handleStopAutoRefresh();
        return;
      }

      currentDateRef.current = nextDate;
      setSharedDate(new Date(nextDate));

      // Make API call for this date
      await makeRefreshCall(nextDate, config.sport, config.majorLeaguesOnly);
    }, config.intervalSeconds * 1000);
  };

  const makeRefreshCall = async (date: Date, sport: 'football' | 'handball', majorLeaguesOnly: boolean = true) => {
    try {
      const dateStr = date.toISOString().split('T')[0];
      console.log(`Refreshing ${sport} matches for ${dateStr}`);
      const includeAllLeagues = !majorLeaguesOnly;

      if (sport === 'football') {
        await footballApi.refreshMatchesByDate(dateStr, includeAllLeagues);
      } else {
        await handballApi.refreshMatchesByDate(dateStr, includeAllLeagues);
      }

      console.log(`Successfully refreshed ${sport} matches for ${dateStr}`);
    } catch (error) {
      console.error(`Failed to refresh ${sport} matches for ${date.toISOString().split('T')[0]}:`, error);
    }
  };

  const handleStopAutoRefresh = () => {
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }
    setIsAutoRefreshing(false);
    setCurrentConfig(null);
  };

  return (
    <div className="app">
      <SidePanel activeView={activeView} onViewChange={(view) => setActiveView(view as any)} />
      <div className="main-content">
        {isAutoRefreshing && (
          <div className="auto-refresh-status">
            <div className="status-info">
              Currently refreshing: {sharedDate.toLocaleDateString('en-US', {
                year: 'numeric',
                month: 'long',
                day: 'numeric'
              })} ({currentConfig?.sport})
            </div>
            <button onClick={handleStopAutoRefresh} className="stop-auto-refresh-button">
              Stop Auto Refresh
            </button>
          </div>
        )}
        {activeView === 'matches' && (
          <>
            <div className="sport-switcher">
              <button
                type="button"
                className={`sport-button ${sport === 'football' ? 'active' : ''}`}
                onClick={() => setSport('football')}
              >
                Football
              </button>
              <button
                type="button"
                className={`sport-button ${sport === 'handball' ? 'active' : ''}`}
                onClick={() => setSport('handball')}
              >
                Handball
              </button>
            </div>
            {sport === 'football' ? (
              <FootballMatchesView
                currentDate={sharedDate}
                onDateChange={setSharedDate}
              />
            ) : (
              <HandballMatchesView
                currentDate={sharedDate}
                onDateChange={setSharedDate}
              />
            )}
          </>
        )}

        {activeView === 'standings' && <StandingsView />}

        {activeView === 'bets' && <BetsView />}

        {activeView === 'statistics' && <StatisticsView />}

        {activeView === 'config' && (
          <ConfigMenu
            onStart={handleStartAutoRefresh}
            onStop={handleStopAutoRefresh}
            isRunning={isAutoRefreshing}
            currentDate={sharedDate}
          />
        )}
      </div>
    </div>
  );
}

export default App;
