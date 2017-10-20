import { createSelector } from 'reselect';

import { IExecution } from '@spinnaker/core';

import { ICanaryState } from '../reducers/index';
import { CANARY_JUDGE } from '../service/run/canaryRunStages';
import { ICanaryJudgeStage } from '../domain/ICanaryJudgeStage';

export const runSelector = (state: ICanaryState): IExecution => state.selectedRun.run;

export const canaryJudgeStageSelector = createSelector(
  runSelector,
  (run: IExecution) => run.stages.find(s => s.type === CANARY_JUDGE) as ICanaryJudgeStage,
);

export const judgeResultSelector = createSelector(
  canaryJudgeStageSelector,
  (stage: ICanaryJudgeStage) => stage.context.result,
);

