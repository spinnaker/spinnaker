import { module } from 'angular';

import { HealthCounts } from './HealthCounts';
import { angularComponentFromReact } from '../angular/angularComponentFromReact';

export const HEALTH_COUNTS_COMPONENT = 'spinnaker.core.healthCounts.component';
module(HEALTH_COUNTS_COMPONENT, []).component(
  'healthCounts',
  angularComponentFromReact(HealthCounts, 'healthCounts', ['container', 'additionalLegendText', 'legendPlacement']),
);
