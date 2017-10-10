import * as React from 'react';

import { ICanaryAnalysisResult } from '../domain/ICanaryJudgeResult';
import ResultRow from './resultRow';

interface IResultsListProps {
  results: ICanaryAnalysisResult[];
}

export default ({ results }: IResultsListProps) => (
  <ul className="list-unstyled">
    {results.map(r => (<li key={r.name}><ResultRow result={r}/></li>))}
  </ul>
);
