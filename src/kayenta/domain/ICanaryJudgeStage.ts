import { IExecutionStage } from '@spinnaker/core';
import { ICanaryJudgeResult } from './ICanaryJudgeResult';

export interface ICanaryJudgeStage extends IExecutionStage {
  context: {
    orchestratorScoreThresholds: {
      pass: number;
      marginal: number;
    };
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
