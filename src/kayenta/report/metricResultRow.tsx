import * as React from 'react';

import { ICanaryAnalysisResult } from '../domain/ICanaryJudgeResult';
import { IMetricResultsColumn } from './metricResultsColumns';

interface IMetricResultRowProps {
  result: ICanaryAnalysisResult;
  columns: IMetricResultsColumn[];
  onClick: (metric: string) => void;
}

export default ({ result, columns, onClick }: IMetricResultRowProps) => {
  return (
    <ul
      className="horizontal list-unstyled"
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
