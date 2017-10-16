'use strict';

const angular = require('angular');

import { STAGE_CORE_MODULE } from '../core/stage.core.module';

module.exports = angular.module('spinnaker.core.pipeline.stage.cloneServerGroup', [
  require('./cloneServerGroupStage.js').name,
  require('../stage.module.js').name,
  STAGE_CORE_MODULE,
]);
