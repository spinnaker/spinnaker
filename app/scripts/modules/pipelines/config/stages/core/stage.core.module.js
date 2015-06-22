'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stage.core', [
  require('./executionSteps.directive.js'),
  require('./displayableTasks.filter.js'),
]);
