import { module } from 'angular';
import { react2angular } from 'react2angular';

import { HealthCounts } from './HealthCounts';

export const HEALTH_COUNTS_COMPONENT = 'spinnaker.core.healthCounts.component';
module(HEALTH_COUNTS_COMPONENT, [])
  .component('healthCounts', react2angular(HealthCounts, ['container', 'additionalLegendText', 'legendPlacement']));
