'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.script', [
  require('./scriptStage.js'),
  require('../stage.module.js'),
  require('../core/stage.core.module.js'),
  require('./scriptExecutionDetails.controller.js'),
]);
