import { module } from 'angular';

import { CHECK_PRECONDITIONS_STAGE } from './checkPreconditionsStage';
import { STAGE_CORE_MODULE } from '../core/stage.core.module';

export const CHECK_PRECONDITIONS_STAGE_MODULE = 'spinnaker.pipelines.stage.checkPreconditions';
module(CHECK_PRECONDITIONS_STAGE_MODULE, [
  require('../stage.module').name,
  STAGE_CORE_MODULE,
  CHECK_PRECONDITIONS_STAGE,
]);
