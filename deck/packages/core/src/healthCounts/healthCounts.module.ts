import { module } from 'angular';

import { HEALTH_COUNTS_COMPONENT } from './healthCounts.component';

export const HEALTH_COUNTS_MODULE = 'spinnaker.core.healthCounts';
module(HEALTH_COUNTS_MODULE, [HEALTH_COUNTS_COMPONENT]);
