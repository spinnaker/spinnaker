import { module } from 'angular';

import { APPENGINE_DESTROY_ASG_STAGE } from './stages/destroyAsg/appengineDestroyAsgStage';
import { APPENGINE_DISABLE_ASG_STAGE } from './stages/disableAsg/appengineDisableAsgStage';
import { APPENGINE_EDIT_LOAD_BALANCER_STAGE } from './stages/editLoadBalancer/appengineEditLoadBalancerStage';
import { APPENGINE_ENABLE_ASG_STAGE } from './stages/enableAsg/appengineEnableAsgStage';
import { APPENGINE_SHRINK_CLUSTER_STAGE } from './stages/shrinkCluster/appengineShrinkClusterStage';
import { APPENGINE_START_SERVER_GROUP_STAGE } from './stages/startServerGroup/appengineStartServerGroupStage';
import { APPENGINE_STOP_SERVER_GROUP_STAGE } from './stages/stopServerGroup/appengineStopServerGroupStage';

export const APPENGINE_PIPELINE_MODULE = 'spinnaker.appengine.pipeline.module';
module(APPENGINE_PIPELINE_MODULE, [
  APPENGINE_DESTROY_ASG_STAGE,
  APPENGINE_DISABLE_ASG_STAGE,
  APPENGINE_EDIT_LOAD_BALANCER_STAGE,
  APPENGINE_ENABLE_ASG_STAGE,
  APPENGINE_SHRINK_CLUSTER_STAGE,
  APPENGINE_START_SERVER_GROUP_STAGE,
  APPENGINE_STOP_SERVER_GROUP_STAGE,
]);
