'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.canary.canaryDeploymentStage', [])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      synthetic: true,
      key: 'canaryDeployment',
      executionDetailsUrl: require('./canaryDeploymentExecutionDetails.html'),
    });
  });
