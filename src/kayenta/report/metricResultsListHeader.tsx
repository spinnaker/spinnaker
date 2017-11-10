import * as React from 'react';

import { IMetricResultsColumn } from './metricResultsColumns';

export interface IMetricResultsListHeaderProps {
  columns: IMetricResultsColumn[];
}

export default ({ columns }: IMetricResultsListHeaderProps) => (
  <section className="horizontal">
    {columns.map(c => (
      <div className={`flex-${c.width}`} key={c.name}>
        <span
          className="uppercase color-text-primary label heading-5"
          style={{paddingLeft: '0'}}
        >{c.name}
        </span>
      </div>
    ))}
  </section>
);
