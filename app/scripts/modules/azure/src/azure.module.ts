import { module } from 'angular';

import { CloudProviderRegistry, DeploymentStrategyRegistry } from '@spinnaker/core';

import './help/azure.help';
import { AZURE_IMAGE_IMAGE_READER } from './image/image.reader';
import { AZURE_INSTANCE_AZUREINSTANCETYPE_SERVICE } from './instance/azureInstanceType.service';
import { AZURE_INSTANCE_DETAILS_INSTANCE_DETAILS_CONTROLLER } from './instance/details/instance.details.controller';
import { AzureLoadBalancerChoiceModal } from './loadBalancer/configure/AzureLoadBalancerChoiceModal';
import { AZURE_LOADBALANCER_CONFIGURE_CREATELOADBALANCER_CONTROLLER } from './loadBalancer/configure/createLoadBalancer.controller';
import { AZURE_LOADBALANCER_DETAILS_LOADBALANCERDETAIL_CONTROLLER } from './loadBalancer/details/loadBalancerDetail.controller';
import { AZURE_LOADBALANCER_LOADBALANCER_TRANSFORMER } from './loadBalancer/loadBalancer.transformer';
import logo from './logo/logo_azure.png';
import { AZURE_PIPELINE_STAGES_BAKE_AZUREBAKESTAGE } from './pipeline/stages/bake/azureBakeStage';
import { AZURE_PIPELINE_STAGES_DESTROYASG_AZUREDESTROYASGSTAGE } from './pipeline/stages/destroyAsg/azureDestroyAsgStage';
import { AZURE_PIPELINE_STAGES_DISABLEASG_AZUREDISABLEASGSTAGE } from './pipeline/stages/disableAsg/azureDisableAsgStage';
import { AZURE_PIPELINE_STAGES_ENABLEASG_AZUREENABLEASGSTAGE } from './pipeline/stages/enableAsg/azureEnableAsgStage';
import { AZURE_SECURITYGROUP_CONFIGURE_CREATESECURITYGROUPCTRL } from './securityGroup/configure/CreateSecurityGroupCtrl';
import { AZURE_SECURITYGROUP_CONFIGURE_EDITSECURITYGROUPCTRL } from './securityGroup/configure/EditSecurityGroupCtrl';
import { AZURE_SECURITYGROUP_DETAILS_SECURITYGROUPDETAIL_CONTROLLER } from './securityGroup/details/securityGroupDetail.controller';
import { AZURE_SECURITYGROUP_SECURITYGROUP_READER } from './securityGroup/securityGroup.reader';
import { AZURE_SECURITYGROUP_SECURITYGROUP_TRANSFORMER } from './securityGroup/securityGroup.transformer';
import { AZURE_SERVERGROUP_CONFIGURE_SERVERGROUP_CONFIGURE_AZURE_MODULE } from './serverGroup/configure/serverGroup.configure.azure.module';
import { AZURE_SERVERGROUP_CONFIGURE_WIZARD_CLONESERVERGROUP_AZURE_CONTROLLER } from './serverGroup/configure/wizard/CloneServerGroup.azure.controller';
import { AZURE_SERVERGROUP_DETAILS_SERVERGROUP_DETAILS_MODULE } from './serverGroup/details/serverGroup.details.module';
import { AZURE_SERVERGROUP_SERVERGROUP_TRANSFORMER } from './serverGroup/serverGroup.transformer';
import { AZURE_VALIDATION_APPLICATIONNAME_VALIDATOR } from './validation/applicationName.validator';

import './logo/azure.logo.less';

export const AZURE_MODULE = 'spinnaker.azure';
module(AZURE_MODULE, [
  AZURE_PIPELINE_STAGES_DESTROYASG_AZUREDESTROYASGSTAGE,
  AZURE_PIPELINE_STAGES_ENABLEASG_AZUREENABLEASGSTAGE,
  AZURE_PIPELINE_STAGES_DISABLEASG_AZUREDISABLEASGSTAGE,
  AZURE_PIPELINE_STAGES_BAKE_AZUREBAKESTAGE,
  AZURE_SERVERGROUP_DETAILS_SERVERGROUP_DETAILS_MODULE,
  AZURE_SERVERGROUP_SERVERGROUP_TRANSFORMER,
  AZURE_SERVERGROUP_CONFIGURE_WIZARD_CLONESERVERGROUP_AZURE_CONTROLLER,
  AZURE_SERVERGROUP_CONFIGURE_SERVERGROUP_CONFIGURE_AZURE_MODULE,
  AZURE_INSTANCE_AZUREINSTANCETYPE_SERVICE,
  AZURE_LOADBALANCER_LOADBALANCER_TRANSFORMER,
  AZURE_LOADBALANCER_DETAILS_LOADBALANCERDETAIL_CONTROLLER,
  AZURE_LOADBALANCER_CONFIGURE_CREATELOADBALANCER_CONTROLLER,
  AZURE_INSTANCE_DETAILS_INSTANCE_DETAILS_CONTROLLER,
  AZURE_SECURITYGROUP_DETAILS_SECURITYGROUPDETAIL_CONTROLLER,
  AZURE_SECURITYGROUP_CONFIGURE_CREATESECURITYGROUPCTRL,
  AZURE_SECURITYGROUP_CONFIGURE_EDITSECURITYGROUPCTRL,
  AZURE_SECURITYGROUP_SECURITYGROUP_TRANSFORMER,
  AZURE_SECURITYGROUP_SECURITYGROUP_READER,
  AZURE_IMAGE_IMAGE_READER,
  AZURE_VALIDATION_APPLICATIONNAME_VALIDATOR,
]).config(function () {
  CloudProviderRegistry.registerProvider('azure', {
    name: 'Azure',
    logo: {
      path: logo,
    },
    image: {
      reader: 'azureImageReader',
    },
    serverGroup: {
      transformer: 'azureServerGroupTransformer',
      detailsTemplateUrl: require('./serverGroup/details/serverGroupDetails.html'),
      detailsController: 'azureServerGroupDetailsCtrl',
      cloneServerGroupTemplateUrl: require('./serverGroup/configure/wizard/serverGroupWizard.html'),
      cloneServerGroupController: 'azureCloneServerGroupCtrl',
      commandBuilder: 'azureServerGroupCommandBuilder',
      configurationService: 'azureServerGroupConfigurationService',
    },
    instance: {
      instanceTypeService: 'azureInstanceTypeService',
      detailsTemplateUrl: require('./instance/details/instanceDetails.html'),
      detailsController: 'azureInstanceDetailsCtrl',
    },
    loadBalancer: {
      transformer: 'azureLoadBalancerTransformer',
      detailsTemplateUrl: require('./loadBalancer/details/loadBalancerDetail.html'),
      detailsController: 'azureLoadBalancerDetailsCtrl',
      createLoadBalancerTemplateUrl: require('./loadBalancer/configure/createLoadBalancer.html'),
      createLoadBalancerController: 'azureCreateLoadBalancerCtrl',
      CreateLoadBalancerModal: AzureLoadBalancerChoiceModal,
    },
    securityGroup: {
      transformer: 'azureSecurityGroupTransformer',
      reader: 'azureSecurityGroupReader',
      detailsTemplateUrl: require('./securityGroup/details/securityGroupDetail.html'),
      detailsController: 'azureSecurityGroupDetailsCtrl',
      createSecurityGroupTemplateUrl: require('./securityGroup/configure/createSecurityGroup.html'),
      createSecurityGroupController: 'azureCreateSecurityGroupCtrl',
    },
  });
});

DeploymentStrategyRegistry.registerProvider('azure', ['redblack']);
