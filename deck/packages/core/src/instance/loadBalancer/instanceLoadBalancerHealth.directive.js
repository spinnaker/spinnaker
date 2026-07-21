import { module } from 'angular';

import { InstanceLoadBalancerHealth } from './InstanceLoadBalancerHealth';
import { angularComponentFromReact } from '../../angular/angularComponentFromReact';

('use strict');

export const CORE_INSTANCE_LOADBALANCER_INSTANCELOADBALANCERHEALTH_DIRECTIVE =
  'spinnaker.core.instance.loadBalancer.health.directive';
export const name = CORE_INSTANCE_LOADBALANCER_INSTANCELOADBALANCERHEALTH_DIRECTIVE; // for backwards compatibility
module(CORE_INSTANCE_LOADBALANCER_INSTANCELOADBALANCERHEALTH_DIRECTIVE, []).component(
  'instanceLoadBalancerHealth',
  angularComponentFromReact(InstanceLoadBalancerHealth, 'instanceLoadBalancerHealth', ['loadBalancer', 'ipAddress']),
);
