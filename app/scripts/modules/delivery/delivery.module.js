'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.delivery', [

  require('./executionGroupHeading.controller.js'),
  require('./pipelineExecutions.controller.js'),
  require('./execution.controller.js'),
  require('./executionBar.controller.js'),
  require('./executionGroup.controller.js'),
  require('./executionStatus.controller.js'),
  require('./executionBuildNumber.directive.js'),

  require('./executionGroups.filter.js'),
  require('./statusNames.filter.js'),
  require('./executions.filter.js'),
  require('./buildDisplayName.filter.js'),

  require('./execution.directive.js'),
  require('./executionBar.directive.js'),

  require('./details/executionDetails.directive.js'),
  require('./details/executionDetailsSectionNav.directive.js'),
  require('./details/executionDetails.controller.js'),

  require('./executionGroup.directive.js'),
  require('./executionGroupHeading.directive.js'),
  require('./executionStatus.directive.js'),
  require('./stageFailureMessage/stageFailureMessage.directive.js'),
  require('./manualPipelineExecution.controller.js'),

  require('../caches/deckCacheFactory.js'),
  require('../utils/appendTransform.js'),
  require('../utils/lodash.js'),
  require('../utils/moment.js'),
  require('../utils/rx.js'),
]).name;
