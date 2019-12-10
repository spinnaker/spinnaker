'use strict';

const angular = require('angular');

export const CORE_TASK_MONITOR_TASKMONITOR_MODULE = 'spinnaker.tasks.monitor';
export const name = CORE_TASK_MONITOR_TASKMONITOR_MODULE; // for backwards compatibility
angular.module(CORE_TASK_MONITOR_TASKMONITOR_MODULE, [
  require('./taskMonitor.directive').TASKS_MONITOR_DIRECTIVE,
  require('./multiTaskMonitor.component').name,
]);
