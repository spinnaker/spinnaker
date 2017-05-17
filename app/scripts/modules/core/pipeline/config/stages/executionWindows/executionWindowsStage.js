'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.executionWindowsStage', [])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Restrict Execution During',
      synthetic: true,
      description: 'Restricts execution of stage during specified period of time',
      key: 'restrictExecutionDuringTimeWindow',
      executionDetailsUrl: require('./executionWindowsDetails.html'),
      executionConfigSections: ['windowConfig', 'taskStatus'],
    });
  })
  .run(function(pipelineConfig, executionWindowsTransformer) {
    pipelineConfig.registerTransformer(executionWindowsTransformer);
  });
