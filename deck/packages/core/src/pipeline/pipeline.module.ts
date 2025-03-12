import { module } from 'angular';
import 'angular-ui-sortable';

import { CORE_PIPELINE_CONFIG_PIPELINECONFIG_MODULE } from './config/pipelineConfig.module';
import { CORE_PIPELINE_CONFIG_PRECONDITIONS_PRECONDITIONS_MODULE } from './config/preconditions/preconditions.module';
import { CORE_PIPELINE_CONFIG_PRECONDITIONS_TYPES_CLUSTERSIZE_CLUSTERSIZE_PRECONDITION_TYPE_MODULE } from './config/preconditions/types/clusterSize/clusterSize.precondition.type.module';
import { CORE_PIPELINE_CONFIG_PRECONDITIONS_TYPES_EXPRESSION_EXPRESSION_PRECONDITION_TYPE_MODULE } from './config/preconditions/types/expression/expression.precondition.type.module';
import { STAGE_STATUS_PRECONDITION } from './config/preconditions/types/stageStatus/stageStatus.precondition.type.module';
import { APPLY_SOURCE_SERVER_GROUP_CAPACITY_STAGE } from './config/stages/applySourceServerGroupCapacity/applySourceServerGroupCapacityStage.module';
import './config/stages/awsCodeBuild/awsCodeBuildStage';
import { CORE_PIPELINE_CONFIG_STAGES_BAKE_BAKESTAGE_MODULE } from './config/stages/bake/bakeStage.module';
import './config/stages/bakeManifest/bakeManifestStage';
import { CHECK_PRECONDITIONS_STAGE_MODULE } from './config/stages/checkPreconditions/checkPreconditionsStage.module';
import { CLONE_SERVER_GROUP_STAGE } from './config/stages/cloneServerGroup/cloneServerGroupStage.module';
import { STAGE_COMMON_MODULE } from './config/stages/common/stage.common.module';
import './config/stages/concourse/concourseStage';
import { CREATE_LOAD_BALANCER_STAGE } from './config/stages/createLoadBalancer/createLoadBalancerStage.module';
import { CORE_PIPELINE_CONFIG_STAGES_DEPLOY_DEPLOYSTAGE_MODULE } from './config/stages/deploy/deployStage.module';
import './config/stages/deployService/deployServiceStage';
import { DESTROY_ASG_STAGE } from './config/stages/destroyAsg/destroyAsgStage';
import './config/stages/destroyService/destroyServiceStage';
import { DISABLE_ASG_STAGE_MODULE } from './config/stages/disableAsg/disableAsgStage.module';
import { DISABLE_CLUSTER_STAGE } from './config/stages/disableCluster/disableClusterStage';
import { ENABLE_ASG_STAGE } from './config/stages/enableAsg/enableAsgStage';
import './config/stages/entityTags/applyEntityTagsStage';
import './config/stages/evaluateVariables/evaluateVariablesStage';
import { EXECUTION_WINDOWS } from './config/stages/executionWindows/executionWindows.module';
import './config/stages/executionWindows/executionWindowsStage';
import { FIND_AMI_STAGE } from './config/stages/findAmi/findAmiStage';
import { FIND_ARTIFACT_FROM_EXECUTION_STAGE } from './config/stages/findArtifactFromExecution/findArtifactFromExecutionStage';
import { CORE_PIPELINE_CONFIG_STAGES_FINDIMAGEFROMTAGS_FINDIMAGEFROMTAGSSTAGE_MODULE } from './config/stages/findImageFromTags/findImageFromTagsStage.module';
import './config/stages/googleCloudBuild/googleCloudBuildStage';
import './config/stages/gremlin/gremlinStage';
import { GROUP_STAGE_MODULE } from './config/stages/group/groupStage.module';
import { CORE_PIPELINE_CONFIG_STAGES_JENKINS_JENKINSSTAGE_MODULE } from './config/stages/jenkins/jenkinsStage.module';
import './config/stages/managed/importDeliveryConfigStage';
import { MANUAL_JUDGMENT_STAGE_MODULE } from './config/stages/manualJudgment/manualJudgmentStage.module';
import { CORE_PIPELINE_CONFIG_STAGES_MONITORPIPELINE_MONITORPIPELINESTAGE_MODULE } from './config/stages/monitorPipeline/monitorPipelineStage.module';
import { EVALUATE_HEALTH_STAGE } from './config/stages/monitoreddeploy/evaluateHealthStage';
import { NOTIFY_DEPLOY_STARTING_STAGE } from './config/stages/monitoreddeploy/notifyDeployStartingStage';
import { CORE_PIPELINE_CONFIG_STAGES_PIPELINE_PIPELINESTAGE_MODULE } from './config/stages/pipeline/pipelineStage.module';
import { PRECONFIGUREDJOB_STAGE_MODULE } from './config/stages/preconfiguredJob/preconfiguredJobStage.module';
import { RESIZE_ASG_STAGE } from './config/stages/resizeAsg/resizeAsgStage';
import { ROLLBACK_CLUSTER_STAGE } from './config/stages/rollbackCluster/rollbackClusterStage';
import { CORE_PIPELINE_CONFIG_STAGES_RUNJOB_RUNJOBSTAGE_MODULE } from './config/stages/runJob/runJobStage.module';
import './config/stages/savePipelines/savePipelinesStage';
import { SCALE_DOWN_CLUSTER_STAGE } from './config/stages/scaleDownCluster/scaleDownClusterStage';
import { SCRIPT_STAGE } from './config/stages/script/scriptStage';
import './config/stages/shareService/shareServiceStage';
import { SHRINK_CLUSTER_STAGE } from './config/stages/shrinkCluster/shrinkClusterStage';
import { CORE_PIPELINE_CONFIG_STAGES_TAGIMAGE_TAGIMAGESTAGE_MODULE } from './config/stages/tagImage/tagImageStage.module';
import { TRAVIS_STAGE_MODULE } from './config/stages/travis/travisStage.module';
import { UNMATCHED_STAGE_TYPE_STAGE } from './config/stages/unmatchedStageTypeStage/unmatchedStageTypeStage';
import './config/stages/unshareService/unshareServiceStage';
import './config/stages/wait/waitStage';
import './config/stages/waitForCondition/waitForConditionStage';
import { CORE_PIPELINE_CONFIG_STAGES_WAITFORPARENTTASKS_WAITFORPARENTTASKS } from './config/stages/waitForParentTasks/waitForParentTasks';
import { WEBHOOK_STAGE_MODULE } from './config/stages/webhook/webhookStage.module';
import { WERCKER_STAGE_MODULE } from './config/stages/wercker/werckerStage.module';
import { PIPELINE_TEMPLATES_V2_STATES_CONFIG } from './config/templates/v2/pipelineTemplateV2.states';
import { EXECUTION_DETAILS_SECTION_NAV } from './details/executionDetailsSectionNav.component';
import { STAGE_FAILURE_MESSAGE_COMPONENT } from './details/stageFailureMessage.component';
import { STAGE_SUMMARY_COMPONENT } from './details/stageSummary.component';
import { STEP_EXECUTION_DETAILS_COMPONENT } from './details/stepExecutionDetails.component';
import { BUILD_DISPLAY_NAME_FILTER } from './executionBuild/buildDisplayName.filter';
import { CORE_PIPELINE_PIPELINE_DATASOURCE } from './pipeline.dataSource';
import { PIPELINE_STATES } from './pipeline.states';
import { ARTIFACT_LIST } from './status/artifactList.component';

import './pipeline.less';

export const PIPELINE_MODULE = 'spinnaker.core.pipeline';

module(PIPELINE_MODULE, [
  'ui.sortable',
  EXECUTION_DETAILS_SECTION_NAV,

  BUILD_DISPLAY_NAME_FILTER,

  STAGE_FAILURE_MESSAGE_COMPONENT,
  STEP_EXECUTION_DETAILS_COMPONENT,
  STAGE_SUMMARY_COMPONENT,

  CORE_PIPELINE_PIPELINE_DATASOURCE,
  ARTIFACT_LIST,
  PIPELINE_STATES,
  CORE_PIPELINE_CONFIG_PIPELINECONFIG_MODULE,
  GROUP_STAGE_MODULE,
  TRAVIS_STAGE_MODULE,
  WERCKER_STAGE_MODULE,
  PRECONFIGUREDJOB_STAGE_MODULE,
  WEBHOOK_STAGE_MODULE,
  UNMATCHED_STAGE_TYPE_STAGE,
  CORE_PIPELINE_CONFIG_STAGES_BAKE_BAKESTAGE_MODULE,
  CHECK_PRECONDITIONS_STAGE_MODULE,
  CLONE_SERVER_GROUP_STAGE,
  STAGE_COMMON_MODULE,
  CORE_PIPELINE_CONFIG_STAGES_DEPLOY_DEPLOYSTAGE_MODULE,
  DESTROY_ASG_STAGE,
  DISABLE_ASG_STAGE_MODULE,
  DISABLE_CLUSTER_STAGE,
  ROLLBACK_CLUSTER_STAGE,
  ENABLE_ASG_STAGE,
  EXECUTION_WINDOWS,
  FIND_AMI_STAGE,
  FIND_ARTIFACT_FROM_EXECUTION_STAGE,
  CORE_PIPELINE_CONFIG_STAGES_FINDIMAGEFROMTAGS_FINDIMAGEFROMTAGSSTAGE_MODULE,
  CORE_PIPELINE_CONFIG_STAGES_JENKINS_JENKINSSTAGE_MODULE,
  MANUAL_JUDGMENT_STAGE_MODULE,
  CORE_PIPELINE_CONFIG_STAGES_TAGIMAGE_TAGIMAGESTAGE_MODULE,
  CORE_PIPELINE_CONFIG_STAGES_PIPELINE_PIPELINESTAGE_MODULE,
  CORE_PIPELINE_CONFIG_STAGES_MONITORPIPELINE_MONITORPIPELINESTAGE_MODULE,
  RESIZE_ASG_STAGE,
  CORE_PIPELINE_CONFIG_STAGES_RUNJOB_RUNJOBSTAGE_MODULE,
  SCALE_DOWN_CLUSTER_STAGE,
  SCRIPT_STAGE,
  SHRINK_CLUSTER_STAGE,
  CORE_PIPELINE_CONFIG_STAGES_WAITFORPARENTTASKS_WAITFORPARENTTASKS,
  CREATE_LOAD_BALANCER_STAGE,
  APPLY_SOURCE_SERVER_GROUP_CAPACITY_STAGE,
  EVALUATE_HEALTH_STAGE,
  NOTIFY_DEPLOY_STARTING_STAGE,
  CORE_PIPELINE_CONFIG_PRECONDITIONS_PRECONDITIONS_MODULE,
  CORE_PIPELINE_CONFIG_PRECONDITIONS_TYPES_CLUSTERSIZE_CLUSTERSIZE_PRECONDITION_TYPE_MODULE,
  CORE_PIPELINE_CONFIG_PRECONDITIONS_TYPES_EXPRESSION_EXPRESSION_PRECONDITION_TYPE_MODULE,
  PIPELINE_TEMPLATES_V2_STATES_CONFIG,
  STAGE_STATUS_PRECONDITION,
]);
