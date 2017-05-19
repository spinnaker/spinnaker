'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.tasks.monitor', [
    require('./taskMonitor.directive').TASKS_MONITOR_DIRECTIVE,
    require('./multiTaskMonitor.component'),
  ]);
