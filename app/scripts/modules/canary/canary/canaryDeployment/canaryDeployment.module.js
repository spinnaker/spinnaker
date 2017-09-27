'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.canary.canaryDeployment', [
  require('./canaryDeploymentStage.js').name,
  require('./canaryDeploymentExecutionDetails.controller.js').name,
]);
