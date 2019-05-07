import { ICanaryJudgeResult } from './ICanaryJudgeResult';
import { ICanaryConfig } from './ICanaryConfig';
import { ICanaryScoreThresholds } from './ICanaryScoreThresholds';

export const CANARY_EXECUTION_NO_PIPELINE_STATUS = 'no-parent-pipeline-execution';

export interface ICanaryExecutionStatusResult {
  id: string; // Added by Deck on load.
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
}

export interface ICanaryExecutionRequest {
  thresholds: ICanaryScoreThresholds;
  scopes: {
    [scopeName: string]: ICanaryScopePair;
  };
}

export interface ICanaryExecutionRequestParams {
  application?: string;
  parentPipelineExecutionId?: string;
  metricsAccountName?: string;
  storageAccountName?: string;
  configurationAccountName?: string;
}

export interface ICanaryExecutionResponse {
  canaryExecutionId: string;
}

export type ICanaryScopesByName = ICanaryExecutionRequest['scopes'];

export interface ICanaryScopePair {
  controlScope: ICanaryScope;
  experimentScope: ICanaryScope;
}

export interface ICanaryScope {
  scope: string;
  location: string;
  start: string;
  end: string;
  step: number;
  extendedScopeParams?: { [param: string]: string };
}
