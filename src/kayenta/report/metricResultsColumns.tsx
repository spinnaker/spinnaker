import * as React from 'react';
import { ICanaryAnalysisResult } from '../domain/ICanaryJudgeResult';
import MetricResultClassification from './metricResultClassification';

export interface IMetricResultsColumn {
  name: string;
  pickValue: (result: ICanaryAnalysisResult) => JSX.Element;
  width: number;
}

const name: IMetricResultsColumn = {
  name: 'name',
  pickValue: r => (<section>{r.name}</section>),
  width: 1,
};

const result: IMetricResultsColumn = {
  name: 'result',
  pickValue: r => (<MetricResultClassification classification={r.classification}/>),
  width: 1,
};

export const metricResultsColumns: IMetricResultsColumn[] = [
  name,
  result,
];

