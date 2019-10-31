import { module } from 'angular';
import { react2angular } from 'react2angular';

import { ManagedResourceStatusIndicator } from './ManagedResourceStatusIndicator';

export const MANAGED_RESOURCE_STATUS_INDICATOR = 'spinnaker.core.managed.resourceStatusIndicator.component';
module(MANAGED_RESOURCE_STATUS_INDICATOR, []).component(
  'managedResourceStatusIndicator',
  react2angular(ManagedResourceStatusIndicator, ['shape', 'resourceSummary']),
);
