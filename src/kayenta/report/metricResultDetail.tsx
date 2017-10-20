import * as React from 'react';

import { ICanaryAnalysisResult } from '../domain/ICanaryJudgeResult';
import MetricSetPairLoadStates from './metricSetPairLoadStates';

export interface IMetricResultDetailProps {
  result: ICanaryAnalysisResult;
}

export default ({ result }: IMetricResultDetailProps) => {
  if (result) {
    return (
      <MetricSetPairLoadStates/>
    );
  } else {
    return (
      <h3 className="heading-3">Select a metric result.</h3>
    );
  }
};
