'use strict';

import { module } from 'angular';

import { WaitForParentTasksExecutionDetails } from './WaitForParentTasksExecutionDetails';
import { NoConfigurationStageConfig } from '../common';
import { Registry } from '../../../../registry';
import { CORE_PIPELINE_CONFIG_STAGES_WAITFORPARENTTASKS_WAITFORPARENTTASKS_TRANSFORMER } from './waitForParentTasks.transformer';

export const CORE_PIPELINE_CONFIG_STAGES_WAITFORPARENTTASKS_WAITFORPARENTTASKS =
  'spinnaker.core.pipeline.stage.waitForParentTasks';
export const name = CORE_PIPELINE_CONFIG_STAGES_WAITFORPARENTTASKS_WAITFORPARENTTASKS; // for backwards compatibility
export const waitForParentTasksStage = {
  key: 'waitForRequisiteCompletion',
  synthetic: true,
  component: NoConfigurationStageConfig,
  executionDetailsSections: [WaitForParentTasksExecutionDetails],
};

Registry.pipeline.registerStage(waitForParentTasksStage);
module(CORE_PIPELINE_CONFIG_STAGES_WAITFORPARENTTASKS_WAITFORPARENTTASKS, [
  CORE_PIPELINE_CONFIG_STAGES_WAITFORPARENTTASKS_WAITFORPARENTTASKS_TRANSFORMER,
]).run([
  'waitForParentTasksTransformer',
  function (waitForParentTasksTransformer) {
    Registry.pipeline.registerTransformer(waitForParentTasksTransformer);
  },
]);
