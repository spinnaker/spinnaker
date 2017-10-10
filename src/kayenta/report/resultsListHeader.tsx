import * as React from 'react';

import { IResultsListColumn } from './resultsListColumns';

interface IResultsListHeaderProps {
  columns: IResultsListColumn[];
}

export default ({ columns }: IResultsListHeaderProps) => (
  <ul className="list-unstyled list-inline horizontal">
    {columns.map(c => (
      <li className={`flex-${c.width}`} key={c.name}>{c.name}</li>
    ))}
  </ul>
);
