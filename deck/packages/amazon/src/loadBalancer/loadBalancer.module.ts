import { module } from 'angular';

import { LOAD_BALANCER_ACTIONS } from './details/loadBalancerActions.component';
import { AWS_LOAD_BALANCER_DETAILS_CTRL } from './details/loadBalancerDetails.controller';
import { AWS_TARGET_GROUP_DETAILS_CTRL } from './details/targetGroupDetails.controller';
import { TARGET_GROUP_STATES } from './targetGroup.states';

export const AWS_LOAD_BALANCER_MODULE = 'spinnaker.amazon.loadBalancer';

module(AWS_LOAD_BALANCER_MODULE, [
  AWS_LOAD_BALANCER_DETAILS_CTRL,
  AWS_TARGET_GROUP_DETAILS_CTRL,
  LOAD_BALANCER_ACTIONS,
  TARGET_GROUP_STATES,
]);
