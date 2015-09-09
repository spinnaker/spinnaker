'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.aws', [
  require('../core/cloudProvider/cloudProvider.registry.js'),
  require('./serverGroup/details/serverGroup.details.module.js'),
  require('./serverGroup/serverGroup.transformer.js'),
  require('./serverGroup/configure/wizard/CloneServerGroup.aws.controller.js'),
  require('./serverGroup/configure/serverGroup.configure.aws.module.js'),
  require('../pipelines/config/stages/bake/aws/awsBakeStage.js'),
  require('../pipelines/config/stages/resizeAsg/aws/awsResizeAsgStage.js'),
  require('./instance/awsInstanceTypeService.js'),
  require('./loadBalancer/loadBalancer.transformer.js'),
  require('./loadBalancer/details/LoadBalancerDetailsCtrl.js'),
  require('./loadBalancer/configure/CreateLoadBalancerCtrl.js'),
  require('./instance/details/instance.details.controller.js'),
  require('./securityGroup/details/SecurityGroupDetailsCtrl.js'),
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
    cloudProviderRegistryProvider.registerProvider('aws', {
      logo: {
        path: require('./logo_aws.png'),
      },
      cache: {
        configurer: 'awsCacheConfigurer',
      },
      image: {
        reader: 'awsImageReader',
      },
      serverGroup: {
        transformer: 'awsServerGroupTransformer',
        detailsTemplateUrl: require('./serverGroup/details/serverGroupDetails.html'),
        detailsController: 'awsServerGroupDetailsCtrl',
        cloneServerGroupTemplateUrl: require('./serverGroup/configure/wizard/serverGroupWizard.html'),
        cloneServerGroupController: 'awsCloneServerGroupCtrl',
        commandBuilder: 'awsServerGroupCommandBuilder',
      },
      instance: {
        instanceTypeService: 'awsInstanceTypeService',
        detailsTemplateUrl: require('./instance/details/instanceDetails.html'),
        detailsController: 'awsInstanceDetailsCtrl',
      },
      loadBalancer: {
        transformer: 'awsLoadBalancerTransformer',
        detailsTemplateUrl: require('./loadBalancer/details/loadBalancerDetails.html'),
        detailsController: 'awsLoadBalancerDetailsCtrl',
        createLoadBalancerTemplateUrl: require('./loadBalancer/configure/createLoadBalancer.html'),
        createLoadBalancerController: 'awsCreateLoadBalancerCtrl',
      },
      securityGroup: {
        transformer: 'awsSecurityGroupTransformer',
        reader: 'awsSecurityGroupReader',
        detailsTemplateUrl: require('./securityGroup/details/securityGroupDetails.html'),
        detailsController: 'awsSecurityGroupDetailsCtrl',
        createSecurityGroupTemplateUrl: require('./securityGroup/configure/createSecurityGroup.html'),
        createSecurityGroupController: 'awsCreateSecurityGroupCtrl',
      }
    });
  }).name;
