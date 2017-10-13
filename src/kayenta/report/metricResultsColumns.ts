import { ICanaryAnalysisResult } from '../domain/ICanaryJudgeResult';

export interface IMetricResultsColumn {
  name: string;
  pickValue: (result: ICanaryAnalysisResult) => string;
  width: number;
}

const name: IMetricResultsColumn = {
  name: 'name',
  pickValue: r => r.name,
  width: 1,
};

const result: IMetricResultsColumn = {
  name: 'result',
  pickValue: r => r.classification,
  width: 1,
};

export const metricResultsColumns: IMetricResultsColumn[] = [
  name,
  result,
];

