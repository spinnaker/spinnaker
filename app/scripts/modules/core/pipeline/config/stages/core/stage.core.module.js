'use strict';
import displayableTaskFilter from 'core/task/displayableTasks.filter';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.core', [
  require('./executionSteps.directive.js'),
  displayableTaskFilter,
]);
