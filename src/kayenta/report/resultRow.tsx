import * as React from 'react';

import { ICanaryAnalysisResult } from '../domain/ICanaryJudgeResult';
import { IResultsListColumn } from './resultsListColumns';

interface IResultRowProps {
  result: ICanaryAnalysisResult;
  columns: IResultsListColumn[];
  onClick: (metric: string) => void;
}

export default ({ result, columns, onClick }: IResultRowProps) => {
  return (
    <ul
      className="list-unstyled list-inline horizontal"
      data-metric={result.name}
      onClick={() => onClick(result.name)}
    >
      {columns.map(c => (
        <li key={c.name} className={`flex-${c.width}`}>
          {c.pickValue(result)}
        </li>
      ))}
    </ul>
  );
}
