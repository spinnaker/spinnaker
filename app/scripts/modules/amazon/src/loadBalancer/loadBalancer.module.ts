import { module } from 'angular';

import { AWS_LOAD_BALANCER_CHOICE_MODAL } from './configure/choice/awsLoadBalancerChoice.modal';
import { AWS_LOAD_BALANCER_DETAILS_CTRL } from './details/loadBalancerDetails.controller';
import { AWS_LOAD_BALANCER_TRANFORMER } from './loadBalancer.transformer';
import { AWS_TARGET_GROUP_DETAILS_CTRL } from './details/targetGroupDetails.controller';
import { TARGET_GROUP_STATES } from './targetGroup.states';

export const AWS_LOAD_BALANCER_MODULE = 'spinnaker.amazon.loadBalancer';

module(AWS_LOAD_BALANCER_MODULE, [
  require('./configure/classic/createClassicLoadBalancer.controller'),
  require('./configure/application/createApplicationLoadBalancer.controller'),
  AWS_LOAD_BALANCER_CHOICE_MODAL,
  AWS_LOAD_BALANCER_DETAILS_CTRL,
  AWS_LOAD_BALANCER_TRANFORMER,
  AWS_TARGET_GROUP_DETAILS_CTRL,
  TARGET_GROUP_STATES
]);
