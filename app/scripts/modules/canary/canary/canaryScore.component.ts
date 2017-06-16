import { module } from 'angular';
import { react2angular } from 'react2angular';

import { CanaryScore } from './CanaryScore';

export const CANARY_SCORE_COMPONENT = 'spinnaker.canary.score.component';
module(CANARY_SCORE_COMPONENT, [])
  .component('canaryScore', react2angular(CanaryScore, ['score', 'health', 'result', 'inverse']));
