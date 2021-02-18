import { module } from 'angular';
import { withErrorBoundary } from 'core/presentation/SpinErrorBoundary';
import { react2angular } from 'react2angular';

import { InstanceInsights } from './InstanceInsights';

export const CORE_INSTANCE_DETAILS_INSTANCEINSIGHTS_COMPONENT = 'spinnaker.core.instance.details.instanceInsights';
export const name = CORE_INSTANCE_DETAILS_INSTANCEINSIGHTS_COMPONENT; // for backwards compatibility

module(name, []).component(
  'instanceInsights',
  react2angular(withErrorBoundary(InstanceInsights, 'instanceInsights'), [
    'analytics',
    'insights',
    'instance',
    'title',
  ]),
);
