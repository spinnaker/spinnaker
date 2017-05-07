import { module } from 'angular';
import { react2angular } from 'react2angular';

import { GlobalRollouts } from './GlobalRollouts';

export const GLOBAL_ROLLOUTS_COMPONENT = 'spinnaker.netflix.fastProperties.globalRollouts.component';
module(GLOBAL_ROLLOUTS_COMPONENT, []).component('globalRollouts', react2angular(GlobalRollouts, ['app']));
