'use strict';
import {DISPLAYABLE_TASKS_FILTER} from 'core/task/displayableTasks.filter';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.core', [
  require('./executionSteps.directive.js'),
  DISPLAYABLE_TASKS_FILTER
]);
