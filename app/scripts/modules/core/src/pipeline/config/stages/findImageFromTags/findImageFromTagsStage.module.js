'use strict';

const angular = require('angular');

import { STAGE_CORE_MODULE } from '../core/stage.core.module';

module.exports = angular.module('spinnaker.core.pipeline.stage.findImageFromTags', [
  require('../stage.module').name,
  STAGE_CORE_MODULE,
  require('./findImageFromTagsStage').name,
]);
