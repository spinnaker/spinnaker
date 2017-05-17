'use strict';

const angular = require('angular');

import {INSTANCE_STATES} from './instance.states';
import './InstanceSearchResultFormatter';
import './instanceSelection.less';

module.exports = angular
  .module('spinnaker.core.instance', [
    require('./details/console/consoleOutputLink.directive.js'),
    require('./loadBalancer/instanceLoadBalancerHealth.directive.js'),
    require('./details/multipleInstances.controller.js'),
    INSTANCE_STATES
  ]);
