'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.netflix.pipeline.stage.canary.canaryDeploymentStage', [])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      synthetic: true,
      key: 'canaryDeployment',
      executionDetailsUrl: require('./canaryDeploymentExecutionDetails.html'),
    });
  });
