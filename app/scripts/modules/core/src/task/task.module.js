'use strict';

const angular = require('angular');

import { PLATFORM_HEALTH_OVERRIDE_MESSAGE } from './platformHealthOverrideMessage.component';
import { TASK_STATES } from './task.states';

import './tasks.less';

module.exports = angular
  .module('spinnaker.core.task', [
    require('./verification/userVerification.directive.js').name,
    require('./modal/reason.directive').name,
    require('./monitor/taskMonitor.module.js').name,
    require('./statusGlyph.directive.js').name,
    require('./tasks.controller.js').name,
    require('./task.dataSource').name,
    TASK_STATES,
    PLATFORM_HEALTH_OVERRIDE_MESSAGE,
  ]);
