import { CloudProviderRegistry, DeploymentStrategyRegistry } from '@spinnaker/core';

import './help/azure.help';
import { AzureImageReader } from './image/image.reader';
import { AzureInstanceTypeService } from './instance/azureInstanceType.service';
import { AzureInstanceDetails } from './instance/details/AzureInstanceDetails';
import { AzureLoadBalancerChoiceModal } from './loadBalancer/configure/AzureLoadBalancerChoiceModal';
import {
  AzureLoadBalancerActions,
  azureLoadBalancerDetailsSections,
  useAzureLoadBalancerDetails,
} from './loadBalancer/details/azureLoadBalancerDetails';
import { AzureLoadBalancerTransformer } from './loadBalancer/loadBalancer.transformer';
import logo from './logo/logo_azure.png';
import { registerAzureBakeStage } from './pipeline/stages/bake/azureBakeStage';
import { registerAzureDestroyAsgStage } from './pipeline/stages/destroyAsg/azureDestroyAsgStage';
import { registerAzureDisableAsgStage } from './pipeline/stages/disableAsg/azureDisableAsgStage';
import { registerAzureEnableAsgStage } from './pipeline/stages/enableAsg/azureEnableAsgStage';
import { AzureSecurityGroupModal } from './securityGroup/configure/AzureSecurityGroupModal';
import { AzureSecurityGroupDetails } from './securityGroup/details/AzureSecurityGroupDetails';
import { AzureSecurityGroupReader } from './securityGroup/securityGroup.reader';
import { AzureSecurityGroupTransformer } from './securityGroup/securityGroup.transformer';
import { AzureServerGroupCommandBuilder } from './serverGroup/configure/serverGroupCommandBuilder.service';
import { AzureServerGroupConfigurationService } from './serverGroup/configure/serverGroupConfiguration.service';
import { AzureCloneServerGroupModal } from './serverGroup/configure/wizard/AzureCloneServerGroupModal';
import {
  AzureServerGroupActions,
  azureServerGroupDetailsGetter,
  azureServerGroupDetailsSections,
} from './serverGroup/details/azureServerGroupDetails';
import { AzureServerGroupTransformer } from './serverGroup/serverGroup.transformer';
import './validation/applicationName.validator';

import './logo/azure.logo.less';

export function registerAzureProvider(): void {
  CloudProviderRegistry.registerProvider('azure', {
    name: 'Azure',
    logo: {
      path: logo,
    },
    image: {
      reader: AzureImageReader,
    },
    serverGroup: {
      transformer: AzureServerGroupTransformer,
      commandBuilder: AzureServerGroupCommandBuilder,
      configurationService: AzureServerGroupConfigurationService,
      CloneServerGroupModal: AzureCloneServerGroupModal,
      detailsGetter: azureServerGroupDetailsGetter,
      detailsActions: AzureServerGroupActions,
      detailsSections: azureServerGroupDetailsSections,
    },
    instance: {
      details: AzureInstanceDetails,
      instanceTypeService: AzureInstanceTypeService,
    },
    loadBalancer: {
      transformer: AzureLoadBalancerTransformer,
      CreateLoadBalancerModal: AzureLoadBalancerChoiceModal,
      useDetailsHook: useAzureLoadBalancerDetails,
      detailsActions: AzureLoadBalancerActions,
      detailsSections: azureLoadBalancerDetailsSections,
    },
    securityGroup: {
      transformer: AzureSecurityGroupTransformer,
      reader: AzureSecurityGroupReader,
      CreateSecurityGroupModal: AzureSecurityGroupModal,
      details: AzureSecurityGroupDetails,
    },
  });
}

export function registerAzurePipelineStages(): void {
  registerAzureDestroyAsgStage();
  registerAzureEnableAsgStage();
  registerAzureDisableAsgStage();
  registerAzureBakeStage();
}

registerAzureProvider();
registerAzurePipelineStages();
DeploymentStrategyRegistry.registerProvider('azure', ['redblack']);
