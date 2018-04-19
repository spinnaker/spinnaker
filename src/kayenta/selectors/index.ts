import { createSelector } from 'reselect';
import { get } from 'lodash';

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
  run => run.config ? run.config.id : run.result.config.id,
);

export const metricResultsSelector = createSelector(
  runSelector,
  run => run.result.judgeResult.results,
);

export const serializedCanaryConfigSelector = createSelector(
  runSelector,
  run => run.config || run.result.config,
);

export const serializedGroupWeightsSelector = createSelector(
  serializedCanaryConfigSelector,
  (config: ICanaryConfig) => config.classifier.groupWeights,
);

export const selectedMetricResultIdSelector =
  (state: ICanaryState): string => state.selectedRun.selectedMetric;

export const selectedMetricResultSelector = createSelector(
  selectedMetricResultIdSelector,
  metricResultsSelector,
  (id, results) => results.find(result => result.id === id),
);

export const selectedMetricConfigSelector = createSelector(
  selectedMetricResultSelector,
  serializedCanaryConfigSelector,
  (metric, config) =>
    config.metrics.find(m => m.name === metric.name),
);

export const selectedConfigSelector = (state: ICanaryState) => state.selectedConfig.config;

export const configTemplatesSelector = createSelector(
  selectedConfigSelector,
  config => config ? config.templates : null,
);

export const editingTemplateSelector = (state: ICanaryState) => state.selectedConfig.editingTemplate;

export const resolveConfigIdFromExecutionId = (state: ICanaryState, executionId: string): string => {
  const executions = get(state, ['data', 'executions', 'data'], [])
  const execution = executions.find(ex => ex.pipelineId === executionId);
  return execution.canaryConfigId;
};
