'use strict';

const angular = require('angular');

import { STAGE_COMMON_MODULE } from '../common/stage.common.module';

export const CORE_PIPELINE_CONFIG_STAGES_FINDIMAGEFROMTAGS_FINDIMAGEFROMTAGSSTAGE_MODULE =
  'spinnaker.core.pipeline.stage.findImageFromTags';
export const name = CORE_PIPELINE_CONFIG_STAGES_FINDIMAGEFROMTAGS_FINDIMAGEFROMTAGSSTAGE_MODULE; // for backwards compatibility
angular.module(CORE_PIPELINE_CONFIG_STAGES_FINDIMAGEFROMTAGS_FINDIMAGEFROMTAGSSTAGE_MODULE, [
  require('../stage.module').name,
  STAGE_COMMON_MODULE,
  require('./findImageFromTagsStage').name,
]);
