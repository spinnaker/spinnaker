import { createSelector } from 'reselect';

import { IExecution } from '@spinnaker/core';

import { ICanaryState } from '../reducers/index';
import { CANARY_JUDGE, SETUP_CANARY } from '../service/run/canaryRunStages';
import { ICanaryJudgeStage, ISetupCanaryStage, ICanaryConfig } from 'kayenta/domain/index';

export const runSelector = (state: ICanaryState): IExecution => state.selectedRun.run;

export const canaryJudgeStageSelector = createSelector(
  runSelector,
  (run: IExecution) => run.stages.find(s => s.type === CANARY_JUDGE) as ICanaryJudgeStage,
);

export const judgeResultSelector = createSelector(
  canaryJudgeStageSelector,
  (stage: ICanaryJudgeStage) => stage.context.result,
);

export const configNameSelector = createSelector(
  canaryJudgeStageSelector,
  (stage: ICanaryJudgeStage) => stage.context.canaryConfigId,
);

export const metricResultsSelector = createSelector(
  canaryJudgeStageSelector,
  (stage: ICanaryJudgeStage) => stage.context.result.results,
);

export const setupCanaryStageSelector = createSelector(
  runSelector,
  (run: IExecution) => run.stages.find(s => s.type === SETUP_CANARY) as ISetupCanaryStage,
);

export const serializedCanaryConfigSelector = createSelector(
  setupCanaryStageSelector,
  (stage: ISetupCanaryStage) => stage.outputs.canaryConfig,
);

export const serializedGroupWeightsSelector = createSelector(
  serializedCanaryConfigSelector,
  (config: ICanaryConfig) => config.classifier.groupWeights,
);
