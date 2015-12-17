'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.delivery', [

  require('./filter/executionFilters.directive.js'),
  require('./executions/executions.directive.js'),
  require('./status/executionStatus.controller.js'),
  require('./executionBuild/executionBuildNumber.directive.js'),

  require('./executionBuild/buildDisplayName.filter.js'),

  require('./details/executionDetails.directive.js'),
  require('./details/executionDetailsSectionNav.directive.js'),
  require('./details/executionDetails.controller.js'),

  require('./status/executionStatus.directive.js'),
  require('./stageFailureMessage/stageFailureMessage.directive.js'),
  require('./manualExecution/manualPipelineExecution.controller.js'),

  require('./states.js'),

  require('../cache/deckCacheFactory.js'),
  require('../utils/appendTransform.js'),
  require('../utils/lodash.js'),
  require('../utils/moment.js'),
  require('../utils/rx.js'),
]);
