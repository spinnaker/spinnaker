'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deploymentStrategy', [
  require('utils/utils.module.js'),
  require('./deploymentStrategySelector.directive.js'),
  require('./deploymentStrategyConfigProvider.js'),
  require('./deploymentStrategySelector.controller.js'),
  require('./services/deploymentStrategyService.js')
]);
