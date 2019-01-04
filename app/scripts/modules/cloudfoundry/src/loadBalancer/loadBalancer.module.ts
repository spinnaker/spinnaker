import { module } from 'angular';

import { CLOUD_FOUNDRY_LOAD_BALANCER_DETAILS } from './details/cloudFoundryLoadBalancerDetails.module';
import { CLOUD_FOUNDRY_LOAD_BALANCER_TRANSFORMER } from './loadBalancer.transformer';

export const CLOUD_FOUNDRY_LOAD_BALANCER_MODULE = 'spinnaker.cloudfoundry.loadBalancer.module';

module(CLOUD_FOUNDRY_LOAD_BALANCER_MODULE, [
  CLOUD_FOUNDRY_LOAD_BALANCER_DETAILS,
  CLOUD_FOUNDRY_LOAD_BALANCER_TRANSFORMER,
]);
