import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from '@spinnaker/core';

import { CanaryScore } from './canaryScore';

export const CANARY_SCORE_COMPONENT = 'spinnaker.kayenta.score.component';
module(CANARY_SCORE_COMPONENT, []).component(
  'kayentaCanaryScore',
  react2angular(withErrorBoundary(CanaryScore, 'kayentaCanaryScore'), ['score', 'health', 'result', 'inverse']),
);
