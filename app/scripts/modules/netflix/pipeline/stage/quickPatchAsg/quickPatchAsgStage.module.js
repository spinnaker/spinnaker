'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.netflix.pipeline.stage.quickPatchAsg', [
  require('./quickPatchAsgStage.js'),
  require('../../../../core/pipeline/config/stages/stage.module.js'),
  require('../../../../core/pipeline/config/stages/core/stage.core.module.js'),
  require('../../../../core/account/account.module.js'),
  require('./quickPatchAsgExecutionDetails.controller.js')
]);
