'use strict';

import { module } from 'angular';

import { Registry } from '../../../../registry';
import { CORE_PIPELINE_CONFIG_STAGES_WAITFORPARENTTASKS_WAITFORPARENTTASKS_TRANSFORMER } from './waitForParentTasks.transformer';

export const CORE_PIPELINE_CONFIG_STAGES_WAITFORPARENTTASKS_WAITFORPARENTTASKS =
  'spinnaker.core.pipeline.stage.waitForParentTasks';
export const name = CORE_PIPELINE_CONFIG_STAGES_WAITFORPARENTTASKS_WAITFORPARENTTASKS; // for backwards compatibility
module(CORE_PIPELINE_CONFIG_STAGES_WAITFORPARENTTASKS_WAITFORPARENTTASKS, [
  CORE_PIPELINE_CONFIG_STAGES_WAITFORPARENTTASKS_WAITFORPARENTTASKS_TRANSFORMER,
])
  .config(function () {
    Registry.pipeline.registerStage({
      key: 'waitForRequisiteCompletion',
      synthetic: true,
      executionDetailsUrl: require('./waitForParentTasksExecutionDetails.html'),
    });
  })
  .run([
    'waitForParentTasksTransformer',
    function (waitForParentTasksTransformer) {
      Registry.pipeline.registerTransformer(waitForParentTasksTransformer);
    },
  ]);
