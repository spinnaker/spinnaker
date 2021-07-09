import { module } from 'angular';
import { react2angular } from 'react2angular';

import { InstanceInsights } from './InstanceInsights';
import { withErrorBoundary } from '../../presentation/SpinErrorBoundary';

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
