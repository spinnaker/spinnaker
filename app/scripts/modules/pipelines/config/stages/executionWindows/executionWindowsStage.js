'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stage.executionWindowsStage', [])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Restrict Execution During',
      synthetic: true,
      description: 'Restricts execution of stage during specified period of time',
      key: 'restrictExecutionDuringTimeWindow',
      executionDetailsUrl: 'app/scripts/modules/pipelines/config/stages/executionWindows/executionWindowsDetails.html',
    });
  })
  .run(function(pipelineConfig, executionWindowsTransformer) {
    pipelineConfig.registerTransformer(executionWindowsTransformer);
  })
  .name;
