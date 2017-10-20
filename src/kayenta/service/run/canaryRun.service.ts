import { IExecution } from '@spinnaker/core';

import { CanarySettings } from 'kayenta/canary.settings';
import { localCanaryRunService } from './localCanaryRun.service';
import { IMetricSetPair } from 'kayenta/domain/index';

export interface ICanaryRunService {
  getCanaryRunsForConfig: (configName: string) => Promise<IExecution[]>;
  getCanaryRun: (configName: string, runId: string) => Promise<IExecution>;
  getMetricSetPair: (configName: string, runId: string, pairId: string) => Promise<IMetricSetPair>;
}

let runService: ICanaryRunService;
if (CanarySettings.liveCalls) {
  /* tslint:disable-next-line: no-console */
  console.warn('Live API calls not yet implemented.');
  runService = {
    getCanaryRunsForConfig: () => Promise.resolve([]),
    getCanaryRun: () => Promise.resolve(null),
    getMetricSetPair: () => Promise.resolve(null),
  };
} else {
  runService = localCanaryRunService;
}

export const getCanaryRunsForConfig = (configName: string): Promise<IExecution[]> =>
  runService.getCanaryRunsForConfig(configName);

export const getCanaryRun = (configName: string, runId: string): Promise<IExecution> =>
  runService.getCanaryRun(configName, runId);

export const getMetricSetPair = (configName: string, runId: string, pairId: string): Promise<IMetricSetPair> =>
  runService.getMetricSetPair(configName, runId, pairId);
