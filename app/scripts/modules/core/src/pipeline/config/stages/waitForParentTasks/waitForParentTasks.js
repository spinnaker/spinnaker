'use strict';

const angular = require('angular');

import { Registry } from 'core/registry';

export const CORE_PIPELINE_CONFIG_STAGES_WAITFORPARENTTASKS_WAITFORPARENTTASKS =
  'spinnaker.core.pipeline.stage.waitForParentTasks';
export const name = CORE_PIPELINE_CONFIG_STAGES_WAITFORPARENTTASKS_WAITFORPARENTTASKS; // for backwards compatibility
angular
  .module(CORE_PIPELINE_CONFIG_STAGES_WAITFORPARENTTASKS_WAITFORPARENTTASKS, [
    require('./waitForParentTasks.transformer').name,
  ])
  .config(function() {
    Registry.pipeline.registerStage({
      key: 'waitForRequisiteCompletion',
      synthetic: true,
      executionDetailsUrl: require('./waitForParentTasksExecutionDetails.html'),
    });
  })
  .run([
    'waitForParentTasksTransformer',
    function(waitForParentTasksTransformer) {
      Registry.pipeline.registerTransformer(waitForParentTasksTransformer);
    },
  ]);
