'use strict';

const angular = require('angular');

import { CLOUD_PROVIDER_REGISTRY, DeploymentStrategyRegistry } from '@spinnaker/core';

import './help/azure.help';

// load all templates into the $templateCache
var templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

module.exports = angular
  .module('spinnaker.azure', [
    require('./pipeline/stages/destroyAsg/azureDestroyAsgStage.js').name,
    require('./pipeline/stages/enableAsg/azureEnableAsgStage.js').name,
    require('./pipeline/stages/disableAsg/azureDisableAsgStage.js').name,
    require('./pipeline/stages/bake/azureBakeStage.js').name,
    CLOUD_PROVIDER_REGISTRY,
    require('./serverGroup/details/serverGroup.details.module.js').name,
    require('./serverGroup/serverGroup.transformer.js').name,
    require('./serverGroup/configure/wizard/CloneServerGroup.azure.controller.js').name,
    require('./serverGroup/configure/serverGroup.configure.azure.module.js').name,
    require('./instance/azureInstanceType.service.js').name,
    require('./loadBalancer/loadBalancer.transformer.js').name,
    require('./loadBalancer/details/loadBalancerDetail.controller.js').name,
    require('./loadBalancer/configure/createLoadBalancer.controller.js').name,
    require('./instance/details/instance.details.controller.js').name,
    require('./securityGroup/details/securityGroupDetail.controller.js').name,
    require('./securityGroup/configure/CreateSecurityGroupCtrl.js').name,
    require('./securityGroup/configure/EditSecurityGroupCtrl.js').name,
    require('./securityGroup/securityGroup.transformer.js').name,
    require('./securityGroup/securityGroup.reader.js').name,
    require('./image/image.reader.js').name,
    require('./cache/cacheConfigurer.service.js').name,
  ])
  .config(function(cloudProviderRegistryProvider) {
    cloudProviderRegistryProvider.registerProvider('azure', {
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
