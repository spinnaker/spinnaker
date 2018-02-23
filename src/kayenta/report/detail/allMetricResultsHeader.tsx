import * as React from 'react';
import * as classNames from 'classnames';
import { ICanaryJudgeScore, ICanaryClassifierThresholdsConfig } from 'kayenta/domain';
import { mapGroupToColor } from './colors';

import HeaderArrow from './headerArrow';

export interface IAllMetricResultsHeader {
  onClick: () => void;
  className: string;
  score: ICanaryJudgeScore;
  scoreThresholds: ICanaryClassifierThresholdsConfig;
}

/*
* Clickable header for all metric results.
*/
export default ({ className, onClick, score, scoreThresholds }: IAllMetricResultsHeader) => (
  <section className={className}>
    <div
      onClick={onClick}
      style={{
        backgroundColor: mapGroupToColor(score, scoreThresholds)
      }}
      className={classNames('clickable', 'text-center', 'all-metric-results-header')}
    >
      <h3 className="heading-3 label">ALL</h3>
      <HeaderArrow className="outer" arrowColor="var(--color-titanium)"/>
      <HeaderArrow className="inner" arrowColor={mapGroupToColor(score, scoreThresholds)}/>
    </div>
  </section>
);
