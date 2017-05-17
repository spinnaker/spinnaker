'use strict';

const angular = require('angular');

import {DESTROY_ASG_EXECUTION_DETAILS_CTRL} from './templates/destroyAsgExecutionDetails.controller';

module.exports = angular.module('spinnaker.core.pipeline.stage.destroyAsg', [
  require('./destroyAsgStage.js'),
  DESTROY_ASG_EXECUTION_DETAILS_CTRL,
  require('../stage.module.js'),
  require('../core/stage.core.module.js'),
  require('core/account/account.module.js'),
]);
