'use strict';

const angular = require('angular');

import { PLATFORM_HEALTH_OVERRIDE_MESSAGE } from './platformHealthOverrideMessage.component';
import { STATUS_GLYPH_COMPONENT } from 'core/task/statusGlyph.component';
import { TASK_STATES } from './task.states';

import './tasks.less';

module.exports = angular.module('spinnaker.core.task', [
  require('./verification/userVerification.directive').name,
  require('./modal/reason.directive').name,
  require('./monitor/taskMonitor.module').name,
  STATUS_GLYPH_COMPONENT,
  require('./tasks.controller').name,
  require('./task.dataSource').name,
  TASK_STATES,
  PLATFORM_HEALTH_OVERRIDE_MESSAGE,
]);
