import { AmazonLoadBalancersTag } from '@spinnaker/amazon';
import { CloudProviderRegistry, DeploymentStrategyRegistry } from '@spinnaker/core';

import './help/titus.help';
import { TitusInstanceDetails } from './instance/details/TitusInstanceDetails';
import titusLogo from './logo/titus.logo.png';
import './pipeline/stages/cloneServerGroup/titusCloneServerGroupStage';
import './pipeline/stages/destroyAsg/titusDestroyAsgStage';
import './pipeline/stages/disableAsg/titusDisableAsgStage';
import './pipeline/stages/disableCluster/titusDisableClusterStage';
import './pipeline/stages/enableAsg/titusEnableAsgStage';
import './pipeline/stages/findAmi/titusFindAmiStage';
import './pipeline/stages/resizeAsg/titusResizeAsgStage';
import './pipeline/stages/runJob/titusRunJobStage';
import './pipeline/stages/scaleDownCluster/titusScaleDownClusterStage';
import './pipeline/stages/shrinkCluster/titusShrinkClusterStage';
import { TitusSecurityGroupReaderDelegate } from './securityGroup/securityGroup.read.service';
import { TitusServerGroupCommandBuilderDelegate } from './serverGroup/configure/ServerGroupCommandBuilder';
import { TitusServerGroupConfigurationServiceFactory } from './serverGroup/configure/serverGroupConfiguration.service';
import { TitusCloneServerGroupModal } from './serverGroup/configure/wizard/TitusCloneServerGroupModal';
import {
  TitusTargetTrackingChart,
  TitusUpsertScalingPolicyModal,
  TitusUpsertTargetTrackingModal,
} from './serverGroup/details';
import { TitusSecurityGroupsDetailsSection } from './serverGroup/details/TitusSecurityGroups';
import { TitusServerGroupActions } from './serverGroup/details/TitusServerGroupActions';
import {
  TitusCapacitySection,
  TitusContainerAttributesSection,
  TitusDisruptionBudgetDetailsSection,
  TitusEnvironmentVariablesSection,
  TitusHealthSection,
  TitusJobAttributesSection,
  TitusLaunchConfigurationSection,
  TitusPackageSection,
  TitusScalingPoliciesSection,
  TitusServerGroupInformationSection,
  TitusServiceJobProcessesDetailsSection,
} from './serverGroup/details/TitusServerGroupDetailsSections';
import { titusServerGroupDetailsGetter } from './serverGroup/details/titusServerGroupDetailsGetter';
import { TitusServerGroupTransformerDelegate } from './serverGroup/serverGroup.transformer';
import './validation/ApplicationNameValidator';

import './logo/titus.logo.less';

export function registerTitusProvider(): void {
  CloudProviderRegistry.registerProvider('titus', {
    name: 'Titus',
    logo: {
      path: titusLogo,
    },
    serverGroup: {
      transformer: TitusServerGroupTransformerDelegate,
      detailsActions: TitusServerGroupActions,
      detailsGetter: titusServerGroupDetailsGetter,
      detailsSections: [
        TitusServerGroupInformationSection,
        TitusCapacitySection,
        TitusHealthSection,
        TitusLaunchConfigurationSection,
        TitusSecurityGroupsDetailsSection,
        TitusServiceJobProcessesDetailsSection,
        TitusScalingPoliciesSection,
        TitusPackageSection,
        TitusDisruptionBudgetDetailsSection,
        TitusJobAttributesSection,
        TitusContainerAttributesSection,
        TitusEnvironmentVariablesSection,
      ],
      CloneServerGroupModal: TitusCloneServerGroupModal,
      commandBuilder: TitusServerGroupCommandBuilderDelegate,
      configurationService: TitusServerGroupConfigurationServiceFactory,
      skipUpstreamStageCheck: true,
      checkForImageProviders: true,
      TargetTrackingChart: TitusTargetTrackingChart,
      UpsertStepPolicyModal: TitusUpsertScalingPolicyModal,
      UpsertTargetTrackingModal: TitusUpsertTargetTrackingModal,
    },
    securityGroup: {
      reader: TitusSecurityGroupReaderDelegate,
      useProvider: 'aws',
    },
    loadBalancer: {
      LoadBalancersTag: AmazonLoadBalancersTag,
      incompatibleLoadBalancerTypes: [
        {
          type: 'classic',
          reason: 'Classic Load Balancers cannot be used with Titus as they do not have IP based target groups.',
        },
      ],
      useProvider: 'aws',
    },
    instance: {
      details: TitusInstanceDetails,
    },
  });
}

registerTitusProvider();

DeploymentStrategyRegistry.registerProvider('titus', ['custom', 'redblack', 'monitored']);
