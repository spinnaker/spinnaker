'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stage.quickPatchAsg', [
  require('./quickPatchAsgStage.js'),
  require('../stage.module.js'),
  require('../core/stage.core.module.js'),
  require('../../../../core/account/account.module.js'),
  require('./quickPatchAsgExecutionDetails.controller.js')
]).name;
