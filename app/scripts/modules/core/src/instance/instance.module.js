'use strict';

const angular = require('angular');

import { INSTANCE_STATES } from './instance.states';
import './instanceSearchResultType';
import './instanceSelection.less';

export const CORE_INSTANCE_INSTANCE_MODULE = 'spinnaker.core.instance';
export const name = CORE_INSTANCE_INSTANCE_MODULE; // for backwards compatibility
angular.module(CORE_INSTANCE_INSTANCE_MODULE, [
  require('./details/console/consoleOutputLink.directive').name,
  require('./loadBalancer/instanceLoadBalancerHealth.directive').name,
  require('./details/multipleInstances.controller').name,
  require('./details/instanceLinks.component').name,
  INSTANCE_STATES,
]);
