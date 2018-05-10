import { module } from 'angular';

import { LOAD_BALANCER_DATA_SOURCE } from './loadBalancer.dataSource';
import { LOAD_BALANCER_STATES } from './loadBalancer.states';
import './loadBalancerSearchResultType';

export const LOAD_BALANCER_MODULE = 'spinnaker.core.loadBalancer';

module(LOAD_BALANCER_MODULE, [LOAD_BALANCER_DATA_SOURCE, LOAD_BALANCER_STATES]);
