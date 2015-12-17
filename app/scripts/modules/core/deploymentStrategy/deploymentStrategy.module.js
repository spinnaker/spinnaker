'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.deploymentStrategy', [
  require('../utils/utils.module.js'),
  require('./deploymentStrategySelector.directive.js'),
  require('./deploymentStrategyConfig.provider.js'),
  require('./deploymentStrategySelector.controller.js'),
  require('./services/deploymentStrategy.service.js')
]);
