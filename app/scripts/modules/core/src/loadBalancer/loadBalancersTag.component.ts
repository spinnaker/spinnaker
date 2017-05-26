import { module } from 'angular';
import { react2angular } from 'react2angular';

import { LoadBalancersTagWrapper } from './LoadBalancersTagWrapper';

export const LOAD_BALANCERS_TAG_COMPONENT = 'spinnaker.core.loadBalancer.loadBalancersTag.component';
module(LOAD_BALANCERS_TAG_COMPONENT, [])
  .component('loadBalancersTag', react2angular(LoadBalancersTagWrapper, ['application', 'serverGroup']));
