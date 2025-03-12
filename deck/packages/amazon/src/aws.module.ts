import { module } from 'angular';

import { CloudProviderRegistry, DeploymentStrategyRegistry } from '@spinnaker/core';

import { AWSProviderSettings } from './aws.settings';
import { COMMON_MODULE } from './common/common.module';
import './deploymentStrategy/rollingPush.strategy';
import { AmazonFunctionDetails } from './function';
import { CreateLambdaFunction } from './function';
import { AwsFunctionTransformer } from './function';
import { AWS_FUNCTION_MODULE } from './function/function.module';
import './help/amazon.help';
import { AwsImageReader } from './image';
import { AMAZON_INSTANCE_AWSINSTANCETYPE_SERVICE } from './instance/awsInstanceType.service';
import { AMAZON_INSTANCE_INFORMATION_COMPONENT } from './instance/details/amazonInstanceInformation.component';
import { AMAZON_INSTANCE_DETAILS_INSTANCE_DETAILS_CONTROLLER } from './instance/details/instance.details.controller';
import { INSTANCE_DNS_COMPONENT } from './instance/details/instanceDns.component';
import { INSTANCE_SECURITY_GROUPS_COMPONENT } from './instance/details/instanceSecurityGroups.component';
import { INSTANCE_STATUS_COMPONENT } from './instance/details/instanceStatus.component';
import { INSTANCE_TAGS_COMPONENT } from './instance/details/instanceTags.component';
import { AmazonLoadBalancerClusterContainer } from './loadBalancer';
import { AmazonLoadBalancersTag } from './loadBalancer';
import { AmazonLoadBalancerChoiceModal } from './loadBalancer';
import { AwsLoadBalancerTransformer } from './loadBalancer';
import { AWS_LOAD_BALANCER_MODULE } from './loadBalancer/loadBalancer.module';
import amazonLogo from './logo/amazon.logo.svg';
import { AMAZON_PIPELINE_STAGES_BAKE_AWSBAKESTAGE } from './pipeline/stages/bake/awsBakeStage';
import { AMAZON_PIPELINE_STAGES_CLONESERVERGROUP_AWSCLONESERVERGROUPSTAGE } from './pipeline/stages/cloneServerGroup/awsCloneServerGroupStage';
import { AMAZON_PIPELINE_STAGES_LAMBDA_DELETE } from './pipeline/stages/deleteLambda';
import { CLOUD_FORMATION_CHANGE_SET_INFO } from './pipeline/stages/deployCloudFormation/CloudFormationChangeSetInfo';
import { CLOUDFORMATION_TEMPLATE_ENTRY } from './pipeline/stages/deployCloudFormation/cloudFormationTemplateEntry.component';
import { DEPLOY_CLOUDFORMATION_STACK_STAGE } from './pipeline/stages/deployCloudFormation/deployCloudFormationStackStage';
import { AWS_EVALUATE_CLOUD_FORMATION_CHANGE_SET_EXECUTION_SERVICE } from './pipeline/stages/deployCloudFormation/evaluateCloudFormationChangeSetExecution.service';
import { AMAZON_PIPELINE_STAGES_LAMBDA_DEPLOY } from './pipeline/stages/deployLambda';
import { AMAZON_PIPELINE_STAGES_DESTROYASG_AWSDESTROYASGSTAGE } from './pipeline/stages/destroyAsg/awsDestroyAsgStage';
import { AMAZON_PIPELINE_STAGES_DISABLEASG_AWSDISABLEASGSTAGE } from './pipeline/stages/disableAsg/awsDisableAsgStage';
import { AMAZON_PIPELINE_STAGES_DISABLECLUSTER_AWSDISABLECLUSTERSTAGE } from './pipeline/stages/disableCluster/awsDisableClusterStage';
import { AMAZON_PIPELINE_STAGES_ENABLEASG_AWSENABLEASGSTAGE } from './pipeline/stages/enableAsg/awsEnableAsgStage';
import { AMAZON_PIPELINE_STAGES_FINDAMI_AWSFINDAMISTAGE } from './pipeline/stages/findAmi/awsFindAmiStage';
import { AMAZON_PIPELINE_STAGES_FINDIMAGEFROMTAGS_AWSFINDIMAGEFROMTAGSSTAGE } from './pipeline/stages/findImageFromTags/awsFindImageFromTagsStage';
import { AMAZON_PIPELINE_STAGES_LAMBDA_INVOKE } from './pipeline/stages/invokeLambda';
import { AMAZON_PIPELINE_STAGES_MODIFYSCALINGPROCESS_MODIFYSCALINGPROCESSSTAGE } from './pipeline/stages/modifyScalingProcess/modifyScalingProcessStage';
import { AMAZON_PIPELINE_STAGES_RESIZEASG_AWSRESIZEASGSTAGE } from './pipeline/stages/resizeAsg/awsResizeAsgStage';
import { AMAZON_PIPELINE_STAGES_ROLLBACKCLUSTER_AWSROLLBACKCLUSTERSTAGE } from './pipeline/stages/rollbackCluster/awsRollbackClusterStage';
import { AMAZON_PIPELINE_STAGES_LAMBDA_ROUTE } from './pipeline/stages/routeLambda';
import { AMAZON_PIPELINE_STAGES_SCALEDOWNCLUSTER_AWSSCALEDOWNCLUSTERSTAGE } from './pipeline/stages/scaleDownCluster/awsScaleDownClusterStage';
import { AMAZON_PIPELINE_STAGES_SHRINKCLUSTER_AWSSHRINKCLUSTERSTAGE } from './pipeline/stages/shrinkCluster/awsShrinkClusterStage';
import { AMAZON_PIPELINE_STAGES_TAGIMAGE_AWSTAGIMAGESTAGE } from './pipeline/stages/tagImage/awsTagImageStage';
import { AMAZON_PIPELINE_STAGES_LAMBDA_UPDATE } from './pipeline/stages/updateCodeLambda';
import { AWS_REACT_MODULE } from './reactShims/aws.react.module';
import { AMAZON_SEARCH_SEARCHRESULTFORMATTER } from './search/searchResultFormatter';
import { AWS_SECURITY_GROUP_MODULE } from './securityGroup/securityGroup.module';
import { AmazonCloneServerGroupModal } from './serverGroup/configure/wizard/AmazonCloneServerGroupModal';
import { AmazonServerGroupActions } from './serverGroup/details/AmazonServerGroupActions';
import { amazonServerGroupDetailsGetter } from './serverGroup/details/amazonServerGroupDetailsGetter';
import {
  AmazonUpsertScalingPolicyModal,
  AmazonUpsertTargetTrackingModal,
  TargetTrackingChart,
} from './serverGroup/details/scalingPolicy';
import {
  AdvancedSettingsDetailsSection,
  AmazonCapacityDetailsSection,
  AmazonInfoDetailsSection,
  HealthDetailsSection,
  InstancesDistributionDetailsSection,
  LaunchConfigDetailsSection,
  LaunchTemplateDetailsSection,
  LogsDetailsSection,
  PackageDetailsSection,
  ScalingPoliciesDetailsSection,
  ScalingProcessesDetailsSection,
  ScheduledActionsDetailsSection,
  SecurityGroupsDetailsSection,
  TagsDetailsSection,
} from './serverGroup/details/sections';
import { SERVER_GROUP_DETAILS_MODULE } from './serverGroup/details/serverGroupDetails.module';
import { AWS_SERVER_GROUP_TRANSFORMER } from './serverGroup/serverGroup.transformer';
import { SUBNET_RENDERER } from './subnet/subnet.renderer';
import './validation/ApplicationNameValidator';
import { VPC_MODULE } from './vpc/vpc.module';

import './logo/aws.logo.less';

export const AMAZON_MODULE = 'spinnaker.amazon';
module(AMAZON_MODULE, [
  AWS_REACT_MODULE,
  AMAZON_PIPELINE_STAGES_BAKE_AWSBAKESTAGE,
  AMAZON_PIPELINE_STAGES_CLONESERVERGROUP_AWSCLONESERVERGROUPSTAGE,
  AMAZON_PIPELINE_STAGES_DESTROYASG_AWSDESTROYASGSTAGE,
  AMAZON_PIPELINE_STAGES_DISABLEASG_AWSDISABLEASGSTAGE,
  AMAZON_PIPELINE_STAGES_DISABLECLUSTER_AWSDISABLECLUSTERSTAGE,
  AMAZON_PIPELINE_STAGES_ROLLBACKCLUSTER_AWSROLLBACKCLUSTERSTAGE,
  AMAZON_PIPELINE_STAGES_ENABLEASG_AWSENABLEASGSTAGE,
  AMAZON_PIPELINE_STAGES_FINDAMI_AWSFINDAMISTAGE,
  AMAZON_PIPELINE_STAGES_FINDIMAGEFROMTAGS_AWSFINDIMAGEFROMTAGSSTAGE,
  AMAZON_PIPELINE_STAGES_MODIFYSCALINGPROCESS_MODIFYSCALINGPROCESSSTAGE,
  AMAZON_PIPELINE_STAGES_RESIZEASG_AWSRESIZEASGSTAGE,
  AMAZON_PIPELINE_STAGES_SCALEDOWNCLUSTER_AWSSCALEDOWNCLUSTERSTAGE,
  AMAZON_PIPELINE_STAGES_SHRINKCLUSTER_AWSSHRINKCLUSTERSTAGE,
  AMAZON_PIPELINE_STAGES_TAGIMAGE_AWSTAGIMAGESTAGE,
  SERVER_GROUP_DETAILS_MODULE,
  COMMON_MODULE,
  AWS_SERVER_GROUP_TRANSFORMER,
  AMAZON_INSTANCE_AWSINSTANCETYPE_SERVICE,
  AWS_LOAD_BALANCER_MODULE,
  AWS_FUNCTION_MODULE,
  AMAZON_INSTANCE_DETAILS_INSTANCE_DETAILS_CONTROLLER,
  AWS_SECURITY_GROUP_MODULE,
  SUBNET_RENDERER,
  VPC_MODULE,
  AMAZON_SEARCH_SEARCHRESULTFORMATTER,
  DEPLOY_CLOUDFORMATION_STACK_STAGE,
  CLOUDFORMATION_TEMPLATE_ENTRY,
  CLOUD_FORMATION_CHANGE_SET_INFO,
  AWS_EVALUATE_CLOUD_FORMATION_CHANGE_SET_EXECUTION_SERVICE,
  INSTANCE_STATUS_COMPONENT,
  INSTANCE_TAGS_COMPONENT,
  INSTANCE_SECURITY_GROUPS_COMPONENT,
  INSTANCE_DNS_COMPONENT,
  AMAZON_INSTANCE_INFORMATION_COMPONENT,
  AMAZON_PIPELINE_STAGES_LAMBDA_DELETE,
  AMAZON_PIPELINE_STAGES_LAMBDA_DEPLOY,
  AMAZON_PIPELINE_STAGES_LAMBDA_INVOKE,
  AMAZON_PIPELINE_STAGES_LAMBDA_UPDATE,
  AMAZON_PIPELINE_STAGES_LAMBDA_ROUTE,
]).config(() => {
  CloudProviderRegistry.registerProvider('aws', {
    name: 'Amazon',
    adHocInfrastructureWritesEnabled: AWSProviderSettings.adHocInfraWritesEnabled,
    logo: {
      path: amazonLogo,
    },
    image: {
      reader: AwsImageReader,
    },
    serverGroup: {
      transformer: 'awsServerGroupTransformer',
      detailsActions: AmazonServerGroupActions,
      detailsGetter: amazonServerGroupDetailsGetter,
      detailsSections: [
        AmazonInfoDetailsSection,
        AmazonCapacityDetailsSection,
        HealthDetailsSection,
        InstancesDistributionDetailsSection,
        LaunchConfigDetailsSection,
        LaunchTemplateDetailsSection,
        SecurityGroupsDetailsSection,
        ScalingProcessesDetailsSection,
        ScalingPoliciesDetailsSection,
        ScheduledActionsDetailsSection,
        TagsDetailsSection,
        PackageDetailsSection,
        AdvancedSettingsDetailsSection,
        LogsDetailsSection,
      ],
      CloneServerGroupModal: AmazonCloneServerGroupModal,
      commandBuilder: 'awsServerGroupCommandBuilder',
      configurationService: 'awsServerGroupConfigurationService',
      scalingActivitiesEnabled: true,
      TargetTrackingChart,
      UpsertStepPolicyModal: AmazonUpsertScalingPolicyModal,
      UpsertTargetTrackingModal: AmazonUpsertTargetTrackingModal,
    },
    instance: {
      instanceTypeService: 'awsInstanceTypeService',
      detailsTemplateUrl: require('./instance/details/instanceDetails.html'),
      detailsController: 'awsInstanceDetailsCtrl',
    },
    loadBalancer: {
      transformer: AwsLoadBalancerTransformer,
      detailsTemplateUrl: require('./loadBalancer/details/loadBalancerDetails.html'),
      detailsController: 'awsLoadBalancerDetailsCtrl',
      CreateLoadBalancerModal: AmazonLoadBalancerChoiceModal,
      targetGroupDetailsTemplateUrl: require('./loadBalancer/details/targetGroupDetails.html'),
      targetGroupDetailsController: 'awsTargetGroupDetailsCtrl',
      ClusterContainer: AmazonLoadBalancerClusterContainer,
      LoadBalancersTag: AmazonLoadBalancersTag,
    },
    function: {
      details: AmazonFunctionDetails,
      CreateFunctionModal: CreateLambdaFunction,
      transformer: AwsFunctionTransformer,
    },
    securityGroup: {
      transformer: 'awsSecurityGroupTransformer',
      reader: 'awsSecurityGroupReader',
      detailsTemplateUrl: require('./securityGroup/details/securityGroupDetail.html'),
      detailsController: 'awsSecurityGroupDetailsCtrl',
      createSecurityGroupTemplateUrl: require('./securityGroup/configure/createSecurityGroup.html'),
      createSecurityGroupController: 'awsCreateSecurityGroupCtrl',
    },
    subnet: {
      renderer: 'awsSubnetRenderer',
    },
    search: {
      resultFormatter: 'awsSearchResultFormatter',
    },
    applicationProviderFields: {
      templateUrl: require('./applicationProviderFields/awsFields.html'),
    },
  });
});

DeploymentStrategyRegistry.registerProvider('aws', [
  'custom',
  'redblack',
  'rollingpush',
  'rollingredblack',
  'monitored',
]);
