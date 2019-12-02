import { CORE_PIPELINE_CONFIG_STAGES_RUNJOB_RUNJOBSTAGE } from './runJobStage';
('use strict');

const angular = require('angular');

export const CORE_PIPELINE_CONFIG_STAGES_RUNJOB_RUNJOBSTAGE_MODULE = 'spinnaker.core.pipeline.stage.runJob';
export const name = CORE_PIPELINE_CONFIG_STAGES_RUNJOB_RUNJOBSTAGE_MODULE; // for backwards compatibility
angular.module(CORE_PIPELINE_CONFIG_STAGES_RUNJOB_RUNJOBSTAGE_MODULE, [CORE_PIPELINE_CONFIG_STAGES_RUNJOB_RUNJOBSTAGE]);
