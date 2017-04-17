'use strict';

let angular = require('angular');

import {TASK_STATES} from './task.states';
import {PLATFORM_HEALTH_OVERRIDE_MESSAGE} from './platformHealthOverrideMessage.component';

require('./tasks.less');

module.exports = angular
  .module('spinnaker.core.task', [
    require('./verification/userVerification.directive.js'),
    require('./monitor/taskMonitor.module.js'),
    require('./statusGlyph.directive.js'),
    require('./task.write.service.js'),
    require('./tasks.controller.js'),
    require('./task.dataSource'),
    TASK_STATES,
    PLATFORM_HEALTH_OVERRIDE_MESSAGE,
  ]);
