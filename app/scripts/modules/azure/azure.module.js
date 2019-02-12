'use strict';

const angular = require('angular');

import { CloudProviderRegistry, DeploymentStrategyRegistry } from '@spinnaker/core';

import './help/azure.help';

// load all templates into the $templateCache
var templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

module.exports = angular
  .module('spinnaker.azure', [
    require('./pipeline/stages/destroyAsg/azureDestroyAsgStage').name,
    require('./pipeline/stages/enableAsg/azureEnableAsgStage').name,
    require('./pipeline/stages/disableAsg/azureDisableAsgStage').name,
    require('./pipeline/stages/bake/azureBakeStage').name,
    require('./serverGroup/details/serverGroup.details.module').name,
    require('./serverGroup/serverGroup.transformer').name,
    require('./serverGroup/configure/wizard/CloneServerGroup.azure.controller').name,
    require('./serverGroup/configure/serverGroup.configure.azure.module').name,
    require('./instance/azureInstanceType.service').name,
    require('./loadBalancer/loadBalancer.transformer').name,
    require('./loadBalancer/details/loadBalancerDetail.controller').name,
    require('./loadBalancer/configure/createLoadBalancer.controller').name,
    require('./instance/details/instance.details.controller').name,
    require('./securityGroup/details/securityGroupDetail.controller').name,
    require('./securityGroup/configure/CreateSecurityGroupCtrl').name,
    require('./securityGroup/configure/EditSecurityGroupCtrl').name,
    require('./securityGroup/securityGroup.transformer').name,
    require('./securityGroup/securityGroup.reader').name,
    require('./image/image.reader').name,
    require('./cache/cacheConfigurer.service').name,
    require('./validation/applicationName.validator').name,
  ])
  .config(function() {
    CloudProviderRegistry.registerProvider('azure', {
      name: 'Azure',
      logo: {
        path: require('./logo_azure.png'),
      },
      cache: {
        configurer: 'azureCacheConfigurer',
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

DeploymentStrategyRegistry.registerProvider('azure', []);
