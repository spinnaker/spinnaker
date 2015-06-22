'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stage.bake', [
  require('../stage.module.js'),
  require('../core/stage.core.module.js'),
  require('./bakeExecutionDetails.controller.js'),
  require('../../../../providerSelection/providerSelector.directive.js'),
  require('../../../../account/accountService.js'),
  require('../../../../utils/lodash.js'),
]);
