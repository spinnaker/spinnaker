'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.canary.canaryDeployment', [
  require('./canaryDeploymentStage').name,
  require('./canaryDeploymentExecutionDetails.controller').name,
]);
