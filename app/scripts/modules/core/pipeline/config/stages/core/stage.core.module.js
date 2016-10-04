'use strict';
import displayableTaskFilter from '../../../../task/displayableTasks.filter';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.core', [
  require('./executionSteps.directive.js'),
  displayableTaskFilter,
]);
