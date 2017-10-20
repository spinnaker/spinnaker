'use strict';

const angular = require('angular');

import { DELIVERY_STATES } from './delivery.states';
import { BUILD_DISPLAY_NAME_FILTER } from './executionBuild/buildDisplayName.filter';
import { EXECUTION_COMPONENT } from './executionGroup/execution/execution.component';
import { EXECUTION_DETAILS_SECTION_NAV } from './details/executionDetailsSectionNav.component';
import { EXECUTION_FILTER_SERVICE } from 'core/delivery/filter/executionFilter.service';
import { STAGE_FAILURE_MESSAGE_COMPONENT } from './stageFailureMessage/stageFailureMessage.component';
import { CORE_DELIVERY_DETAILS_SINGLEEXECUTIONDETAILS } from './details/singleExecutionDetails.component';
import { STAGE_DETAILS_COMPONENT } from './details/stageDetails.component';
import { STAGE_SUMMARY_COMPONENT } from './details/stageSummary.component';

module.exports = angular.module('spinnaker.delivery', [
  CORE_DELIVERY_DETAILS_SINGLEEXECUTIONDETAILS,
  EXECUTION_COMPONENT,
  EXECUTION_DETAILS_SECTION_NAV,
  EXECUTION_FILTER_SERVICE,

  BUILD_DISPLAY_NAME_FILTER,

  require('./manualExecution/manualPipelineExecution.controller.js').name,

  STAGE_FAILURE_MESSAGE_COMPONENT,
  STAGE_DETAILS_COMPONENT,
  STAGE_SUMMARY_COMPONENT,

  require('../utils/appendTransform.js').name,
  require('../utils/moment.js').name,

  require('./delivery.dataSource').name,
  DELIVERY_STATES,
]);
