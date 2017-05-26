import { module } from 'angular';

import { INSTANCES_COMPONENT } from 'core/instance/instances.component';
import { LOAD_BALANCERS_TAG_COMPONENT } from './loadBalancersTag.component';
import { LOAD_BALANCER_DATA_SOURCE } from './loadBalancer.dataSource';
import { LOAD_BALANCER_STATES } from './loadBalancer.states';
import { LOAD_BALANCER_FILTER } from './filter/loadBalancer.filter.component';
import './LoadBalancerSearchResultFormatter';

export const LOAD_BALANCER_MODULE = 'spinnaker.core.loadBalancer';

module(LOAD_BALANCER_MODULE, [
  LOAD_BALANCERS_TAG_COMPONENT,
  LOAD_BALANCER_DATA_SOURCE,
  LOAD_BALANCER_STATES,
  LOAD_BALANCER_FILTER,
  INSTANCES_COMPONENT,
]);
