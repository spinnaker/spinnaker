'use strict';

const angular = require('angular');

import { STAGE_COMMON_MODULE } from '../common/stage.common.module';

export const CORE_PIPELINE_CONFIG_STAGES_TAGIMAGE_TAGIMAGESTAGE_MODULE = 'spinnaker.core.pipeline.stage.tagImage';
export const name = CORE_PIPELINE_CONFIG_STAGES_TAGIMAGE_TAGIMAGESTAGE_MODULE; // for backwards compatibility
angular.module(CORE_PIPELINE_CONFIG_STAGES_TAGIMAGE_TAGIMAGESTAGE_MODULE, [
  require('../stage.module').name,
  STAGE_COMMON_MODULE,
  require('./tagImageStage').name,
]);
