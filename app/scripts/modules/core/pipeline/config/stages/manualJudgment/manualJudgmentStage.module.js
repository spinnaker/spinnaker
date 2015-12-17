'use strict';

let angular = require('angular');

require('./manualJudgmentExecutionDetails.less');

module.exports = angular.module('spinnaker.core.pipeline.stage.manualJudgment', [
  require('../stage.module.js'),
  require('../core/stage.core.module.js'),
  require('./manualJudgmentExecutionDetails.controller.js'),
  require('./modal/editNotification.controller.modal.js'),
  require('./manualJudgmentStage.js'),
]);
