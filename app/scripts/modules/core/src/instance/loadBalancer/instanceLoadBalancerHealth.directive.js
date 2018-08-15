'use strict';

import { react2angular } from 'react2angular';
import { InstanceLoadBalancerHealth } from './InstanceLoadBalancerHealth';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.instance.loadBalancer.health.directive', [])
  .component('instanceLoadBalancerHealth', react2angular(InstanceLoadBalancerHealth, ['loadBalancer']));
