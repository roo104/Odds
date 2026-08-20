import {SofascoreEvent} from '../types';
import {PredictedOutcome, StoredMatchPrediction} from '../services/api';
import './MatchesTable.css';
import * as React from "react";
import {createPortal} from 'react-dom';

interface OddsOutcome {
  label: string;
  odds: number;
  implied: number;
  fair: number;
}

interface OddsBreakdown {
  outcomes: OddsOutcome[];
  impliedTotal: number;
  margin: number;
  insiderShare: number;
}

interface OddsTooltipState {
  breakdown: OddsBreakdown;
  x: number;
  y: number;
  placement: 'above' | 'below';
}

interface PredictionTooltipState {
  prediction: StoredMatchPrediction;
  x: number;
  y: number;
  placement: 'above' | 'below';
}

// Rough tooltip heights, only used to decide whether they still fit below the row
const ODDS_TOOLTIP_HEIGHT = 170;
const PREDICTION_TOOLTIP_HEIGHT = 260;

const OUTCOME_LABELS: Record<PredictedOutcome, string> = {
  HOME: 'Home win',
  DRAW: 'Draw',
  AWAY: 'Away win',
};

// Bisection steps for Shin's insider share; 60 halvings is far past double precision
const SHIN_ITERATIONS = 60;

interface MatchesTableProps {
  matches: SofascoreEvent[];
  onMatchClick: (event: SofascoreEvent) => void;
  onRefreshMatch: (eventId: number) => void;
  onPredictMatch: (event: SofascoreEvent) => void;
  shouldHighlight: (event: SofascoreEvent) => boolean;
  parseOdds: (fractionalOdds?: string) => number;
  refreshingMatchId: number | null;
  /** Latest stored Claude prediction per event id; matches without one simply have no entry. */
  predictions?: Record<number, StoredMatchPrediction>;
}

function MatchesTable({ matches, onMatchClick, onRefreshMatch, onPredictMatch, shouldHighlight, parseOdds, refreshingMatchId, predictions }: MatchesTableProps) {
  const [oddsTooltip, setOddsTooltip] = React.useState<OddsTooltipState | null>(null);
  const [predictionTooltip, setPredictionTooltip] = React.useState<PredictionTooltipState | null>(null);

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

  const formatPercent = (value: number): string => `${(value * 100).toFixed(1)}%`;

  // Shin (1992): the bookmaker prices against a share z of insiders, so the margin
  // sits more heavily on longshots than a proportional split would put it.
  const shinProbability = (implied: number, impliedTotal: number, z: number): number =>
    (Math.sqrt(z * z + 4 * (1 - z) * (implied * implied) / impliedTotal) - z) / (2 * (1 - z));

  // Sum of the Shin probabilities falls as z rises, so bisect for the z that makes them sum to 1
  const solveInsiderShare = (implied: number[], impliedTotal: number): number => {
    let low = 0;
    let high = 1;
    for (let i = 0; i < SHIN_ITERATIONS; i++) {
      const z = (low + high) / 2;
      const sum = implied.reduce((acc, probability) => acc + shinProbability(probability, impliedTotal, z), 0);
      if (sum > 1) {
        low = z;
      } else {
        high = z;
      }
    }
    return (low + high) / 2;
  };

  // Implied probability per outcome (1 / decimal odds). They sum to more than 100%;
  // that excess is the bookmaker margin, which the fair column strips out.
  const getOddsBreakdown = (match: SofascoreEvent): OddsBreakdown | null => {
    const outcomes = [
      { label: 'Home', odds: parseOdds(match.odds?.home) },
      { label: 'Draw', odds: parseOdds(match.odds?.draw) },
      { label: 'Away', odds: parseOdds(match.odds?.away) },
    ].filter((outcome) => outcome.odds > 0);

    if (outcomes.length === 0) return null;

    const implied = outcomes.map((outcome) => 1 / outcome.odds);
    const impliedTotal = implied.reduce((sum, probability) => sum + probability, 0);

    // Shin needs a real overround to work with; without one it degenerates to a plain rescale
    const usesShin = outcomes.length > 1 && impliedTotal > 1;
    const insiderShare = usesShin ? solveInsiderShare(implied, impliedTotal) : 0;

    return {
      outcomes: outcomes.map((outcome, index) => ({
        ...outcome,
        implied: implied[index],
        fair: usesShin
          ? shinProbability(implied[index], impliedTotal, insiderShare)
          : implied[index] / impliedTotal,
      })),
      impliedTotal,
      margin: 1 - 1 / impliedTotal,
      insiderShare,
    };
  };

  const handleOddsEnter = (e: React.MouseEvent<HTMLTableCellElement>, match: SofascoreEvent) => {
    const breakdown = getOddsBreakdown(match);
    if (!breakdown) return;

    // Anchor to the whole odds group so the tooltip doesn't jump between cells
    const cells = e.currentTarget.parentElement?.querySelectorAll('td.odds-cell');
    if (!cells || cells.length === 0) return;

    const first = cells[0].getBoundingClientRect();
    const last = cells[cells.length - 1].getBoundingClientRect();
    const placement = last.bottom + ODDS_TOOLTIP_HEIGHT > window.innerHeight ? 'above' : 'below';

    setOddsTooltip({
      breakdown,
      x: (first.left + last.right) / 2,
      y: placement === 'below' ? last.bottom + 8 : first.top - 8,
      placement,
    });
  };

  const handleOddsLeave = (e: React.MouseEvent<HTMLTableCellElement>) => {
    // Moving between the three odds cells keeps the same tooltip open
    const next = e.relatedTarget;
    if (next instanceof Element) {
      const nextCell = next.closest('td.odds-cell');
      if (nextCell && nextCell.parentElement === e.currentTarget.parentElement) return;
    }
    setOddsTooltip(null);
  };

  // A prediction is shown against the row it belongs to, anchored to the team names rather than
  // the cursor so it does not chase the pointer across the row.
  const handleRowEnter = (e: React.MouseEvent<HTMLTableRowElement>, match: SofascoreEvent) => {
    const prediction = predictions?.[match.id];
    if (!prediction) return;

    const cells = e.currentTarget.querySelectorAll('td.team-cell');
    if (cells.length === 0) return;

    const first = cells[0].getBoundingClientRect();
    const last = cells[cells.length - 1].getBoundingClientRect();
    const placement = last.bottom + PREDICTION_TOOLTIP_HEIGHT > window.innerHeight ? 'above' : 'below';

    setPredictionTooltip({
      prediction,
      x: (first.left + last.right) / 2,
      y: placement === 'below' ? last.bottom + 8 : first.top - 8,
      placement,
    });
  };

  /** Claude's percentage as a decimal price, so it reads on the same scale as the bookmaker's. */
  const fairOdds = (percent: number | null | undefined): number | null =>
    percent != null && percent > 0 ? 100 / percent : null;

  const predictionRows = (prediction: StoredMatchPrediction) =>
    ([
      { key: 'HOME' as PredictedOutcome, label: 'Home', percent: prediction.probabilities?.home, book: prediction.marketOdds?.home },
      { key: 'DRAW' as PredictedOutcome, label: 'Draw', percent: prediction.probabilities?.draw, book: prediction.marketOdds?.draw },
      { key: 'AWAY' as PredictedOutcome, label: 'Away', percent: prediction.probabilities?.away, book: prediction.marketOdds?.away },
    ]).map((row) => {
      const fair = fairOdds(row.percent);
      return {
        ...row,
        fair,
        // The bookmaker paying more than Claude's own price is what value looks like here.
        isValue: fair != null && row.book != null && row.book > fair,
      };
    });

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
                onMouseEnter={(e) => handleRowEnter(e, match)}
                onMouseLeave={() => setPredictionTooltip(null)}
                className={shouldHighlight(match) ? 'highlight-row' : ''}
              >
                <td>{formatDateTime(match.startTimestamp)}</td>
                <td className={`team-cell group-start ${matchResult === 'home-win' ? 'winner' : matchResult === 'away-win' ? 'loser' : matchResult === 'draw' ? 'draw' : ''}`}>
                  {match.homeTeam.name}
                </td>
                <td className={`team-cell ${matchResult === 'away-win' ? 'winner' : matchResult === 'home-win' ? 'loser' : matchResult === 'draw' ? 'draw' : ''}`}>
                  {match.awayTeam.name}
                </td>
                <td className="group-start">
                  {match.homeScore?.current ?? '-'} - {match.awayScore?.current ?? '-'}
                </td>
                <td
                  className={`odds-cell group-start ${hasOdds(match) && highestVote === 'home' ? 'highest-value' : ''}`}
                  onMouseEnter={(e) => handleOddsEnter(e, match)}
                  onMouseLeave={handleOddsLeave}
                >
                  {formatOdds(match.odds?.home)}
                </td>
                <td
                  className={`odds-cell ${hasOdds(match) && highestVote === 'draw' ? 'highest-value' : ''}`}
                  onMouseEnter={(e) => handleOddsEnter(e, match)}
                  onMouseLeave={handleOddsLeave}
                >
                  {formatOdds(match.odds?.draw)}
                </td>
                <td
                  className={`odds-cell ${hasOdds(match) && highestVote === 'away' ? 'highest-value' : ''}`}
                  onMouseEnter={(e) => handleOddsEnter(e, match)}
                  onMouseLeave={handleOddsLeave}
                >
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
                  <button
                    className={`predict-match-button ${predictions?.[match.id] ? 'has-prediction' : ''}`}
                    onClick={(e) => { e.stopPropagation(); onPredictMatch(match); }}
                    title={predictions?.[match.id]
                      ? 'Claude has called this one - hover the row to see it, click to ask again'
                      : match.status.type === 'inprogress'
                        ? 'Predict the outcome from live statistics and odds'
                        : 'Predict the outcome from odds and votes'}
                  >
                    ✨
                  </button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
      {oddsTooltip && createPortal(
        <div
          className={`odds-tooltip odds-tooltip-${oddsTooltip.placement}`}
          style={{ left: oddsTooltip.x, top: oddsTooltip.y }}
        >
          <div className="odds-tooltip-title">Implied probability &middot; fair = Shin</div>
          <table className="odds-tooltip-table">
            <thead>
              <tr>
                <th />
                <th>Odds</th>
                <th>Implied</th>
                <th>Fair</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {oddsTooltip.breakdown.outcomes.map((outcome) => (
                <tr key={outcome.label}>
                  <th>{outcome.label}</th>
                  <td>{outcome.odds.toFixed(2)}</td>
                  <td>{formatPercent(outcome.implied)}</td>
                  <td className="odds-tooltip-fair">{formatPercent(outcome.fair)}</td>
                  <td className="odds-tooltip-bar-cell">
                    <span className="odds-tooltip-bar" style={{ width: `${outcome.fair * 100}%` }} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="odds-tooltip-footer">
            <span>Implied total <strong>{formatPercent(oddsTooltip.breakdown.impliedTotal)}</strong></span>
            <span>Margin <strong>{formatPercent(oddsTooltip.breakdown.margin)}</strong></span>
            <span>Insider z <strong>{formatPercent(oddsTooltip.breakdown.insiderShare)}</strong></span>
          </div>
        </div>,
        document.body
      )}
      {predictionTooltip && !oddsTooltip && createPortal(
        <div
          className={`prediction-tooltip prediction-tooltip-${predictionTooltip.placement}`}
          style={{ left: predictionTooltip.x, top: predictionTooltip.y }}
        >
          <div className="prediction-tooltip-title">
            <span>Claude&rsquo;s call &middot; {formatLastUpdated(predictionTooltip.prediction.predictedAt)}</span>
            {predictionTooltip.prediction.wasLive && (
              <span className="prediction-tooltip-live">
                Live · {predictionTooltip.prediction.statusDescription}
                {predictionTooltip.prediction.homeScore != null &&
                  ` ${predictionTooltip.prediction.homeScore}-${predictionTooltip.prediction.awayScore ?? 0}`}
              </span>
            )}
          </div>

          {predictionTooltip.prediction.probabilities ? (
            <table className="odds-tooltip-table">
              <thead>
                <tr>
                  <th />
                  <th>Claude</th>
                  <th>AI odds</th>
                  <th>Book</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {predictionRows(predictionTooltip.prediction).map((row) => (
                  <tr
                    key={row.key}
                    className={predictionTooltip.prediction.predictedOutcome === row.key ? 'prediction-tooltip-top' : ''}
                  >
                    <th>{row.label}</th>
                    <td>{row.percent != null ? `${row.percent.toFixed(0)}%` : '-'}</td>
                    <td className="odds-tooltip-fair">{row.fair != null ? row.fair.toFixed(2) : '-'}</td>
                    <td className={row.isValue ? 'prediction-tooltip-value' : ''}>
                      {row.book != null ? row.book.toFixed(2) : '-'}
                    </td>
                    <td className="odds-tooltip-bar-cell">
                      <span className="odds-tooltip-bar" style={{ width: `${row.percent ?? 0}%` }} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <div className="prediction-tooltip-note">Claude answered without percentages this time.</div>
          )}

          {predictionTooltip.prediction.predictedOutcome && (
            <div className="prediction-tooltip-call">
              Call: <strong>{OUTCOME_LABELS[predictionTooltip.prediction.predictedOutcome]}</strong>
              <span className="prediction-tooltip-hint"> · green price pays more than Claude&rsquo;s own</span>
            </div>
          )}

          <p className="prediction-tooltip-text">{predictionTooltip.prediction.prediction}</p>

          <div className="odds-tooltip-footer">
            <span>{predictionTooltip.prediction.model ?? predictionTooltip.prediction.provider}</span>
            <span>Odds shown are the prices at the time of the call</span>
          </div>
        </div>,
        document.body
      )}
    </div>
  );
}

export default MatchesTable;
