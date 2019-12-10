'use strict';

const angular = require('angular');

import { PIPELINE_BAKE_STAGE_CHOOSE_OS } from 'core/pipeline/config/stages/bake/bakeStageChooseOs.component';

export const CORE_PIPELINE_CONFIG_STAGES_BAKE_BAKESTAGE_MODULE = 'spinnaker.core.pipeline.stage.bake';
export const name = CORE_PIPELINE_CONFIG_STAGES_BAKE_BAKESTAGE_MODULE; // for backwards compatibility
angular.module(CORE_PIPELINE_CONFIG_STAGES_BAKE_BAKESTAGE_MODULE, [
  require('./bakeStage').name,
  require('./modal/addExtendedAttribute.controller.modal').name,
  PIPELINE_BAKE_STAGE_CHOOSE_OS,
]);
