import * as React from 'react';

import { ICanaryAnalysisResult } from '../domain/ICanaryJudgeResult';
import { IMetricResultsColumn } from './metricResultsColumns';

export interface IMetricResultRowProps {
  result: ICanaryAnalysisResult;
  columns: IMetricResultsColumn[];
}

export default ({ result, columns }: IMetricResultRowProps) => {
  return (
    <section className="horizontal">
      {columns.map(c => (
        <div key={c.name} className={`flex-${c.width}`}>
          {c.pickValue(result)}
        </div>
      ))}
    </section>
  );
}
