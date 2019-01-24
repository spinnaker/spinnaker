'use strict';

const angular = require('angular');

import { STAGE_CORE_MODULE } from '../core/stage.core.module';

module.exports = angular.module('spinnaker.core.pipeline.stage.tagImage', [
  require('../stage.module').name,
  STAGE_CORE_MODULE,
  require('./tagImageStage').name,
]);
