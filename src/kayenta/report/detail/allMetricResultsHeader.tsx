import * as React from 'react';
import * as classNames from 'classnames';

import HeaderArrow from './headerArrow';

export interface IAllMetricResultsHeader {
  onClick: () => void;
  className: string;
}

/*
* Clickable header for all metric results.
*/
export default ({ className, onClick }: IAllMetricResultsHeader) => (
  <section className={className}>
    <div
      onClick={onClick}
      className={classNames('clickable', 'text-center', 'all-metric-results-header')}
    >
      <h3 className="heading-3 label">ALL</h3>
      <HeaderArrow className="outer" arrowColor="var(--color-titanium)"/>
      <HeaderArrow className="inner" arrowColor="var(--color-alabaster)"/>
    </div>
  </section>
);
