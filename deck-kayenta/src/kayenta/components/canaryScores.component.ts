import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from '@spinnaker/core';

import { CanaryScores } from './canaryScores';

export const CANARY_SCORES_CONFIG_COMPONENT = 'spinnaker.kayenta.canaryScores.component';
module(CANARY_SCORES_CONFIG_COMPONENT, []).component(
  'kayentaCanaryScores',
  react2angular(withErrorBoundary(CanaryScores, 'kayentaCanaryScores'), [
    'onChange',
    'successfulHelpFieldId',
    'successfulLabel',
    'successfulScore',
    'unhealthyHelpFieldId',
    'unhealthyLabel',
    'unhealthyScore',
  ]),
);
