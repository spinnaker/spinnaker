import { module } from 'angular';

import { CANARY_SCORE_COMPONENT } from './canaryScore.component';
import { CANARY_SCORES_CONFIG_COMPONENT } from './canaryScores.component';

import './canary.less';

export const CANARY_COMPONENTS = 'spinnaker.kayenta.components.module';
module(CANARY_COMPONENTS, [
  CANARY_SCORE_COMPONENT,
  CANARY_SCORES_CONFIG_COMPONENT,
]);
