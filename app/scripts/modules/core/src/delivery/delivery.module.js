'use strict';

const angular = require('angular');

import { DELIVERY_STATES } from './delivery.states';
import { EXECUTION_DETAILS_COMPONENT } from './details/executionDetails.component';
import { EXECUTION_DETAILS_CONTROLLER } from './details/executionDetails.controller';
import { BUILD_DISPLAY_NAME_FILTER } from './executionBuild/buildDisplayName.filter';
import { EXECUTION_COMPONENT } from './executionGroup/execution/execution.component';
import { EXECUTION_FILTERS_COMPONENT } from './filter/executionFilters.component';
import { EXECUTION_GROUPS_COMPONENT } from './executionGroup/executionGroups.component';
import { EXECUTIONS_COMPONENT } from './executions/executions.component';
import { STAGE_FAILURE_MESSAGE_COMPONENT } from './stageFailureMessage/stageFailureMessage.component';
import { CORE_DELIVERY_DETAILS_SINGLEEXECUTIONDETAILS } from './details/singleExecutionDetails.component';


module.exports = angular.module('spinnaker.delivery', [
  EXECUTION_DETAILS_CONTROLLER,
  CORE_DELIVERY_DETAILS_SINGLEEXECUTIONDETAILS,
  EXECUTION_COMPONENT,
  EXECUTION_GROUPS_COMPONENT,
  EXECUTION_DETAILS_COMPONENT,
  EXECUTIONS_COMPONENT,
  require('./details/executionDetailsSectionNav.directive.js').name,

  BUILD_DISPLAY_NAME_FILTER,

  EXECUTION_FILTERS_COMPONENT,

  require('./manualExecution/manualPipelineExecution.controller.js').name,

  STAGE_FAILURE_MESSAGE_COMPONENT,

  require('../utils/appendTransform.js').name,
  require('../utils/moment.js').name,

  require('./delivery.dataSource').name,
  DELIVERY_STATES,
]);
