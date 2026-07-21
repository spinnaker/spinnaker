import { module } from 'angular';

import { SpinnakerContainer } from './SpinnakerContainer';
import { angularComponentFromReact } from '../angular/angularComponentFromReact';

export const SPINNAKER_CONTAINER_COMPONENT = 'spinnaker.core.container.component';
module(SPINNAKER_CONTAINER_COMPONENT, []).component(
  'spinnakerContainer',
  angularComponentFromReact(SpinnakerContainer, 'spinnakerContainer', ['authenticating', 'routing']),
);
