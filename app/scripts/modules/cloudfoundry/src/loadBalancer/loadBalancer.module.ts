import { module } from 'angular';

import { CLOUD_FOUNDRY_LOAD_BALANCER_DETAILS_CTRL } from './details/loadBalancer.details.controller';
import { CLOUD_FOUNDRY_LOAD_BALANCER_TRANSFORMER } from './loadBalancer.transformer';

export const CLOUD_FOUNDRY_LOAD_BALANCER_MODULE = 'spinnaker.cloudfoundry.loadBalancer.module';

module(CLOUD_FOUNDRY_LOAD_BALANCER_MODULE, [
  CLOUD_FOUNDRY_LOAD_BALANCER_DETAILS_CTRL,
  CLOUD_FOUNDRY_LOAD_BALANCER_TRANSFORMER,
]);
