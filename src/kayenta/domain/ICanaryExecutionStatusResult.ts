import { ICanaryJudgeResult } from './ICanaryJudgeResult';
import { ICanaryClassifierThresholdsConfig, ICanaryConfig } from './ICanaryConfig';

export interface ICanaryExecutionStatusResult {
  id: string // Added by Deck on load.
  complete: boolean;
  metricSetPairListId: string;
  status: string;
  result: ICanaryResult;
  stageStatus: { [key: string]: string };
}

export interface ICanaryResult {
  judgeResult: ICanaryJudgeResult;
  config: ICanaryConfig;
  canaryExecutionRequest: ICanaryExecutionRequest;
}

export interface ICanaryExecutionRequest {
  thresholds: ICanaryClassifierThresholdsConfig;
  controlScope: ICanaryScope;
  experimentScope: ICanaryScope;
}

export interface ICanaryScope {
  scope: string;
  region: string;
  start: string;
  end: string;
  step: number;
}
