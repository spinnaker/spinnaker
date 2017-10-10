import * as React from 'react';

import { ICanaryAnalysisResult } from '../domain/ICanaryJudgeResult';

interface IResultRowProps {
  result: ICanaryAnalysisResult;
  onClick: (event: any) => void;
}

export default ({ result, onClick }: IResultRowProps) => {
  return (
    <section
      data-metric={result.name}
      onClick={onClick}
    >{result.name}
    </section>
  );
}
