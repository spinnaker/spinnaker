import { CORE_TASK_MONITOR_MULTITASKMONITOR_COMPONENT } from './multiTaskMonitor.component';
import { TASKS_MONITOR_DIRECTIVE as TASKMONITOR_DIRECTIVE } from './taskMonitor.directive';
('use strict');

const angular = require('angular');

export const CORE_TASK_MONITOR_TASKMONITOR_MODULE = 'spinnaker.tasks.monitor';
export const name = CORE_TASK_MONITOR_TASKMONITOR_MODULE; // for backwards compatibility
angular.module(CORE_TASK_MONITOR_TASKMONITOR_MODULE, [
  TASKMONITOR_DIRECTIVE,
  CORE_TASK_MONITOR_MULTITASKMONITOR_COMPONENT,
]);
