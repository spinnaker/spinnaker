'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.canary.canaryDeployment', [
  require('./canaryDeploymentStage.js'),
  require('./canaryDeploymentExecutionDetails.controller.js'),
]);
