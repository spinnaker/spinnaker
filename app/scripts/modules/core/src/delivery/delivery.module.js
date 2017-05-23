'use strict';

const angular = require('angular');

import { DELIVERY_STATES } from './delivery.states';
import { EXECUTION_DETAILS_COMPONENT } from './details/executionDetails.component';
import { BUILD_DISPLAY_NAME_FILTER } from './executionBuild/buildDisplayName.filter';
import { EXECUTION_BUILD_NUMBER_COMPONENT } from './executionBuild/executionBuildNumber.component';
import { EXECUTION_COMPONENT } from './executionGroup/execution/execution.component';
import { EXECUTION_GROUPS_COMPONENT } from './executionGroup/executionGroups.component';
import { EXECUTIONS_COMPONENT } from './executions/executions.component';
import { STAGE_FAILURE_MESSAGE_COMPONENT } from './stageFailureMessage/stageFailureMessage.component';
import { EXECUTION_STATUS_COMPONENT } from './status/executionStatus.component';

module.exports = angular.module('spinnaker.delivery', [

  require('./details/executionDetails.controller.js'),
  require('./details/singleExecutionDetails.controller.js'),
  EXECUTION_COMPONENT,
  EXECUTION_GROUPS_COMPONENT,
  EXECUTION_DETAILS_COMPONENT,
  EXECUTIONS_COMPONENT,
  require('./details/executionDetailsSectionNav.directive.js'),

  BUILD_DISPLAY_NAME_FILTER,
  EXECUTION_BUILD_NUMBER_COMPONENT,

  require('./filter/executionFilters.directive.js'),

  require('./manualExecution/manualPipelineExecution.controller.js'),

  STAGE_FAILURE_MESSAGE_COMPONENT,
  EXECUTION_STATUS_COMPONENT,

  require('../utils/appendTransform.js'),
  require('../utils/moment.js'),

  require('./delivery.dataSource'),
  DELIVERY_STATES,
]);
