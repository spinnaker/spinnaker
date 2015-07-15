'use strict';

let angular = require('angular');

require('./waitStage.html');
require('./waitExecutionDetails.html');

module.exports = angular.module('spinnaker.pipelines.stage.waitStage', [])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Wait',
      description: 'Waits a specified period of time',
      key: 'wait',
      templateUrl: require('./waitStage.html'),
      executionDetailsUrl: require('./waitExecutionDetails.html'),
    });
  })
  .name;
