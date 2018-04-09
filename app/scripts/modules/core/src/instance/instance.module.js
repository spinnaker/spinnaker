'use strict';

const angular = require('angular');

import { INSTANCE_STATES } from './instance.states';
import './instanceSearchResultType';
import './instanceSelection.less';

module.exports = angular.module('spinnaker.core.instance', [
  require('./details/console/consoleOutputLink.directive.js').name,
  require('./loadBalancer/instanceLoadBalancerHealth.directive.js').name,
  require('./details/multipleInstances.controller.js').name,
  require('./details/instanceLinks.component').name,
  INSTANCE_STATES,
]);
