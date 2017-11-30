import { module } from 'angular';

import { APPLY_SOURCE_SERVER_GROUP_CAPACITY_STAGE } from './config/stages/applySourceServerGroupCapacity/applySourceServerGroupCapacityStage.module';
import { CHECK_PRECONDITIONS_STAGE_MODULE } from './config/stages/checkPreconditions/checkPreconditionsStage.module';
import { CLONE_SERVER_GROUP_STAGE } from './config/stages/cloneServerGroup/cloneServerGroupStage.module';
import { COPY_STAGE_MODAL_CONTROLLER } from './config/copyStage/copyStage.modal.controller';
import { CREATE_LOAD_BALANCER_STAGE } from './config/stages/createLoadBalancer/createLoadBalancerStage.module';
import { DESTROY_ASG_STAGE } from './config/stages/destroyAsg/destroyAsgStage';
import { DISABLE_ASG_STAGE_MODULE } from './config/stages/disableAsg/disableAsgStage.module';
import { DISABLE_CLUSTER_STAGE } from './config/stages/disableCluster/disableClusterStage';
import { ENABLE_ASG_STAGE } from './config/stages/enableAsg/enableAsgStage';
import { EXECUTION_WINDOWS_STAGE_MODULE } from './config/stages/executionWindows/executionWindowsStage.module';
import { FIND_AMI_STAGE } from './config/stages/findAmi/findAmiStage';
import { GROUP_STAGE_MODULE } from './config/stages/group/groupStage.module';
import { MANUAL_JUDGMENT_STAGE_MODULE } from './config/stages/manualJudgment/manualJudgmentStage.module';
import { RESIZE_ASG_STAGE } from './config/stages/resizeAsg/resizeAsgStage';
import { SCALE_DOWN_CLUSTER_STAGE } from './config/stages/scaleDownCluster/scaleDownClusterStage';
import { SCRIPT_STAGE } from './config/stages/script/scriptStage';
import { SHRINK_CLUSTER_STAGE } from './config/stages/shrinkCluster/shrinkClusterStage';
import { STAGE_CORE_MODULE } from './config/stages/core/stage.core.module';
import { TRAVIS_STAGE_MODULE } from './config/stages/travis/travisStage.module';
import { UNMATCHED_STAGE_TYPE_STAGE } from './config/stages/unmatchedStageTypeStage/unmatchedStageTypeStage';
import { WAIT_STAGE } from './config/stages/wait/waitStage';
import { WEBHOOK_STAGE_MODULE } from './config/stages/webhook/webhookStage.module';
import { PIPELINE_STATES } from './pipeline.states';
import { BUILD_DISPLAY_NAME_FILTER } from './executionBuild/buildDisplayName.filter';
import { EXECUTION_DETAILS_SECTION_NAV } from './details/executionDetailsSectionNav.component';
import { EXECUTION_FILTER_SERVICE } from 'core/pipeline/filter/executionFilter.service';
import { STAGE_FAILURE_MESSAGE_COMPONENT } from './details/stageFailureMessage.component';
import { STEP_EXECUTION_DETAILS_COMPONENT } from './details/stepExecutionDetails.component';
import { STAGE_SUMMARY_COMPONENT } from './details/stageSummary.component';

import './pipeline.less';
import 'angular-ui-sortable';

export const PIPELINE_MODULE = 'spinnaker.core.pipeline';

module(PIPELINE_MODULE, [
  'ui.sortable',
  EXECUTION_DETAILS_SECTION_NAV,
  EXECUTION_FILTER_SERVICE,

  BUILD_DISPLAY_NAME_FILTER,

  require('./manualExecution/manualPipelineExecution.controller.js').name,

  STAGE_FAILURE_MESSAGE_COMPONENT,
  STEP_EXECUTION_DETAILS_COMPONENT,
  STAGE_SUMMARY_COMPONENT,

  require('../utils/appendTransform.js').name,

  require('./pipeline.dataSource').name,
  PIPELINE_STATES,
  require('./config/pipelineConfig.module').name,
  COPY_STAGE_MODAL_CONTROLLER,
  GROUP_STAGE_MODULE,
  TRAVIS_STAGE_MODULE,
  WEBHOOK_STAGE_MODULE,
  UNMATCHED_STAGE_TYPE_STAGE,
  require('./config/stages/bake/bakeStage.module').name,
  CHECK_PRECONDITIONS_STAGE_MODULE,
  CLONE_SERVER_GROUP_STAGE,
  STAGE_CORE_MODULE,
  require('./config/stages/deploy/deployStage.module').name,
  DESTROY_ASG_STAGE,
  DISABLE_ASG_STAGE_MODULE,
  DISABLE_CLUSTER_STAGE,
  ENABLE_ASG_STAGE,
  EXECUTION_WINDOWS_STAGE_MODULE,
  FIND_AMI_STAGE,
  require('./config/stages/findImageFromTags/findImageFromTagsStage.module').name,
  require('./config/stages/jenkins/jenkinsStage.module').name,
  MANUAL_JUDGMENT_STAGE_MODULE,
  require('./config/stages/tagImage/tagImageStage.module').name,
  require('./config/stages/pipeline/pipelineStage.module').name,
  RESIZE_ASG_STAGE,
  require('./config/stages/runJob/runJobStage.module').name,
  SCALE_DOWN_CLUSTER_STAGE,
  SCRIPT_STAGE,
  SHRINK_CLUSTER_STAGE,
  WAIT_STAGE,
  require('./config/stages/waitForParentTasks/waitForParentTasks').name,
  CREATE_LOAD_BALANCER_STAGE,
  APPLY_SOURCE_SERVER_GROUP_CAPACITY_STAGE,
  require('./config/preconditions/preconditions.module').name,
  require('./config/preconditions/types/clusterSize/clusterSize.precondition.type.module').name,
  require('./config/preconditions/types/expression/expression.precondition.type.module').name,
]);
