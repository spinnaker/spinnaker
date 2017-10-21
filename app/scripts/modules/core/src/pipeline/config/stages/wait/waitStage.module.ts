import { module } from 'angular';

import { WAIT_STAGE } from './waitStage';

export const WAIT_STAGE_MODULE = 'spinnaker.core.pipeline.stage.wait';
module(WAIT_STAGE_MODULE, [
  WAIT_STAGE,
]);
