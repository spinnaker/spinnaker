'use strict';

const angular = require('angular');

import { COPY_STAGE_MODAL_CONTROLLER } from './config/copyStage/copyStage.modal.controller';
import { TRAVIS_STAGE_MODULE } from './config/stages/travis/travisStage.module';
import { UNMATCHED_STAGE_TYPE_STAGE } from './config/stages/unmatchedStageTypeStage/unmatchedStageTypeStage';
import { WEBHOOK_STAGE_MODULE } from './config/stages/webhook/webhookStage.module';

import './pipelines.less';
import 'angular-ui-sortable';

module.exports = angular.module('spinnaker.core.pipeline', [
  'ui.sortable',
  require('./config/pipelineConfig.module'),
  COPY_STAGE_MODAL_CONTROLLER,
  TRAVIS_STAGE_MODULE,
  WEBHOOK_STAGE_MODULE,
  UNMATCHED_STAGE_TYPE_STAGE,
  require('./config/stages/bake/bakeStage.module'),
  require('./config/stages/checkPreconditions/checkPreconditionsStage.module'),
  require('./config/stages/cloneServerGroup/cloneServerGroupStage.module'),
  require('./config/stages/core/stage.core.module'),
  require('./config/stages/deploy/deployStage.module'),
  require('./config/stages/destroyAsg/destroyAsgStage.module'),
  require('./config/stages/disableAsg/disableAsgStage.module'),
  require('./config/stages/disableCluster/disableClusterStage.module'),
  require('./config/stages/enableAsg/enableAsgStage.module'),
  require('./config/stages/executionWindows/executionWindowsStage.module'),
  require('./config/stages/findAmi/findAmiStage.module'),
  require('./config/stages/findImageFromTags/findImageFromTagsStage.module'),
  require('./config/stages/jenkins/jenkinsStage.module'),
  require('./config/stages/manualJudgment/manualJudgmentStage.module'),
  require('./config/stages/tagImage/tagImageStage.module'),
  require('./config/stages/pipeline/pipelineStage.module'),
  require('./config/stages/resizeAsg/resizeAsgStage.module'),
  require('./config/stages/runJob/runJobStage.module'),
  require('./config/stages/scaleDownCluster/scaleDownClusterStage.module'),
  require('./config/stages/script/scriptStage.module'),
  require('./config/stages/shrinkCluster/shrinkClusterStage.module'),
  require('./config/stages/wait/waitStage.module'),
  require('./config/stages/waitForParentTasks/waitForParentTasks'),
  require('./config/stages/createLoadBalancer/createLoadBalancerStage.module'),
  require('./config/stages/applySourceServerGroupCapacity/applySourceServerGroupCapacityStage.module'),
  require('./config/preconditions/preconditions.module'),
  require('./config/preconditions/types/clusterSize/clusterSize.precondition.type.module'),
  require('./config/preconditions/types/expression/expression.precondition.type.module'),
]);
