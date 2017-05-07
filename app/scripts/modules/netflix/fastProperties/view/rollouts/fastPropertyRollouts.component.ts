import { module } from 'angular';
import { react2angular } from 'react2angular';

import { FastPropertyRollouts } from './FastPropertyRollouts';

export const FAST_PROPERTY_ROLLOUTS_COMPONENT = 'spinnaker.netflix.fastProperties.rollouts.component';
module(FAST_PROPERTY_ROLLOUTS_COMPONENT, [])
  .component('fastPropertyRollouts', react2angular(FastPropertyRollouts, ['application', 'filters', 'filtersUpdatedStream']));
