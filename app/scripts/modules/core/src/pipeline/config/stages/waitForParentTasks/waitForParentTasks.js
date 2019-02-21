'use strict';

const angular = require('angular');

import { Registry } from 'core/registry';

module.exports = angular
  .module('spinnaker.core.pipeline.stage.waitForParentTasks', [require('./waitForParentTasks.transformer').name])
  .config(function() {
    Registry.pipeline.registerStage({
      key: 'waitForRequisiteCompletion',
      synthetic: true,
      executionDetailsUrl: require('./waitForParentTasksExecutionDetails.html'),
    });
  })
  .run(['waitForParentTasksTransformer', function(waitForParentTasksTransformer) {
    Registry.pipeline.registerTransformer(waitForParentTasksTransformer);
  }]);
