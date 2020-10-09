import { withErrorBoundary } from 'core/presentation/SpinErrorBoundary';
import { module } from 'angular';
import { react2angular } from 'react2angular';

import { SpinnakerContainer } from './SpinnakerContainer';

export const SPINNAKER_CONTAINER_COMPONENT = 'spinnaker.core.container.component';
module(SPINNAKER_CONTAINER_COMPONENT, []).component(
  'spinnakerContainer',
  react2angular(withErrorBoundary(SpinnakerContainer, 'spinnakerContainer'), ['authenticating', 'routing']),
);
