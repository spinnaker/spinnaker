'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure', [
  require('../core/cloudProvider/cloudProvider.registry.js'),
  require('./serverGroup/details/serverGroup.details.module.js'),
  require('./serverGroup/serverGroup.transformer.js'),
  require('./serverGroup/configure/wizard/CloneServerGroup.azure.controller.js'),
  require('./serverGroup/configure/serverGroup.configure.azure.module.js'),
  require('../core/pipeline/config/stages/bake/aws/awsBakeStage.js'),
  require('../core/pipeline/config/stages/destroyAsg/aws/awsDestroyAsgStage.js'),
  require('../core/pipeline/config/stages/resizeAsg/aws/awsResizeAsgStage.js'),
  require('./instance/azureInstanceType.service.js'),
  require('./loadBalancer/loadBalancer.transformer.js'),
  require('./loadBalancer/details/loadBalancerDetail.controller.js'),
  require('./loadBalancer/configure/createLoadBalancer.controller.js'),
  require('./instance/details/instance.details.controller.js'),
  require('./securityGroup/details/securityGroupDetail.controller.js'),
  require('./securityGroup/configure/CreateSecurityGroupCtrl.js'),
  require('./keyPairs/keyPairs.read.service.js'),
  require('./securityGroup/configure/EditSecurityGroupCtrl.js'),
  require('./securityGroup/securityGroup.transformer.js'),
  require('./securityGroup/securityGroup.reader.js'),
  require('./subnet/subnet.module.js'),
  require('./vpc/vpc.module.js'),
  require('./image/image.reader.js'),
  require('./cache/cacheConfigurer.service.js'),
])
  .config(function(cloudProviderRegistryProvider) {
    cloudProviderRegistryProvider.registerProvider('azure', {
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
  }).name;
