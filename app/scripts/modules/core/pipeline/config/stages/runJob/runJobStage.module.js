'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.runJob', [
  require('../../../../utils/lodash.js'),
  require('./runJobStage.js'),
  require('./runJobExecutionDetails.controller.js'),
  require('../deploy/clusterName.filter.js'),
  require('../core/stage.core.module.js'),
  require('../../../../account/providerToggles.directive.js'),
]);
