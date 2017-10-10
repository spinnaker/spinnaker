import { ICanaryAnalysisResult } from '../domain/ICanaryJudgeResult';

export interface IResultsListColumn {
  name: string;
  pickValue: (result: ICanaryAnalysisResult) => any;
  width: number;
}

const name: IResultsListColumn = {
  name: 'name',
  pickValue: (r: ICanaryAnalysisResult): string => r.name,
  width: 1,
};

const result: IResultsListColumn = {
  name: 'result',
  pickValue: (r: ICanaryAnalysisResult): string => r.classification,
  width: 1,
};

export const resultsListColumns: IResultsListColumn[] = [
  name,
  result,
];

