'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stage.canary.canaryDeploymentStage', [])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      synthetic: true,
      key: 'canaryDeployment',
      executionDetailsUrl: require('./canaryDeploymentExecutionDetails.html'),
    });
  }).name;
