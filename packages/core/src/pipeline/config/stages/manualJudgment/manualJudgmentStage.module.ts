import { module } from 'angular';

import { STAGE_COMMON_MODULE } from '../common/stage.common.module';
import { MANUAL_JUDGMENT_SERVICE } from './manualJudgment.service';
import { CORE_PIPELINE_CONFIG_STAGES_MANUALJUDGMENT_MANUALJUDGMENTSTAGE } from './manualJudgmentStage';
import { CORE_PIPELINE_CONFIG_STAGES_STAGE_MODULE } from '../stage.module';

import './manualJudgmentExecutionDetails.less';

export const MANUAL_JUDGMENT_STAGE_MODULE = 'spinnaker.core.pipeline.stage.manualJudgment';

module(MANUAL_JUDGMENT_STAGE_MODULE, [
  CORE_PIPELINE_CONFIG_STAGES_STAGE_MODULE,
  MANUAL_JUDGMENT_SERVICE,
  STAGE_COMMON_MODULE,
  CORE_PIPELINE_CONFIG_STAGES_MANUALJUDGMENT_MANUALJUDGMENTSTAGE,
]);
