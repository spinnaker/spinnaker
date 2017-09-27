'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.pipeline.stage.waitForParentTasks', [
    require('./waitForParentTasks.transformer.js').name,
  ])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      key: 'waitForRequisiteCompletion',
      synthetic: true,
      executionDetailsUrl: require('./waitForParentTasksExecutionDetails.html'),
    });
  })
  .run(function(pipelineConfig, waitForParentTasksTransformer) {
    pipelineConfig.registerTransformer(waitForParentTasksTransformer);
  });
