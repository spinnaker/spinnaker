'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.createLoadBalancer', [
  require('./createLoadBalancerStage.js'),
  require('./createLoadBalancerExecutionDetails.controller.js'),
  require('../stage.module.js'),
  require('../core/stage.core.module.js'),
  require('core/account/account.module.js'),
]);
