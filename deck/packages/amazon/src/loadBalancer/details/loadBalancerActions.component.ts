import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from '@spinnaker/core';

import { LoadBalancerActions } from './LoadBalancerActions';

export const LOAD_BALANCER_ACTIONS = 'spinnaker.amazon.loadBalancer.details.loadBalancerActions.component';
module(LOAD_BALANCER_ACTIONS, []).component(
  'loadBalancerActions',
  react2angular(withErrorBoundary(LoadBalancerActions, 'loadBalancerActions'), [
    'app',
    'loadBalancer',
    'loadBalancerFromParams',
  ]),
);
