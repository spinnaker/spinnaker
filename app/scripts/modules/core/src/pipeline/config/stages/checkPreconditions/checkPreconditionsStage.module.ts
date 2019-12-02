import { module } from 'angular';

import { CHECK_PRECONDITIONS_STAGE } from './checkPreconditionsStage';
import { STAGE_COMMON_MODULE } from '../common/stage.common.module';
import { CORE_PIPELINE_CONFIG_STAGES_STAGE_MODULE } from '../stage.module';

export const CHECK_PRECONDITIONS_STAGE_MODULE = 'spinnaker.pipelines.stage.checkPreconditions';
module(CHECK_PRECONDITIONS_STAGE_MODULE, [
  CORE_PIPELINE_CONFIG_STAGES_STAGE_MODULE,
  STAGE_COMMON_MODULE,
  CHECK_PRECONDITIONS_STAGE,
]);
