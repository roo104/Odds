import {useState} from 'react';
import FootballMatchesView from './components/FootballMatchesView';
import HandballMatchesView from './components/HandballMatchesView';
import './App.css';

function App() {
  const [sport, setSport] = useState<'football' | 'handball'>('football');

  return (
    <div className="app">
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
      {sport === 'football' ? <FootballMatchesView /> : <HandballMatchesView />}
    </div>
  );
}

export default App;
