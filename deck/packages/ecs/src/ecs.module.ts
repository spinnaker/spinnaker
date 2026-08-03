import { AWSProviderSettings } from '@spinnaker/amazon';
import { CloudProviderRegistry, DeploymentStrategyRegistry } from '@spinnaker/core';

import './ecs.help';
import './ecs.settings';
import { EcsInstanceDetails } from './instance/details/EcsInstanceDetails';
import { EcsLoadBalancerClusterContainer } from './loadBalancer/EcsLoadBalancerClusterContainer';
import { EcsLoadBalancerDetails } from './loadBalancer/details/loadBalancerDetails';
import { EcsTargetGroupDetails } from './loadBalancer/details/targetGroupDetails';
import { EcsLoadBalancerTransformer } from './loadBalancer/loadBalancer.transformer';
import './loadBalancer/targetGroup.states';
import ecsLogo from './logo/ecs.logo.svg';
import { registerEcsCloneServerGroupStage } from './pipeline/stages/cloneServerGroup/ecsCloneServerGroupStage';
import { registerEcsDestroyServerGroupStage } from './pipeline/stages/destroyAsg/ecsDestroyAsgStage';
import { registerEcsDisableServerGroupStage } from './pipeline/stages/disableAsg/ecsDisableAsgStage';
import { registerEcsDisableClusterStage } from './pipeline/stages/disableCluster/ecsDisableClusterStage';
import { registerEcsEnableServerGroupStage } from './pipeline/stages/enableAsg/ecsEnableAsgStage';
import { registerEcsFindImageFromTagsStage } from './pipeline/stages/findImageFromTags/ecsFindImageFromTagStage';
import { registerEcsResizeServerGroupStage } from './pipeline/stages/resizeAsg/ecsResizeAsgStage';
import { registerEcsScaleDownClusterStage } from './pipeline/stages/scaleDownCluster/ecsScaleDownClusterStage';
import { registerEcsShrinkClusterStage } from './pipeline/stages/shrinkCluster/ecsShrinkClusterStage';
import { EcsSecurityGroupDetails } from './securityGroup/details/EcsSecurityGroupDetails';
import { EcsSecurityGroupReader } from './securityGroup/securityGroup.reader';
import { EcsSecurityGroupTransformer } from './securityGroup/securityGroup.transformer';
import { EcsServerGroupCommandBuilder } from './serverGroup/configure/serverGroupCommandBuilder.service';
import { EcsCloneServerGroupModal } from './serverGroup/configure/wizard/EcsCloneServerGroupModal';
import { EcsServerGroupActions } from './serverGroup/details/EcsServerGroupActions';
import { ecsServerGroupDetailsGetter } from './serverGroup/details/ecsServerGroupDetailsGetter';
import {
  EcsBuildInfoSection,
  EcsCapacitySection,
  EcsEnvironmentVariablesSection,
  EcsFirewallsSection,
  EcsHealthSection,
  EcsScalingPoliciesSection,
  EcsTaskDefinitionSection,
} from './serverGroup/details/sections/EcsServerGroupDetailsSections';
import { EcsServerGroupEventsSection } from './serverGroup/details/sections/EcsServerGroupEventsSection';
import { EcsServerGroupInformationSection } from './serverGroup/details/sections/EcsServerGroupInformationSection';
import { EcsServerGroupTransformer } from './serverGroup/serverGroup.transformer';

import './logo/ecs.logo.less';

export function registerEcsProvider(): void {
  CloudProviderRegistry.registerProvider('ecs', {
    name: 'EC2 Container Service',
    adHocInfrastructureWritesEnabled: AWSProviderSettings.adHocInfraWritesEnabled,
    logo: { path: ecsLogo },
    serverGroup: {
      transformer: EcsServerGroupTransformer,
      detailsActions: EcsServerGroupActions,
      detailsGetter: ecsServerGroupDetailsGetter,
      detailsSections: [
        EcsServerGroupInformationSection,
        EcsTaskDefinitionSection,
        EcsEnvironmentVariablesSection,
        EcsHealthSection,
        EcsFirewallsSection,
        EcsCapacitySection,
        EcsScalingPoliciesSection,
        EcsBuildInfoSection,
        EcsServerGroupEventsSection,
      ],
      CloneServerGroupModal: EcsCloneServerGroupModal,
      commandBuilder: EcsServerGroupCommandBuilder,
      scalingActivitiesEnabled: false,
      skipUpstreamStageCheck: true,
    },
    loadBalancer: {
      transformer: EcsLoadBalancerTransformer,
      ClusterContainer: EcsLoadBalancerClusterContainer,
      targetGroupDetails: EcsTargetGroupDetails,
      details: EcsLoadBalancerDetails,
    },
    instance: {
      details: EcsInstanceDetails,
    },
    securityGroup: {
      transformer: EcsSecurityGroupTransformer,
      reader: EcsSecurityGroupReader,
      details: EcsSecurityGroupDetails,
    },
  });
}

export function registerEcsPipelineStages(): void {
  registerEcsCloneServerGroupStage();
  registerEcsDestroyServerGroupStage();
  registerEcsDisableServerGroupStage();
  registerEcsDisableClusterStage();
  registerEcsEnableServerGroupStage();
  registerEcsFindImageFromTagsStage();
  registerEcsResizeServerGroupStage();
  registerEcsScaleDownClusterStage();
  registerEcsShrinkClusterStage();
}

registerEcsProvider();
registerEcsPipelineStages();
DeploymentStrategyRegistry.registerProvider('ecs', ['redblack']);
