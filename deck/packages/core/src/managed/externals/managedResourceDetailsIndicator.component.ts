import { module } from 'angular';

import { ManagedResourceDetailsIndicator } from './ManagedResourceDetailsIndicator';
import { angularComponentFromReact } from '../../angular/angularComponentFromReact';

export const MANAGED_RESOURCE_DETAILS_INDICATOR = 'spinnaker.core.managed.resourceDetailsIndicator.component';
module(MANAGED_RESOURCE_DETAILS_INDICATOR, []).component(
  'managedResourceDetailsIndicator',
  angularComponentFromReact(ManagedResourceDetailsIndicator, 'managedResourceDetailsIndicator', [
    'resourceSummary',
    'application',
  ]),
);
