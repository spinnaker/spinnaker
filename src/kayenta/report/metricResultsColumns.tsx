import * as React from 'react';
import { ICanaryAnalysisResult } from '../domain/ICanaryJudgeResult';
import MetricResultClassification from './metricResultClassification';
import { ITableColumn } from 'kayenta/layout/table';

export const metricResultsColumns: ITableColumn<ICanaryAnalysisResult>[] = [
  {
    label: 'name',
    getContent: r => (<section>{r.name}</section>),
    width: 1,
  },
  {
    label: 'result',
    getContent: r => (<MetricResultClassification classification={r.classification}/>),
    width: 1,
  }
];

