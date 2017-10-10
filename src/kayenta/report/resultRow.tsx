import * as React from 'react';

import { ICanaryAnalysisResult } from '../domain/ICanaryJudgeResult';

interface IResultRowProps {
  result: ICanaryAnalysisResult;
}

export default ({ result }: IResultRowProps) => {
  return <span>{result.name}</span>;
}
