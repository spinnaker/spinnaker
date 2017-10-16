'use strict';

const angular = require('angular');

import { STAGE_CORE_MODULE } from '../core/stage.core.module';

import './manualJudgmentExecutionDetails.less';

module.exports = angular.module('spinnaker.core.pipeline.stage.manualJudgment', [
  require('../stage.module.js').name,
  STAGE_CORE_MODULE,
  require('./manualJudgmentExecutionDetails.controller.js').name,
  require('./manualJudgmentStage.js').name,
  require('../../../../notification/modal/editNotification.controller.modal.js').name,
]);
