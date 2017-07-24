import { module } from 'angular';

import { KAYENTA_CANARY_STAGE } from './kayentaStage/kayentaStage';

export const CANARY_STAGES = 'spinnaker.kayenta.stages.module';
module(CANARY_STAGES, [
  KAYENTA_CANARY_STAGE,
]);
