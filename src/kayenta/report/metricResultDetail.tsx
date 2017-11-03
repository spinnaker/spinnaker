import * as React from 'react';

import { ICanaryAnalysisResult } from '../domain/ICanaryJudgeResult';
import MetricSetPairLoadStates from './metricSetPairLoadStates';

export interface IMetricResultDetailProps {
  result: ICanaryAnalysisResult;
}

/*
* Top-level component for the metric result detail - i.e., the right side of the
* screen that opens when you click on a metric result.
* */
export default ({ result }: IMetricResultDetailProps) => {
  if (result) {
    return (
      <MetricSetPairLoadStates/>
    );
  } else {
    return (
      <h3 className="heading-3 text-center">Select a metric result.</h3>
    );
  }
};
