'use strict';

const angular = require('angular');

import { PLATFORM_HEALTH_OVERRIDE_MESSAGE } from './platformHealthOverrideMessage.component';
import { STATUS_GLYPH_COMPONENT } from 'core/task/statusGlyph.component';
import { TASK_STATES } from './task.states';

import './tasks.less';

export const CORE_TASK_TASK_MODULE = 'spinnaker.core.task';
export const name = CORE_TASK_TASK_MODULE; // for backwards compatibility
angular.module(CORE_TASK_TASK_MODULE, [
  require('./verification/userVerification.directive').name,
  require('./modal/reason.directive').name,
  require('./monitor/taskMonitor.module').name,
  STATUS_GLYPH_COMPONENT,
  require('./tasks.controller').name,
  require('./task.dataSource').name,
  TASK_STATES,
  PLATFORM_HEALTH_OVERRIDE_MESSAGE,
]);
