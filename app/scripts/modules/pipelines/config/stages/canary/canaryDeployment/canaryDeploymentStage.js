'use strict';

let angular = require('angular');

require('./canaryDeploymentExecutionDetails.html');

module.exports = angular.module('spinnaker.pipelines.stage.canary.canaryDeployment')
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      synthetic: true,
      key: 'canaryDeployment',
      executionDetailsUrl: require('./canaryDeploymentExecutionDetails.html'),
    });
  }).name;
