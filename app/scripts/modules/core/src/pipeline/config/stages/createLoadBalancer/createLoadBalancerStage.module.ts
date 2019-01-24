import { module } from 'angular';

import { CREATE_LOAD_BALANCER_EXECUTION_DETAILS_CTRL } from './createLoadBalancerExecutionDetails.controller';

export const CREATE_LOAD_BALANCER_STAGE = 'spinnaker.core.pipeline.stage.createLoadBalancer';

module(CREATE_LOAD_BALANCER_STAGE, [
  require('./createLoadBalancerStage').name,
  CREATE_LOAD_BALANCER_EXECUTION_DETAILS_CTRL,
]);
