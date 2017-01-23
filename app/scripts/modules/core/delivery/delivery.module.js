'use strict';

let angular = require('angular');

import {BUILD_DISPLAY_NAME_FILTER} from './executionBuild/buildDisplayName.filter';
import {STAGE_FAILURE_MESSAGE_COMPONENT} from './stageFailureMessage/stageFailureMessage.component';
import {DELIVERY_STATES} from './delivery.states';

module.exports = angular.module('spinnaker.delivery', [

  require('./details/executionDetails.controller.js'),
  require('./details/singleExecutionDetails.controller.js'),
  require('./details/executionDetails.directive.js'),
  require('./details/executionDetailsSectionNav.directive.js'),

  BUILD_DISPLAY_NAME_FILTER,
  require('./executionBuild/executionBuildNumber.directive.js'),
  require('./executions/executions.directive.js'),

  require('./filter/executionFilters.directive.js'),

  require('./manualExecution/manualPipelineExecution.controller.js'),

  STAGE_FAILURE_MESSAGE_COMPONENT,
  require('./status/executionStatus.directive.js'),

  require('../utils/appendTransform.js'),
  require('../utils/moment.js'),

  require('./delivery.dataSource'),
  DELIVERY_STATES,
]);
