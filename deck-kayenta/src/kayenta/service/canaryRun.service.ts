import { CanarySettings } from 'kayenta/canary.settings';
import {
  ICanaryExecutionRequest,
  ICanaryExecutionRequestParams,
  ICanaryExecutionResponse,
  ICanaryExecutionStatusResult,
  IMetricSetPair,
} from 'kayenta/domain';

import { REST } from '@spinnaker/core';

export interface ICanaryExecutionRouteService {
  params: Record<string, number | undefined>;
}

export const getCanaryRun = (configId: string, canaryExecutionId: string): Promise<ICanaryExecutionStatusResult> =>
  Promise.resolve(
    REST('/v2/canaries/canary')
      .path(configId, canaryExecutionId)
      .query({ storageAccountName: CanarySettings.storageAccountName })
      .useCache()
      .get<ICanaryExecutionStatusResult>(),
  ).then((run: ICanaryExecutionStatusResult) => {
    const { config } = run;
    config.id = configId;
    run.id = canaryExecutionId;
    run.result?.judgeResult.results.sort((a, b) => a.name.localeCompare(b.name));
    return run;
  });

export const startCanaryRun = (
  configId: string,
  executionRequest: ICanaryExecutionRequest,
  params: ICanaryExecutionRequestParams = {},
): Promise<ICanaryExecutionResponse> => {
  return Promise.resolve(
    REST('/v2/canaries/canary')
      .path(configId)
      .query(params as any)
      .post<ICanaryExecutionResponse>(executionRequest),
  );
};

export const getMetricSetPair = (metricSetPairListId: string, metricSetPairId: string): Promise<IMetricSetPair> =>
  Promise.resolve(
    REST('/v2/canaries/metricSetPairList')
      .path(metricSetPairListId)
      .query({ storageAccountName: CanarySettings.storageAccountName })
      .useCache()
      .get<IMetricSetPair[]>(),
  ).then((list: IMetricSetPair[]) => list.find((pair) => pair.id === metricSetPairId));

export const listCanaryExecutions = (
  application: string,
  stateService: ICanaryExecutionRouteService,
): Promise<ICanaryExecutionStatusResult[]> => {
  const limit = stateService.params.count || 20;
  return Promise.resolve(
    REST('/v2/canaries').path(application, 'executions').query({ limit }).get<ICanaryExecutionStatusResult[]>(),
  );
};

export const getHealthLabel = (health: string, result: string): string => {
  const healthLC = (health || '').toLowerCase();
  const resultLC = (result || '').toLowerCase();
  return healthLC === 'unhealthy'
    ? 'unhealthy'
    : resultLC === 'success'
    ? 'healthy'
    : resultLC === 'failure'
    ? 'failing'
    : 'unknown';
};
