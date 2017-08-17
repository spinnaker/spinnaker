import { module } from 'angular';
import { react2angular } from 'react2angular';

import { CanaryScore } from './canaryScore';

export const CANARY_SCORE_COMPONENT = 'spinnaker.kayenta.score.component';
module(CANARY_SCORE_COMPONENT, [])
  .component('kayentaCanaryScore', react2angular(CanaryScore, ['score', 'health', 'result', 'inverse']));
