import { module } from 'angular';
import { react2angular } from 'react2angular';

import { HealthCounts } from './HealthCounts';
import { withErrorBoundary } from '../presentation/SpinErrorBoundary';

export const HEALTH_COUNTS_COMPONENT = 'spinnaker.core.healthCounts.component';
module(HEALTH_COUNTS_COMPONENT, []).component(
  'healthCounts',
  react2angular(withErrorBoundary(HealthCounts, 'healthCounts'), [
    'container',
    'additionalLegendText',
    'legendPlacement',
  ]),
);
