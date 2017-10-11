import * as React from 'react';

import { IMetricResultsColumn } from './metricResultsColumns';

interface IMetricResultsListHeaderProps {
  columns: IMetricResultsColumn[];
}

export default ({ columns }: IMetricResultsListHeaderProps) => (
  <section className="horizontal">
    {columns.map(c => (
      <div className={`flex-${c.width}`} key={c.name}>
        <span className="uppercase">{c.name}</span>
      </div>
    ))}
  </section>
);
