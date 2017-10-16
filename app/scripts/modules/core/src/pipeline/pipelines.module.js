'use strict';

const angular = require('angular');

import { COPY_STAGE_MODAL_CONTROLLER } from './config/copyStage/copyStage.modal.controller';
import { GROUP_STAGE_MODULE } from './config/stages/group/groupStage.module';
import { STAGE_CORE_MODULE } from './config/stages/core/stage.core.module';
import { TRAVIS_STAGE_MODULE } from './config/stages/travis/travisStage.module';
import { UNMATCHED_STAGE_TYPE_STAGE } from './config/stages/unmatchedStageTypeStage/unmatchedStageTypeStage';
import { WEBHOOK_STAGE_MODULE } from './config/stages/webhook/webhookStage.module';

import './pipelines.less';
import 'angular-ui-sortable';

module.exports = angular.module('spinnaker.core.pipeline', [
  'ui.sortable',
  require('./config/pipelineConfig.module').name,
  COPY_STAGE_MODAL_CONTROLLER,
  GROUP_STAGE_MODULE,
  TRAVIS_STAGE_MODULE,
  WEBHOOK_STAGE_MODULE,
  UNMATCHED_STAGE_TYPE_STAGE,
  require('./config/stages/bake/bakeStage.module').name,
  require('./config/stages/checkPreconditions/checkPreconditionsStage.module').name,
  require('./config/stages/cloneServerGroup/cloneServerGroupStage.module').name,
  STAGE_CORE_MODULE,
  require('./config/stages/deploy/deployStage.module').name,
  require('./config/stages/destroyAsg/destroyAsgStage.module').name,
  require('./config/stages/disableAsg/disableAsgStage.module').name,
  require('./config/stages/disableCluster/disableClusterStage.module').name,
  require('./config/stages/enableAsg/enableAsgStage.module').name,
  require('./config/stages/executionWindows/executionWindowsStage.module').name,
  require('./config/stages/findAmi/findAmiStage.module').name,
  require('./config/stages/findImageFromTags/findImageFromTagsStage.module').name,
  require('./config/stages/jenkins/jenkinsStage.module').name,
  require('./config/stages/manualJudgment/manualJudgmentStage.module').name,
  require('./config/stages/tagImage/tagImageStage.module').name,
  require('./config/stages/pipeline/pipelineStage.module').name,
  require('./config/stages/resizeAsg/resizeAsgStage.module').name,
  require('./config/stages/runJob/runJobStage.module').name,
  require('./config/stages/scaleDownCluster/scaleDownClusterStage.module').name,
  require('./config/stages/script/scriptStage.module').name,
  require('./config/stages/shrinkCluster/shrinkClusterStage.module').name,
  require('./config/stages/wait/waitStage.module').name,
  require('./config/stages/waitForParentTasks/waitForParentTasks').name,
  require('./config/stages/createLoadBalancer/createLoadBalancerStage.module').name,
  require('./config/stages/applySourceServerGroupCapacity/applySourceServerGroupCapacityStage.module').name,
  require('./config/preconditions/preconditions.module').name,
  require('./config/preconditions/types/clusterSize/clusterSize.precondition.type.module').name,
  require('./config/preconditions/types/expression/expression.precondition.type.module').name,
]);
