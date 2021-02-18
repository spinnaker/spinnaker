import { module } from 'angular';
import { withErrorBoundary } from 'core/presentation/SpinErrorBoundary';
import { react2angular } from 'react2angular';

import { ManagedResourceDetailsIndicator } from './ManagedResourceDetailsIndicator';

export const MANAGED_RESOURCE_DETAILS_INDICATOR = 'spinnaker.core.managed.resourceDetailsIndicator.component';
module(MANAGED_RESOURCE_DETAILS_INDICATOR, []).component(
  'managedResourceDetailsIndicator',
  react2angular(withErrorBoundary(ManagedResourceDetailsIndicator, 'managedResourceDetailsIndicator'), [
    'resourceSummary',
    'application',
  ]),
);
