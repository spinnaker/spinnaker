import { module } from 'angular';

import { CORE_PIPELINE_CONFIG_STAGES_RUNJOB_RUNJOBSTAGE } from './runJobStage';

('use strict');

export const CORE_PIPELINE_CONFIG_STAGES_RUNJOB_RUNJOBSTAGE_MODULE = 'spinnaker.core.pipeline.stage.runJob';
export const name = CORE_PIPELINE_CONFIG_STAGES_RUNJOB_RUNJOBSTAGE_MODULE; // for backwards compatibility
module(CORE_PIPELINE_CONFIG_STAGES_RUNJOB_RUNJOBSTAGE_MODULE, [CORE_PIPELINE_CONFIG_STAGES_RUNJOB_RUNJOBSTAGE]);
