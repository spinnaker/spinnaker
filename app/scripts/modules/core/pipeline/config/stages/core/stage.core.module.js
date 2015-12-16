'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.core', [
  require('./executionSteps.directive.js'),
  require('../../../../task/displayableTasks.filter.js'),
]);
