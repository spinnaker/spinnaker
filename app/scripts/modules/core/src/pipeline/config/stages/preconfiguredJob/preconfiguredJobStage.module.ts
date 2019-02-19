import { module } from 'angular';

import { TIME_FORMATTERS } from 'core/utils/timeFormatters';
import { STAGE_COMMON_MODULE } from '../common/stage.common.module';
import { PRECONFIGUREDJOB_STAGE } from './preconfiguredJobStage';

export const PRECONFIGUREDJOB_STAGE_MODULE = 'spinnaker.core.pipeline.stage.preconfiguredjob';
module(PRECONFIGUREDJOB_STAGE_MODULE, [
  PRECONFIGUREDJOB_STAGE,
  require('../stage.module').name,
  STAGE_COMMON_MODULE,
  TIME_FORMATTERS,
]);
