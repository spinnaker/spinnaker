'use strict';

const angular = require('angular');

import './manualJudgmentExecutionDetails.less';

module.exports = angular.module('spinnaker.core.pipeline.stage.manualJudgment', [
  require('../stage.module.js').name,
  require('../core/stage.core.module.js').name,
  require('./manualJudgmentExecutionDetails.controller.js').name,
  require('./manualJudgmentStage.js').name,
  require('../../../../notification/modal/editNotification.controller.modal.js').name,
]);
