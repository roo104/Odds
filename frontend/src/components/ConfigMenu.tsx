import {useState} from 'react';
import './ConfigMenu.css';

interface ConfigMenuProps {
  onStart: (config: ConfigSettings) => void;
  onStop: () => void;
  isRunning: boolean;
  currentDate?: Date;
}

export interface ConfigSettings {
  startDate: Date;
  endDate: Date;
  sport: 'football' | 'handball';
  intervalSeconds: number;
}

function ConfigMenu({ onStart, onStop, isRunning, currentDate }: ConfigMenuProps) {
  const [startDate, setStartDate] = useState(new Date().toISOString().split('T')[0]);
  const [endDate, setEndDate] = useState(new Date().toISOString().split('T')[0]);
  const [sport, setSport] = useState<'football' | 'handball'>('football');
  const [intervalSeconds, setIntervalSeconds] = useState(10);

  const handleStart = () => {
    const config: ConfigSettings = {
      startDate: new Date(startDate),
      endDate: new Date(endDate),
      sport,
      intervalSeconds
    };
    onStart(config);
  };

  return (
    <div className="config-menu">
      <h3>Auto Refresh Configuration</h3>
      <div className="config-form">
        <div className="config-row">
          <label>Start Date:</label>
          <input
            type="date"
            value={startDate}
            onChange={(e) => setStartDate(e.target.value)}
            disabled={isRunning}
          />
        </div>
        <div className="config-row">
          <label>End Date:</label>
          <input
            type="date"
            value={endDate}
            onChange={(e) => setEndDate(e.target.value)}
            disabled={isRunning}
          />
        </div>
        <div className="config-row">
          <label>Sport:</label>
          <select
            value={sport}
            onChange={(e) => setSport(e.target.value as 'football' | 'handball')}
            disabled={isRunning}
          >
            <option value="football">Football</option>
            <option value="handball">Handball</option>
          </select>
        </div>
        <div className="config-row">
          <label>Interval (seconds):</label>
          <input
            type="number"
            min="1"
            value={intervalSeconds}
            onChange={(e) => setIntervalSeconds(parseInt(e.target.value))}
            disabled={isRunning}
          />
        </div>
        <div className="config-actions">
          {!isRunning && (
            <button onClick={handleStart} className="start-button">
              Start Auto Refresh
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

export default ConfigMenu;
