'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stage.modifyScalingProcess', [
  require('./modifyScalingProcessStage.js'),
  require('../stage.module.js'),
  require('../core/stage.core.module.js'),
  require('../../../../account/account.module.js'),
  require('./modifyScalingProcessExecutionDetails.controller.js'),
]).name;
