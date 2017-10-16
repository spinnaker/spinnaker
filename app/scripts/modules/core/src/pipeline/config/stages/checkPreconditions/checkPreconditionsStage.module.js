'use strict';

const angular = require('angular');

import { STAGE_CORE_MODULE } from '../core/stage.core.module';

module.exports = angular.module('spinnaker.pipelines.stage.checkPreconditions', [
  require('../stage.module.js').name,
  STAGE_CORE_MODULE,
  require('./checkPreconditionsStage.js').name,
]);
