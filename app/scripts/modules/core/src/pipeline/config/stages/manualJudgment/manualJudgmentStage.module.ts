import { module } from 'angular';

import { MANUAL_JUDGMENT_SERVICE } from './manualJudgment.service';
import { STAGE_COMMON_MODULE } from '../common/stage.common.module';

import './manualJudgmentExecutionDetails.less';
import { CORE_PIPELINE_CONFIG_STAGES_STAGE_MODULE } from '../stage.module';
import { CORE_PIPELINE_CONFIG_STAGES_MANUALJUDGMENT_MANUALJUDGMENTSTAGE } from './manualJudgmentStage';

export const MANUAL_JUDGMENT_STAGE_MODULE = 'spinnaker.core.pipeline.stage.manualJudgment';

module(MANUAL_JUDGMENT_STAGE_MODULE, [
  CORE_PIPELINE_CONFIG_STAGES_STAGE_MODULE,
  MANUAL_JUDGMENT_SERVICE,
  STAGE_COMMON_MODULE,
  CORE_PIPELINE_CONFIG_STAGES_MANUALJUDGMENT_MANUALJUDGMENTSTAGE,
]);
