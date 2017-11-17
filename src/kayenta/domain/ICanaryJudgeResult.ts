import { ScoreClassificationLabel } from './ScoreClassificationLabel';
import { MetricClassificationLabel } from './MetricClassificationLabel';

export interface ICanaryJudgeResult {
  judgeName: string;
  results: ICanaryAnalysisResult[];
  groupScores: ICanaryJudgeGroupScore[];
  score: ICanaryJudgeScore;
}

export interface ICanaryAnalysisResult {
  name: string;
  id: string;
  tags: {[key: string]: string};
  classification: MetricClassificationLabel;
  classificationReason: string;
  groups: string[];
  // TODO: is every judge going to implement the stats interface for
  // experiment and control metadata (which would make the reporting UI much easier),
  // or just the Netflix judge?
  experimentMetadata: {
    [key: string]: any;
    stats: ICanaryAnalysisResultsStats;
  };
  controlMetadata: {
    [key: string]: any;
    stats: ICanaryAnalysisResultsStats;
  };
  resultMetadata: {[key: string]: any};
}

export interface ICanaryAnalysisResultsStats {
  count: number;
  mean: number;
  min: number;
  max: number;
  median: number;
}

export interface ICanaryJudgeGroupScore {
  name: string;
  score: number;
  classification: string;
  classificationReason: string;
}

export interface ICanaryJudgeScore {
  score: number;
  classification: ScoreClassificationLabel;
  classificationReason: string;
}
