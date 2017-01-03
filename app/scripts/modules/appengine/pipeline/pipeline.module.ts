import {module} from 'angular';

import {APPENGINE_DESTROY_ASG_STAGE} from './stages/destroyAsg/appengineDestroyAsgStage';
import {APPENGINE_DISABLE_ASG_STAGE} from './stages/disableAsg/appengineDisableAsgStage';
import {APPENGINE_ENABLE_ASG_STAGE} from './stages/enableAsg/appengineEnableAsgStage';

export const APPENGINE_PIPELINE_MODULE = 'spinnaker.appengine.pipeline.module';

module(APPENGINE_PIPELINE_MODULE, [
  APPENGINE_DESTROY_ASG_STAGE,
  APPENGINE_DISABLE_ASG_STAGE,
  APPENGINE_ENABLE_ASG_STAGE,
]);
