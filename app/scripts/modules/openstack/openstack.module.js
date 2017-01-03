'use strict';

import {CLOUD_PROVIDER_REGISTRY} from 'core/cloudProvider/cloudProvider.registry';

let angular = require('angular');

require('./logo/openstack.logo.less');

// load all templates into the $templateCache
var templates = require.context('./', true, /\.html$/);
_.forEach(templates.keys(), function(key) {
  templates(key);
});

module.exports = angular.module('spinnaker.openstack', [
  require('./instance/openstackInstanceType.service.js'),
  require('./serverGroup/configure/ServerGroupCommandBuilder.js'),
  require('./serverGroup/configure/serverGroup.configure.openstack.module.js'),
  require('./serverGroup/configure/wizard/Clone.controller.js'),
  require('./serverGroup/serverGroup.transformer.js'),
  require('./serverGroup/details/serverGroup.details.module.js'),
  require('./securityGroup/securityGroup.reader.js'),
  require('./securityGroup/configure/configure.openstack.module.js'),
  require('./securityGroup/details/details.controller.js'),
  require('./securityGroup/transformer.js'),
  require('./validation/applicationName.validator.js'),
  require('./cache/cacheConfigurer.service.js'),
  CLOUD_PROVIDER_REGISTRY,
  require('./loadBalancer/configure/configure.openstack.module.js'),
  require('./loadBalancer/details/details.openstack.module.js'),
  require('./loadBalancer/transformer.js'),
  require('./instance/details/instance.details.controller.js'),
  require('./common/selectField.component.js'),
  require('./search/resultFormatter.js'),
  require('./pipeline/stages/bake/openstackBakeStage.js'),
  require('./pipeline/stages/findAmi/openstackFindAmiStage.js'),
  require('./pipeline/stages/resizeAsg/openstackResizeAsgStage.js'),
  require('./pipeline/stages/destroyAsg/openstackDestroyAsgStage.js'),
  require('./pipeline/stages/disableAsg/openstackDisableAsgStage.js'),
  require('./pipeline/stages/enableAsg/openstackEnableAsgStage.js'),
  require('./pipeline/stages/disableCluster/openstackDisableClusterStage.js'),
  require('./pipeline/stages/scaleDownCluster/openstackScaleDownClusterStage.js'),
  require('./pipeline/stages/shrinkCluster/openstackShrinkClusterStage.js'),
  require('./pipeline/stages/cloneServerGroup/openstackCloneServerGroupStage.js'),
  require('./subnet/subnet.renderer.js')
])
  .config(function(cloudProviderRegistryProvider) {
    cloudProviderRegistryProvider.registerProvider('openstack', {
      name: 'openstack',
      logo: {
        path: require('./logo/openstack.logo.png')
      },
      cache: {
        configurer: 'openstackCacheConfigurer',
      },
      search: {
        resultFormatter: 'openstackSearchResultFormatter',
      },
      image: {
        reader: 'openstackImageReader',
      },
      instance: {
        instanceTypeService: 'openstackInstanceTypeService',
        detailsTemplateUrl: require('./instance/details/instanceDetails.html'),
        detailsController: 'openstackInstanceDetailsCtrl',
      },
      securityGroup: {
        reader: 'openstackSecurityGroupReader',
        transformer: 'openstackSecurityGroupTransformer',
        detailsTemplateUrl: require('./securityGroup/details/details.html'),
        detailsController: 'openstackSecurityGroupDetailsController',
        createSecurityGroupTemplateUrl: require('./securityGroup/configure/wizard/createWizard.html'),
        createSecurityGroupController: 'openstackUpsertSecurityGroupController',
      },
      serverGroup: {
        transformer: 'openstackServerGroupTransformer',
        cloneServerGroupController: 'openstackCloneServerGroupCtrl',
        cloneServerGroupTemplateUrl: require('./serverGroup/configure/wizard/serverGroupWizard.html'),
        commandBuilder: 'openstackServerGroupCommandBuilder',
        configurationService: 'openstackServerGroupConfigurationService',
        detailsTemplateUrl: require('./serverGroup/details/serverGroupDetails.html'),
        detailsController: 'openstackServerGroupDetailsCtrl',
      },
      subnet: {
        renderer: 'openstackSubnetRenderer',
      },
      loadBalancer: {
        transformer: 'openstackLoadBalancerTransformer',
        detailsTemplateUrl: require('./loadBalancer/details/details.html'),
        detailsController: 'openstackLoadBalancerDetailsController',
        createLoadBalancerTemplateUrl: require('./loadBalancer/configure/wizard/createWizard.html'),
        createLoadBalancerController: 'openstackUpsertLoadBalancerController',
      }
    });
  });
