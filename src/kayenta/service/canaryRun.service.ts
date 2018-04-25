import { sortBy } from 'lodash';

import { API } from '@spinnaker/core';

import { CanarySettings } from 'kayenta/canary.settings';
import { IMetricSetPair, ICanaryExecutionStatusResult } from 'kayenta/domain';

export const getCanaryRun = (configId: string, canaryExecutionId: string): Promise<ICanaryExecutionStatusResult> =>
  API
    .one('v2/canaries/canary')
    .one(configId)
    .one(canaryExecutionId)
    .withParams({ storageAccountName: CanarySettings.storageAccountName })
    .useCache()
    .get()
    .then((run: ICanaryExecutionStatusResult) => {
      const config = run.config || run.result.config;
      config.id = configId;
      run.id = canaryExecutionId;
      run.result.judgeResult.results = sortBy(run.result.judgeResult.results, 'name');
      return run;
    });

export const getMetricSetPair = (metricSetPairListId: string, metricSetPairId: string): Promise<IMetricSetPair> =>
  API
    .one('v2/canaries/metricSetPairList')
    .one(metricSetPairListId)
    .withParams({ storageAccountName: CanarySettings.storageAccountName })
    .useCache()
    .get()
    .then(
      (list: IMetricSetPair[]) =>
        list.find(pair => pair.id === metricSetPairId)
    );

export const listCanaryExecutions = (application: string, limit: number, statuses?: string, storageAccountName?: string): Promise<ICanaryExecutionStatusResult[]> =>
  API
    .one('v2/canaries')
    .one(application)
    .one('executions')
    .withParams({ limit, statuses, storageAccountName })
    .get();

export const getHealthLabel = (health: string, result: string): string => {
  const healthLC = (health || '').toLowerCase();
  const resultLC = (result || '').toLowerCase();
  return healthLC === 'unhealthy' ? 'unhealthy'
    : resultLC === 'success' ? 'healthy'
      : resultLC === 'failure' ? 'failing'
        : 'unknown';
};
