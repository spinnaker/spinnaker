import { createSelector } from 'reselect';

import { ICanaryState } from '../reducers/index';
import { ICanaryConfig } from 'kayenta/domain/index';
import { ICanaryExecutionStatusResult } from '../domain/ICanaryExecutionStatusResult';

export const runSelector = (state: ICanaryState): ICanaryExecutionStatusResult => state.selectedRun.run;

export const judgeResultSelector = createSelector(
  runSelector,
  run => run.result.judgeResult,
);

export const configIdSelector = createSelector(
  runSelector,
  run => run.result.config.id,
);

export const metricResultsSelector = createSelector(
  runSelector,
  run => run.result.judgeResult.results,
);

export const serializedCanaryConfigSelector = createSelector(
  runSelector,
  run => run.result.config,
);

export const serializedGroupWeightsSelector = createSelector(
  serializedCanaryConfigSelector,
  (config: ICanaryConfig) => config.classifier.groupWeights,
);

export const selectedMetricNameSelector =
  (state: ICanaryState): string => state.selectedRun.selectedMetric;

export const selectedMetricConfigSelector = createSelector(
  selectedMetricNameSelector,
  serializedCanaryConfigSelector,
  (metricName, config) => config.metrics.find(m => m.name === metricName),
);

export const selectedConfigSelector = (state: ICanaryState) => state.selectedConfig.config;

export const configTemplatesSelector = createSelector(
  selectedConfigSelector,
  config => config ? config.templates : null,
);
