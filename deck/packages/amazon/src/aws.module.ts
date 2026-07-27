import { CloudProviderRegistry, DeploymentStrategyRegistry, Registry } from '@spinnaker/core';

import { AWSProviderSettings } from './aws.settings';
import './deploymentStrategy/rollingPush.strategy';
import { AmazonFunctionDetails } from './function';
import { CreateLambdaFunction } from './function';
import { AwsFunctionTransformer } from './function';
import './help/amazon.help';
import { AwsImageReader } from './image';
import { AwsInstanceTypeService } from './instance/awsInstanceType.service';
import { AmazonInstanceDetails } from './instance/details/AmazonInstanceDetails';
import { AmazonLoadBalancerClusterContainer } from './loadBalancer';
import { AmazonLoadBalancersTag } from './loadBalancer';
import { AmazonLoadBalancerChoiceModal } from './loadBalancer';
import { AwsLoadBalancerTransformer } from './loadBalancer';
import {
  AmazonLoadBalancerActions,
  amazonLoadBalancerDetailsSections,
  TargetGroupDetails,
  useAmazonLoadBalancerDetails,
} from './loadBalancer';
import amazonLogo from './logo/amazon.logo.svg';
import { registerAwsBakeStage } from './pipeline/stages/bake/awsBakeStage';
import { registerAwsCloneServerGroupStage } from './pipeline/stages/cloneServerGroup/awsCloneServerGroupStage';
import { registerLambdaDeleteStage } from './pipeline/stages/deleteLambda';
import { registerDeployCloudFormationStackStage } from './pipeline/stages/deployCloudFormation/deployCloudFormationStackStage';
import { registerLambdaDeployStage } from './pipeline/stages/deployLambda';
import { registerAwsDestroyAsgStage } from './pipeline/stages/destroyAsg/awsDestroyAsgStage';
import { registerAwsDisableAsgStage } from './pipeline/stages/disableAsg/awsDisableAsgStage';
import { registerAwsDisableClusterStage } from './pipeline/stages/disableCluster/awsDisableClusterStage';
import { registerAwsEnableAsgStage } from './pipeline/stages/enableAsg/awsEnableAsgStage';
import { registerAwsFindAmiStage } from './pipeline/stages/findAmi/awsFindAmiStage';
import { registerAwsFindImageFromTagsStage } from './pipeline/stages/findImageFromTags/awsFindImageFromTagsStage';
import { registerLambdaInvokeStage } from './pipeline/stages/invokeLambda';
import { registerAwsModifyScalingProcessStage } from './pipeline/stages/modifyScalingProcess/modifyScalingProcessStage';
import { registerAwsResizeAsgStage } from './pipeline/stages/resizeAsg/awsResizeAsgStage';
import { registerAwsRollbackClusterStage } from './pipeline/stages/rollbackCluster/awsRollbackClusterStage';
import { registerLambdaRouteStage } from './pipeline/stages/routeLambda';
import { registerAwsScaleDownClusterStage } from './pipeline/stages/scaleDownCluster/awsScaleDownClusterStage';
import { registerAwsShrinkClusterStage } from './pipeline/stages/shrinkCluster/awsShrinkClusterStage';
import { registerAwsTagImageStage } from './pipeline/stages/tagImage/awsTagImageStage';
import { registerLambdaUpdateStage } from './pipeline/stages/updateCodeLambda';
import { AmazonSecurityGroupModal } from './securityGroup/configure/AmazonSecurityGroupModal';
import { AmazonSecurityGroupDetails } from './securityGroup/details/AmazonSecurityGroupDetails';
import { AwsSecurityGroupReader } from './securityGroup/securityGroup.reader';
import { AwsSecurityGroupTransformer } from './securityGroup/securityGroup.transformer';
import { AwsServerGroupCommandBuilder } from './serverGroup/configure/serverGroupCommandBuilder.service';
import { AwsServerGroupConfigurationServiceDelegate } from './serverGroup/configure/serverGroupConfiguration.service';
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
import { AwsServerGroupTransformer } from './serverGroup/serverGroup.transformer';
import './validation/ApplicationNameValidator';

import './logo/aws.logo.less';

export function registerAmazonProvider(): void {
  CloudProviderRegistry.registerProvider('aws', {
    name: 'Amazon',
    adHocInfrastructureWritesEnabled: AWSProviderSettings.adHocInfraWritesEnabled,
    logo: {
      path: amazonLogo,
    },
    applicationProviderFields: [
      {
        field: 'useAmiBlockDeviceMappings',
        label: 'Prefer AMI Block Device Mappings',
        type: 'boolean',
      },
    ],
    image: {
      reader: AwsImageReader,
    },
    serverGroup: {
      transformer: AwsServerGroupTransformer,
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
      commandBuilder: AwsServerGroupCommandBuilder,
      configurationService: AwsServerGroupConfigurationServiceDelegate,
      instanceTypeService: AwsInstanceTypeService,
      scalingActivitiesEnabled: true,
      TargetTrackingChart,
      UpsertStepPolicyModal: AmazonUpsertScalingPolicyModal,
      UpsertTargetTrackingModal: AmazonUpsertTargetTrackingModal,
    },
    instance: {
      instanceTypeService: AwsInstanceTypeService,
      details: AmazonInstanceDetails,
    },
    loadBalancer: {
      transformer: AwsLoadBalancerTransformer,
      CreateLoadBalancerModal: AmazonLoadBalancerChoiceModal,
      useDetailsHook: useAmazonLoadBalancerDetails,
      detailsActions: AmazonLoadBalancerActions,
      detailsSections: amazonLoadBalancerDetailsSections,
      targetGroupDetails: TargetGroupDetails,
      ClusterContainer: AmazonLoadBalancerClusterContainer,
      LoadBalancersTag: AmazonLoadBalancersTag,
    },
    function: {
      details: AmazonFunctionDetails,
      CreateFunctionModal: CreateLambdaFunction,
      setTransformer: AwsFunctionTransformer,
      transformer: AwsFunctionTransformer,
    },
    securityGroup: {
      transformer: AwsSecurityGroupTransformer,
      reader: AwsSecurityGroupReader,
      CreateSecurityGroupModal: AmazonSecurityGroupModal,
      details: AmazonSecurityGroupDetails,
    },
  });
}

function registerPipelineStageOnce(
  stageKey: string,
  registerStage: () => void,
  cloudProvider: string | null = 'aws',
): void {
  const isRegistered = Registry.pipeline.getStageTypes().some((stage) => {
    const registeredStageKey = stage.provides || stage.key;
    const cloudProviderMatches = cloudProvider === null || stage.cloudProvider === cloudProvider;

    return registeredStageKey === stageKey && cloudProviderMatches;
  });

  if (!isRegistered) {
    registerStage();
  }
}

export function registerAmazonPipelineStages(): void {
  registerPipelineStageOnce('bake', registerAwsBakeStage);
  registerPipelineStageOnce('cloneServerGroup', registerAwsCloneServerGroupStage);
  registerPipelineStageOnce('deployCloudFormation', registerDeployCloudFormationStackStage);
  registerPipelineStageOnce('destroyServerGroup', registerAwsDestroyAsgStage);
  registerPipelineStageOnce('disableServerGroup', registerAwsDisableAsgStage);
  registerPipelineStageOnce('disableCluster', registerAwsDisableClusterStage);
  registerPipelineStageOnce('enableServerGroup', registerAwsEnableAsgStage);
  registerPipelineStageOnce('findImage', registerAwsFindAmiStage);
  registerPipelineStageOnce('findImageFromTags', registerAwsFindImageFromTagsStage);
  registerPipelineStageOnce('Aws.LambdaDeleteStage', registerLambdaDeleteStage, null);
  registerPipelineStageOnce('Aws.LambdaDeploymentStage', registerLambdaDeployStage, null);
  registerPipelineStageOnce('Aws.LambdaInvokeStage', registerLambdaInvokeStage, null);
  registerPipelineStageOnce('modifyAwsScalingProcess', registerAwsModifyScalingProcessStage);
  registerPipelineStageOnce('resizeServerGroup', registerAwsResizeAsgStage);
  registerPipelineStageOnce('rollbackCluster', registerAwsRollbackClusterStage);
  registerPipelineStageOnce('Aws.LambdaUpdateCodeStage', registerLambdaUpdateStage, null);
  registerPipelineStageOnce('Aws.LambdaTrafficRoutingStage', registerLambdaRouteStage, null);
  registerPipelineStageOnce('scaleDownCluster', registerAwsScaleDownClusterStage);
  registerPipelineStageOnce('shrinkCluster', registerAwsShrinkClusterStage);
  registerPipelineStageOnce('upsertImageTags', registerAwsTagImageStage);
}

registerAmazonProvider();
registerAmazonPipelineStages();

DeploymentStrategyRegistry.registerProvider('aws', [
  'custom',
  'redblack',
  'rollingpush',
  'rollingredblack',
  'monitored',
]);
