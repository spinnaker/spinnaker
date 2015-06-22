'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stage.canary.canaryDeployment', [
  require('./canaryDeploymentExecutionDetails.controller.js'),
]);
