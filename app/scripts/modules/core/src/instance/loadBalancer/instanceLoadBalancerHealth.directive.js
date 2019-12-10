'use strict';

import { react2angular } from 'react2angular';
import { InstanceLoadBalancerHealth } from './InstanceLoadBalancerHealth';

const angular = require('angular');

export const CORE_INSTANCE_LOADBALANCER_INSTANCELOADBALANCERHEALTH_DIRECTIVE =
  'spinnaker.core.instance.loadBalancer.health.directive';
export const name = CORE_INSTANCE_LOADBALANCER_INSTANCELOADBALANCERHEALTH_DIRECTIVE; // for backwards compatibility
angular
  .module(CORE_INSTANCE_LOADBALANCER_INSTANCELOADBALANCERHEALTH_DIRECTIVE, [])
  .component('instanceLoadBalancerHealth', react2angular(InstanceLoadBalancerHealth, ['loadBalancer', 'ipAddress']));
