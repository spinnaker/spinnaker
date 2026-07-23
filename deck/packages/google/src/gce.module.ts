import { CloudProviderRegistry, DeploymentStrategyRegistry, Registry } from '@spinnaker/core';

import { GceCacheConfigurer } from './cache/cacheConfigurer.service';
import './help/gce.help';
import { GceImageReader } from './image';
import { GceInstanceDetails } from './instance/details/GceInstanceDetails';
import { GceInstanceTypeService } from './instance/gceInstanceType.service';
import { GceMultiInstanceTaskTransformer } from './instance/gceMultiInstanceTask.transformer';
import { GceLoadBalancerChoiceModal } from './loadBalancer/configure/choice/GceLoadBalancerChoiceModal';
import {
  GceLoadBalancerActions,
  gceLoadBalancerDetailsSections,
  useGceLoadBalancerDetails,
} from './loadBalancer/details/gceLoadBalancerDetails';
import { GceLoadBalancerSetTransformer } from './loadBalancer/loadBalancer.setTransformer';
import { GceLoadBalancerTransformer } from './loadBalancer/loadBalancer.transformer';
import logo from './logo/gce.logo.png';
import { registerGceBakeStage } from './pipeline/stages/bake/gceBakeStage';
import { registerGceCloneServerGroupStage } from './pipeline/stages/cloneServerGroup/gceCloneServerGroupStage';
import { registerGceDestroyAsgStage } from './pipeline/stages/destroyAsg/gceDestroyAsgStage';
import { registerGceDisableAsgStage } from './pipeline/stages/disableAsg/gceDisableAsgStage';
import { registerGceDisableClusterStage } from './pipeline/stages/disableCluster/gceDisableClusterStage';
import { registerGceEnableAsgStage } from './pipeline/stages/enableAsg/gceEnableAsgStage';
import { registerGceFindAmiStage } from './pipeline/stages/findAmi/gceFindAmiStage';
import { registerGceFindImageFromTagsStage } from './pipeline/stages/findImageFromTags/gceFindImageFromTagsStage';
import { registerGceResizeAsgStage } from './pipeline/stages/resizeAsg/gceResizeAsgStage';
import { registerGceScaleDownClusterStage } from './pipeline/stages/scaleDownCluster/gceScaleDownClusterStage';
import { registerGceShrinkClusterStage } from './pipeline/stages/shrinkCluster/gceShrinkClusterStage';
import { registerGceTagImageStage } from './pipeline/stages/tagImage/gceTagImageStage';
import { GceSecurityGroupModal } from './securityGroup/configure/GceSecurityGroupModal';
import { GceSecurityGroupDetails } from './securityGroup/details/GceSecurityGroupDetails';
import { GceSecurityGroupReader } from './securityGroup/securityGroup.reader';
import { GceSecurityGroupTransformer } from './securityGroup/securityGroup.transformer';
import { GceServerGroupCommandBuilder } from './serverGroup/configure/serverGroupCommandBuilder.service';
import { GceServerGroupConfigurationService } from './serverGroup/configure/serverGroupConfiguration.service';
import { GceCloneServerGroupModal } from './serverGroup/configure/wizard/GceCloneServerGroupModal';
import { GceCustomInstanceBuilder } from './serverGroup/configure/wizard/customInstance/GceCustomInstanceBuilder';
import {
  GceServerGroupActions,
  gceServerGroupDetailsGetter,
  gceServerGroupDetailsSections,
} from './serverGroup/details/gceServerGroupDetails';
import { GceServerGroupTransformer } from './serverGroup/serverGroup.transformer';
import { GceSubnetRenderer } from './subnet/subnet.renderer';

import './logo/gce.logo.less';

export function registerGoogleProvider(): void {
  CloudProviderRegistry.registerProvider('gce', {
    name: 'Google',
    logo: {
      path: logo,
    },
    cache: {
      configurer: GceCacheConfigurer,
    },
    image: {
      reader: GceImageReader,
    },
    serverGroup: {
      transformer: GceServerGroupTransformer,
      commandBuilder: GceServerGroupCommandBuilder,
      configurationService: GceServerGroupConfigurationService,
      CloneServerGroupModal: GceCloneServerGroupModal,
      detailsActions: GceServerGroupActions,
      detailsGetter: gceServerGroupDetailsGetter,
      detailsSections: gceServerGroupDetailsSections,
    },
    instance: {
      CustomInstanceBuilder: GceCustomInstanceBuilder,
      details: GceInstanceDetails,
      instanceTypeService: GceInstanceTypeService,
      multiInstanceTaskTransformer: GceMultiInstanceTaskTransformer,
    },
    loadBalancer: {
      CreateLoadBalancerModal: GceLoadBalancerChoiceModal,
      pipelineCreateLoadBalancerModal: (props: any) =>
        GceLoadBalancerChoiceModal.show({ ...props, app: props.application, forPipelineConfig: true }),
      detailsActions: GceLoadBalancerActions,
      detailsSections: gceLoadBalancerDetailsSections,
      transformer: GceLoadBalancerTransformer,
      setTransformer: GceLoadBalancerSetTransformer,
      useDetailsHook: useGceLoadBalancerDetails,
    },
    securityGroup: {
      CreateSecurityGroupModal: GceSecurityGroupModal,
      details: GceSecurityGroupDetails,
      transformer: GceSecurityGroupTransformer,
      reader: GceSecurityGroupReader,
    },
    subnet: {
      renderer: GceSubnetRenderer,
    },
    snapshotsEnabled: true,
  });
}

registerGoogleProvider();

function registerPipelineStageOnce(stageKey: string, registerStage: () => void): void {
  const isRegistered = Registry.pipeline.getStageTypes().some((stage) => {
    const registeredStageKey = stage.provides || stage.key;
    return registeredStageKey === stageKey && stage.cloudProvider === 'gce';
  });

  if (!isRegistered) {
    registerStage();
  }
}

export function registerGooglePipelineStages(): void {
  registerPipelineStageOnce('bake', registerGceBakeStage);
  registerPipelineStageOnce('cloneServerGroup', registerGceCloneServerGroupStage);
  registerPipelineStageOnce('destroyServerGroup', registerGceDestroyAsgStage);
  registerPipelineStageOnce('disableServerGroup', registerGceDisableAsgStage);
  registerPipelineStageOnce('disableCluster', registerGceDisableClusterStage);
  registerPipelineStageOnce('enableServerGroup', registerGceEnableAsgStage);
  registerPipelineStageOnce('findImage', registerGceFindAmiStage);
  registerPipelineStageOnce('findImageFromTags', registerGceFindImageFromTagsStage);
  registerPipelineStageOnce('resizeServerGroup', registerGceResizeAsgStage);
  registerPipelineStageOnce('scaleDownCluster', registerGceScaleDownClusterStage);
  registerPipelineStageOnce('shrinkCluster', registerGceShrinkClusterStage);
  registerPipelineStageOnce('upsertImageTags', registerGceTagImageStage);
}

registerGooglePipelineStages();

DeploymentStrategyRegistry.registerProvider('gce', ['custom', 'redblack']);
