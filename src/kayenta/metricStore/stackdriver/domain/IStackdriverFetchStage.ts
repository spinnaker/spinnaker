import { IExecutionStage } from '@spinnaker/core';

export const STACKDRIVER_FETCH_STAGE = 'stackdriverFetch';

export interface IStackdriverFetchStage extends IExecutionStage {
  context: {
    metricsAccountName: string;
    storageAccountName: string;
    configurationAccountName: string;
    stackdriverCanaryScope: {
      scope: string;
      start: number;
      end: number;
      step: number;
      intervalStartTimeIso: string;
      intervalEndTimeIso: string
    };
    canaryConfigId: string;
    metricSetListIds: string[];
  };
}
