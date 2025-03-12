import { module } from 'angular';

import { CLOUDRUN_EDIT_LOAD_BALANCER_STAGE } from './stages/editLoadBalancer/cloudrunEditLoadBalancerStage';

export const CLOUDRUN_PIPELINE_MODULE = 'spinnaker.cloudrun.pipeline.module';
module(CLOUDRUN_PIPELINE_MODULE, [CLOUDRUN_EDIT_LOAD_BALANCER_STAGE]);
