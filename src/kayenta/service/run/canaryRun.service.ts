import { sortBy } from 'lodash';
import { CanarySettings } from 'kayenta/canary.settings';
import { IMetricSetPair, ICanaryExecutionStatusResult } from 'kayenta/domain/index';
import { liveCanaryRunService } from './liveCanaryRun.service';

export interface ICanaryRunService {
  getCanaryRun: (configId: string, canaryExecutionId: string) => Promise<ICanaryExecutionStatusResult>;
  getMetricSetPair: (metricSetPairListId: string, metricSetPairId: string) => Promise<IMetricSetPair>;
  listCanaryExecutions: (application: string, limit: number, statuses?: string, storageAccountName?: string) => Promise<ICanaryExecutionStatusResult[]>;
}

let runService: ICanaryRunService;
if (CanarySettings.liveCalls) {
  runService = liveCanaryRunService;
} else {
  /* tslint:disable-next-line: no-console */
  console.warn('Local canary run store not implemented.');
}

export const getCanaryRun = (configId: string, canaryExecutionId: string): Promise<ICanaryExecutionStatusResult> =>
  runService.getCanaryRun(configId, canaryExecutionId)
    .then(run => {
      run.result.judgeResult.results = sortBy(run.result.judgeResult.results, 'name');
      return run;
    });

export const getMetricSetPair = (metricSetPairListId: string, metricSetPairId: string): Promise<IMetricSetPair> =>
  runService.getMetricSetPair(metricSetPairListId, metricSetPairId);

export const listCanaryExecutions = (application: string, limit: number, statuses?: string, storageAccountName?: string): Promise<ICanaryExecutionStatusResult[]> =>
  runService.listCanaryExecutions(application, limit, statuses, storageAccountName);
