'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stage.quickPatchAsg.bulkQuickPatchStage', [
  require('../../stage.module.js'),
  require('../../core/stage.core.module.js'),
  require('../../../../../account/account.module.js'),
  require('./bulkQuickPatchStageExecutionDetails.controller.js')
]);
