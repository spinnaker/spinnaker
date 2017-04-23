'use strict';

let angular = require('angular');

import {BUILD_DISPLAY_NAME_FILTER} from './executionBuild/buildDisplayName.filter';
import {STAGE_FAILURE_MESSAGE_COMPONENT} from './stageFailureMessage/stageFailureMessage.component';
import {DELIVERY_STATES} from './delivery.states';
import {EXECUTION_BUILD_NUMBER_COMPONENT} from './executionBuild/executionBuildNumber.component';
import {EXECUTION_STATUS_COMPONENT} from './status/executionStatus.component';
import {EXECUTION_DETAILS_COMPONENT} from './details/executionDetails.component';
import {EXECUTION_COMPONENT} from './executionGroup/execution/execution.component';

module.exports = angular.module('spinnaker.delivery', [

  require('./details/executionDetails.controller.js'),
  require('./details/singleExecutionDetails.controller.js'),
  EXECUTION_COMPONENT,
  EXECUTION_DETAILS_COMPONENT,
  require('./details/executionDetailsSectionNav.directive.js'),

  BUILD_DISPLAY_NAME_FILTER,
  EXECUTION_BUILD_NUMBER_COMPONENT,
  require('./executions/executions.directive.js'),

  require('./filter/executionFilters.directive.js'),

  require('./manualExecution/manualPipelineExecution.controller.js'),

  STAGE_FAILURE_MESSAGE_COMPONENT,
  EXECUTION_STATUS_COMPONENT,

  require('../utils/appendTransform.js'),
  require('../utils/moment.js'),

  require('./delivery.dataSource'),
  DELIVERY_STATES,
]);
