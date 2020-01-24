'use strict';

import { module } from 'angular';

import { PLATFORM_HEALTH_OVERRIDE_MESSAGE } from './platformHealthOverrideMessage.component';
import { STATUS_GLYPH_COMPONENT } from './statusGlyph.component';
import { TASK_STATES } from './task.states';

import './tasks.less';
import { CORE_TASK_VERIFICATION_USERVERIFICATION_DIRECTIVE } from './verification/userVerification.directive';
import { CORE_TASK_MODAL_REASON_DIRECTIVE } from './modal/reason.directive';
import { CORE_TASK_MONITOR_TASKMONITOR_MODULE } from './monitor/taskMonitor.module';
import { CORE_TASK_TASKS_CONTROLLER } from './tasks.controller';
import { CORE_TASK_TASK_DATASOURCE } from './task.dataSource';

export const CORE_TASK_TASK_MODULE = 'spinnaker.core.task';
export const name = CORE_TASK_TASK_MODULE; // for backwards compatibility
module(CORE_TASK_TASK_MODULE, [
  CORE_TASK_VERIFICATION_USERVERIFICATION_DIRECTIVE,
  CORE_TASK_MODAL_REASON_DIRECTIVE,
  CORE_TASK_MONITOR_TASKMONITOR_MODULE,
  STATUS_GLYPH_COMPONENT,
  CORE_TASK_TASKS_CONTROLLER,
  CORE_TASK_TASK_DATASOURCE,
  TASK_STATES,
  PLATFORM_HEALTH_OVERRIDE_MESSAGE,
]);
