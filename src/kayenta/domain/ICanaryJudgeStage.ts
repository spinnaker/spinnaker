import { IExecutionStage } from '@spinnaker/core';
import { ICanaryJudgeResult } from './ICanaryJudgeResult';
import { ICanaryClassifierThresholdsConfig } from './ICanaryConfig';

export interface ICanaryJudgeStage extends IExecutionStage {
  context: {
    orchestratorScoreThresholds: ICanaryClassifierThresholdsConfig;
    storageAccountName: string;
    configurationAccountName: string;
    metricSetPairListId: string;
    user: string;
    durationString: string;
    canaryConfigId: string;
    canaryJudgeResultId: string;
    result: ICanaryJudgeResult;
  }
}
