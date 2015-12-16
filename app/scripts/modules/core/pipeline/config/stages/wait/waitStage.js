'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.waitStage', [])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Wait',
      description: 'Waits a specified period of time',
      key: 'wait',
      templateUrl: require('./waitStage.html'),
      executionDetailsUrl: require('./waitExecutionDetails.html'),
      strategy: true,
    });
  });
