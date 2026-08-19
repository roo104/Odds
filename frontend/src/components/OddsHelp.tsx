import './OddsHelp.css';

function OddsHelp() {
  return (
    <div className="odds-help">
      <h2 className="odds-help-title">How the odds numbers work</h2>
      <div className="odds-help-body">
        <p className="odds-help-intro">
          Hover the Home / Draw / Away odds on any row to see the probability breakdown behind the prices.
        </p>
        <dl className="odds-help-terms">
          <dt>Decimal odds</dt>
          <dd>Total return per 1 unit staked, stake included. 2.50 pays 2.50 back on a 1 unit bet.</dd>

          <dt>Implied</dt>
          <dd>The probability the price corresponds to: <code>1 / odds</code>. Odds of 2.50 imply 40%.</dd>

          <dt>Implied total</dt>
          <dd>
            The three implied probabilities added up. It always lands above 100% &mdash; that excess is
            what the bookmaker builds into the prices.
          </dd>

          <dt>Margin</dt>
          <dd>
            The bookmaker&apos;s cut of turnover, <code>1 &minus; 1 / total</code>. A tighter margin means
            prices closer to the bookmaker&apos;s real opinion.
          </dd>

          <dt>Fair (Shin)</dt>
          <dd>
            The implied probabilities with the margin removed, so they sum to exactly 100%. The margin is
            not stripped evenly: Shin&apos;s method loads more of it onto longshots, matching how
            bookmakers actually price. Invert a fair probability to get the break-even odds.
          </dd>

          <dt>Insider z</dt>
          <dd>
            Shin&apos;s single parameter &mdash; the share of money the bookmaker appears to be pricing
            against as informed. Solved from the prices, not measured. Roughly 1&ndash;3% on a competitive
            market; 8%+ means a defensively priced book, where fair differs most from implied.
          </dd>
        </dl>
        <p className="odds-help-example">
          <strong>Example</strong> 2.10 / 3.40 / 3.80 &rarr; implied 47.6% / 29.4% / 26.3% = 103.3%
          &rarr; margin 3.2%, z 1.7% &rarr; fair 46.4% / 28.3% / 25.3%.
        </p>
      </div>
    </div>
  );
}

export default OddsHelp;
