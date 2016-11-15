'use strict';

let angular = require('angular');

require('./logo/aws.logo.less');

// load all templates into the $templateCache
var templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

module.exports = angular.module('spinnaker.aws', [
  require('core/cloudProvider/cloudProvider.registry.js'),
  require('./pipeline/stages/bake/awsBakeStage.js'),
  require('./pipeline/stages/cloneServerGroup/awsCloneServerGroupStage.js'),
  require('./pipeline/stages/destroyAsg/awsDestroyAsgStage.js'),
  require('./pipeline/stages/disableAsg/awsDisableAsgStage.js'),
  require('./pipeline/stages/disableCluster/awsDisableClusterStage.js'),
  require('./pipeline/stages/enableAsg/awsEnableAsgStage.js'),
  require('./pipeline/stages/findAmi/awsFindAmiStage.js'),
  require('core/pipeline/config/stages/modifyScalingProcess/modifyScalingProcess.module.js'),
  require('./pipeline/stages/resizeAsg/awsResizeAsgStage.js'),
  require('./pipeline/stages/scaleDownCluster/awsScaleDownClusterStage.js'),
  require('./pipeline/stages/shrinkCluster/awsShrinkClusterStage.js'),
  require('./serverGroup/details/serverGroup.details.module.js'),
  require('./serverGroup/serverGroup.transformer.js'),
  require('./serverGroup/configure/wizard/CloneServerGroup.aws.controller.js'),
  require('./serverGroup/configure/serverGroup.configure.aws.module.js'),
  require('./instance/awsInstanceType.service.js'),
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
  require('./subnet/subnet.renderer.js'),
  require('./validation/applicationName.validator.js'),
  require('./vpc/vpc.module.js'),
  require('./image/image.reader.js'),
  require('./cache/cacheConfigurer.service.js'),
  require('./search/searchResultFormatter.js'),
])
  .config(function(cloudProviderRegistryProvider) {
    cloudProviderRegistryProvider.registerProvider('aws', {
      name: 'Amazon',
      logo: {
        path: require('./logo/amazon.logo.svg'),
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
        configurationService: 'awsServerGroupConfigurationService',
      },
      instance: {
        instanceTypeService: 'awsInstanceTypeService',
        detailsTemplateUrl: require('./instance/details/instanceDetails.html'),
        detailsController: 'awsInstanceDetailsCtrl',
      },
      loadBalancer: {
        transformer: 'awsLoadBalancerTransformer',
        detailsTemplateUrl: require('./loadBalancer/details/loadBalancerDetail.html'),
        detailsController: 'awsLoadBalancerDetailsCtrl',
        createLoadBalancerTemplateUrl: require('./loadBalancer/configure/createLoadBalancer.html'),
        createLoadBalancerController: 'awsCreateLoadBalancerCtrl',
        editLoadBalancerTemplateUrl: require('./loadBalancer/configure/editLoadBalancer.html'),
      },
      securityGroup: {
        transformer: 'awsSecurityGroupTransformer',
        reader: 'awsSecurityGroupReader',
        detailsTemplateUrl: require('./securityGroup/details/securityGroupDetail.html'),
        detailsController: 'awsSecurityGroupDetailsCtrl',
        createSecurityGroupTemplateUrl: require('./securityGroup/configure/createSecurityGroup.html'),
        createSecurityGroupController: 'awsCreateSecurityGroupCtrl',
      },
      subnet: {
        renderer: 'awsSubnetRenderer',
      },
      search: {
        resultFormatter: 'awsSearchResultFormatter',
      },
      applicationProviderFields:{
        templateUrl: require('./applicationProviderFields/awsFields.html'),
      },
    });
  });
