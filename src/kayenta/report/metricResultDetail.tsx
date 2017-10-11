import * as React from 'react';

import { ICanaryAnalysisResult } from '../domain/ICanaryJudgeResult';

interface IMetricResultDetailProps {
  result: ICanaryAnalysisResult;
}

export default ({ result }: IMetricResultDetailProps) => {
  if (result) {
    return (
      <pre>{JSON.stringify(result, null, 2)}</pre>
    );
  } else {
    return (
      <h3 className="heading-3">Select a metric result.</h3>
    );
  }
};
