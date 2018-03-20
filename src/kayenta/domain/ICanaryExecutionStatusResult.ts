import { ICanaryJudgeResult } from './ICanaryJudgeResult';
import { ICanaryClassifierThresholdsConfig, ICanaryConfig } from './ICanaryConfig';

export interface ICanaryExecutionStatusResult {
  id: string // Added by Deck on load.
  complete: boolean;
  status: string;
  result: ICanaryResult;
  stageStatus: { [key: string]: string };
  startTimeIso: string;
  application: string;
  pipelineId: string;
  parentPipelineExecutionId: string;
  metricSetPairListId: string;
  config: ICanaryConfig;
  canaryExecutionRequest: ICanaryExecutionRequest;
  storageAccountName: string;
}

export interface ICanaryResult {
  judgeResult: ICanaryJudgeResult;
  config: ICanaryConfig; // TODO: deprecated, use same field on parent; remove after 5/1/18
  canaryExecutionRequest: ICanaryExecutionRequest; // TODO: deprecated, use same field on parent; remove after 5/1/18
  metricSetPairListId: string; // TODO: deprecated, use same field on parent; remove after 5/1/18
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
