'use strict';

import { WaitForParentTasksExecutionDetails } from './WaitForParentTasksExecutionDetails';
import { NoConfigurationStageConfig } from '../common';
import { Registry } from '../../../../registry';
import { WaitForParentTasksTransformer } from './waitForParentTasks.transformer';

export const waitForParentTasksStage = {
  key: 'waitForRequisiteCompletion',
  synthetic: true,
  component: NoConfigurationStageConfig,
  executionDetailsSections: [WaitForParentTasksExecutionDetails],
};

Registry.pipeline.registerStage(waitForParentTasksStage);
Registry.pipeline.registerTransformer(new WaitForParentTasksTransformer());
