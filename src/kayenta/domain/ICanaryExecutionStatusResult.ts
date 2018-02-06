import { ICanaryJudgeResult } from './ICanaryJudgeResult';
import { ICanaryClassifierThresholdsConfig, ICanaryConfig } from './ICanaryConfig';

export interface ICanaryExecutionStatusResult {
  id: string // Added by Deck on load.
  complete: boolean;
  status: string;
  result: ICanaryResult;
  stageStatus: { [key: string]: string };
  startTimeIso: string;
}

export interface ICanaryResult {
  judgeResult: ICanaryJudgeResult;
  config: ICanaryConfig;
  canaryExecutionRequest: ICanaryExecutionRequest;
  metricSetPairListId: string;
  parentPipelineExecutionId: string;
  pipelineId: string;
}

export interface ICanaryExecutionRequest {
  thresholds: ICanaryClassifierThresholdsConfig;
  scopes: {
    [scopeName: string]: {
      controlScope: ICanaryScope;
      experimentScope: ICanaryScope;
    };
  };
}

export interface ICanaryScope {
  scope: string;
  region: string;
  start: string;
  end: string;
  step: number;
}
