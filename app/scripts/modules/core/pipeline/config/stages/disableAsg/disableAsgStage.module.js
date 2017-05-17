'use strict';

const angular = require('angular');

import {DISABLE_ASG_EXECUTION_DETAILS_CTRL} from './templates/disableAsgExecutionDetails.controller';

module.exports = angular.module('spinnaker.core.pipeline.stage.disableAsg', [
  require('./disableAsgStage.js'),
  require('../stage.module.js'),
  require('../core/stage.core.module.js'),
  require('core/account/account.module.js'),
  DISABLE_ASG_EXECUTION_DETAILS_CTRL,
]);
