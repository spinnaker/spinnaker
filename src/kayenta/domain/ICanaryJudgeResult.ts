import { ScoreClassificationLabel } from './ScoreClassificationLabel';
import { MetricClassificationLabel } from './MetricClassificationLabel';

export interface ICanaryJudgeResult {
  judgeName: string;
  results: {[metricName: string]: ICanaryAnalysisResult};
  groupScores: ICanaryJudgeGroupScore[];
  score: ICanaryJudgeScore;
}

export interface ICanaryAnalysisResult {
  name: string;
  metricSetPairId: string; // Not yet defined on the Kayenta model.
  tags: {[key: string]: string};
  classification: MetricClassificationLabel;
  classificationReason: string;
  groups: string[];
  experimentMetadata: {[key: string]: any};
  controlMetadata: {[key: string]: any};
  resultMetadata: {[key: string]: any};
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
