import * as React from 'react';

import { IMetricResultsColumn } from './metricResultsColumns';

interface IMetricResultsListHeaderProps {
  columns: IMetricResultsColumn[];
}

export default ({ columns }: IMetricResultsListHeaderProps) => (
  <ul className="list-unstyled list-inline horizontal">
    {columns.map(c => (
      <li className={`flex-${c.width}`} key={c.name}>{c.name}</li>
    ))}
  </ul>
);
