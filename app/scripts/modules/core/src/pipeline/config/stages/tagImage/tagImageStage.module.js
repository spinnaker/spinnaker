'use strict';

const angular = require('angular');

import { STAGE_COMMON_MODULE } from '../common/stage.common.module';

module.exports = angular.module('spinnaker.core.pipeline.stage.tagImage', [
  require('../stage.module').name,
  STAGE_COMMON_MODULE,
  require('./tagImageStage').name,
]);
