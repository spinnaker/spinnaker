'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stage.waitStage', [])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Wait',
      description: 'Waits a specified period of time',
      key: 'wait',
      templateUrl: require('./waitStage.html'),
      executionDetailsUrl: 'app/scripts/modules/pipelines/config/stages/wait/waitExecutionDetails.html',
    });
  })
  .name;
