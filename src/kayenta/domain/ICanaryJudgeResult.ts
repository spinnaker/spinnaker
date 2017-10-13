export interface ICanaryJudgeResult {
  judgeName: string;
  results: {[metricName: string]: ICanaryAnalysisResult};
  groupScores: ICanaryJudgeGroupScore[];
  score: ICanaryJudgeGroupScore;
}

export interface ICanaryAnalysisResult {
  name: string;
  tags: {[key: string]: string};
  classification: string;
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
  classification: string;
  classificationReason: string;
}
