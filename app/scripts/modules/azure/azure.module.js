'use strict';

let angular = require('angular');

// load all templates into the $templateCache
var templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

module.exports = angular.module('spinnaker.azure', [
  require('./pipeline/stages/destroyAsg/azureDestroyAsgStage.js'),
  require('./pipeline/stages/enableAsg/azureEnableAsgStage.js'),
  require('./pipeline/stages/disableAsg/azureDisableAsgStage.js'),
  require('./pipeline/stages/bake/azureBakeStage.js'),
  require('../core/cloudProvider/cloudProvider.registry.js'),
  require('./serverGroup/details/serverGroup.details.module.js'),
  require('./serverGroup/serverGroup.transformer.js'),
  require('./serverGroup/configure/wizard/CloneServerGroup.azure.controller.js'),
  require('./serverGroup/configure/serverGroup.configure.azure.module.js'),
  require('./instance/azureInstanceType.service.js'),
  require('./loadBalancer/loadBalancer.transformer.js'),
  require('./loadBalancer/details/loadBalancerDetail.controller.js'),
  require('./loadBalancer/configure/createLoadBalancer.controller.js'),
  require('./instance/details/instance.details.controller.js'),
  require('./securityGroup/details/securityGroupDetail.controller.js'),
  require('./securityGroup/configure/CreateSecurityGroupCtrl.js'),
  require('./securityGroup/configure/EditSecurityGroupCtrl.js'),
  require('./securityGroup/securityGroup.transformer.js'),
  require('./securityGroup/securityGroup.reader.js'),
  require('./subnet/subnet.module.js'),
  require('./image/image.reader.js'),
  require('./cache/cacheConfigurer.service.js'),
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
      }
    });
  });
