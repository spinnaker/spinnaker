'use strict';

const angular = require('angular');

import { DELIVERY_STATES } from './delivery.states';
import { EXECUTION_DETAILS_COMPONENT } from './details/executionDetails.component';
import { EXECUTION_DETAILS_CONTROLLER } from './details/executionDetails.controller';
import { BUILD_DISPLAY_NAME_FILTER } from './executionBuild/buildDisplayName.filter';
import { EXECUTION_COMPONENT } from './executionGroup/execution/execution.component';
import { EXECUTION_DETAILS_SECTION_NAV } from './details/executionDetailsSectionNav.component';
import { STAGE_FAILURE_MESSAGE_COMPONENT } from './stageFailureMessage/stageFailureMessage.component';
import { CORE_DELIVERY_DETAILS_SINGLEEXECUTIONDETAILS } from './details/singleExecutionDetails.component';


module.exports = angular.module('spinnaker.delivery', [
  EXECUTION_DETAILS_CONTROLLER,
  CORE_DELIVERY_DETAILS_SINGLEEXECUTIONDETAILS,
  EXECUTION_COMPONENT,
  EXECUTION_DETAILS_COMPONENT,
  EXECUTION_DETAILS_SECTION_NAV,

  BUILD_DISPLAY_NAME_FILTER,

  require('./manualExecution/manualPipelineExecution.controller.js').name,

  STAGE_FAILURE_MESSAGE_COMPONENT,

  require('../utils/appendTransform.js').name,
  require('../utils/moment.js').name,

  require('./delivery.dataSource').name,
  DELIVERY_STATES,
]);
