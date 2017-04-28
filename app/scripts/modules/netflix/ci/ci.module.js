'use strict';

let angular = require('angular');

import {CI_STATES} from './ci.states';
import {NETFLIX_CI_TRIGGER_HANDLER_COMPONENT} from './trigger/ciTriggerHandler.component';
import {NETFLIX_GIT_MANUAL_EXECUTION_HANDLER} from './trigger/manualExecution.handler';

require('./ci.less');

module.exports = angular
  .module('spinnaker.netflix.ci', [
    CI_STATES,
    NETFLIX_CI_TRIGGER_HANDLER_COMPONENT,
    NETFLIX_GIT_MANUAL_EXECUTION_HANDLER,
    require('./ci.dataSource'),
    require('./ci.controller'),
    require('./detail/detail.controller'),
    require('./detail/detailTab/detailTab.controller'),
  ]);
