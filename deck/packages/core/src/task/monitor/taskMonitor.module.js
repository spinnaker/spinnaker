import { module } from 'angular';

import { TASKS_MONITOR_DIRECTIVE as TASKMONITOR_DIRECTIVE } from './taskMonitor.directive';

('use strict');

export const CORE_TASK_MONITOR_TASKMONITOR_MODULE = 'spinnaker.tasks.monitor';
export const name = CORE_TASK_MONITOR_TASKMONITOR_MODULE; // for backwards compatibility
module(CORE_TASK_MONITOR_TASKMONITOR_MODULE, [TASKMONITOR_DIRECTIVE]);
