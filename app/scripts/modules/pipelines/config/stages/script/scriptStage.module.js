'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stage.script', [
  require('../stage.module.js'),
  require('../core/stage.core.module.js'),
  require('./scriptExecutionDetails.controller.js'),
]);
