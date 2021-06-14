import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from '@spinnaker/core';

import { CanaryScore } from './CanaryScore';

export const CANARY_SCORE_COMPONENT = 'spinnaker.canary.score.component';
module(CANARY_SCORE_COMPONENT, []).component(
  'canaryScore',
  react2angular(withErrorBoundary(CanaryScore, 'canaryScore'), ['score', 'health', 'result', 'inverse']),
);
