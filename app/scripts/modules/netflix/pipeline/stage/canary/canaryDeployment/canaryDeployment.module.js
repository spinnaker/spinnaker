'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.netflix.pipeline.stage.canary.canaryDeployment', [
  require('./canaryDeploymentStage.js'),
  require('./canaryDeploymentExecutionDetails.controller.js'),
]);
