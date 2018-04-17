'use strict';

import { module } from 'angular';

import { CLOUD_PROVIDER_REGISTRY, CloudProviderRegistry, DeploymentStrategyRegistry } from '@spinnaker/core';
import './help/openstack.help';

import './logo/openstack.logo.less';

// load all templates into the $templateCache
const templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

export const OPENSTACK_MODULE = 'spinnaker.openstack';
module(OPENSTACK_MODULE, [
  require('./instance/openstackInstanceType.service.js').name,
  require('./serverGroup/configure/ServerGroupCommandBuilder.js').name,
  require('./serverGroup/configure/serverGroup.configure.openstack.module.js').name,
  require('./serverGroup/configure/wizard/Clone.controller.js').name,
  require('./serverGroup/serverGroup.transformer.js').name,
  require('./serverGroup/details/serverGroup.details.module.js').name,
  require('./securityGroup/securityGroup.reader.js').name,
  require('./securityGroup/configure/configure.openstack.module.js').name,
  require('./securityGroup/details/details.controller.js').name,
  require('./securityGroup/transformer.js').name,
  require('./validation/applicationName.validator.js').name,
  require('./cache/cacheConfigurer.service.js').name,
  CLOUD_PROVIDER_REGISTRY,
  require('./loadBalancer/configure/configure.openstack.module.js').name,
  require('./loadBalancer/details/details.openstack.module.js').name,
  require('./loadBalancer/transformer.js').name,
  require('./instance/details/instance.details.controller.js').name,
  require('./common/selectField.component.js').name,
  require('./search/resultFormatter.js').name,
  require('./pipeline/stages/bake/openstackBakeStage.js').name,
  require('./pipeline/stages/findAmi/openstackFindAmiStage.js').name,
  require('./pipeline/stages/resizeAsg/openstackResizeAsgStage.js').name,
  require('./pipeline/stages/destroyAsg/openstackDestroyAsgStage.js').name,
  require('./pipeline/stages/disableAsg/openstackDisableAsgStage.js').name,
  require('./pipeline/stages/enableAsg/openstackEnableAsgStage.js').name,
  require('./pipeline/stages/disableCluster/openstackDisableClusterStage.js').name,
  require('./pipeline/stages/scaleDownCluster/openstackScaleDownClusterStage.js').name,
  require('./pipeline/stages/shrinkCluster/openstackShrinkClusterStage.js').name,
  require('./pipeline/stages/cloneServerGroup/openstackCloneServerGroupStage.js').name,
  require('./subnet/subnet.renderer.js').name,
]).config((cloudProviderRegistryProvider: CloudProviderRegistry) => {
  cloudProviderRegistryProvider.registerProvider('openstack', {
    name: 'openstack',
    logo: {
      path: require('./logo/openstack.logo.png'),
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
    },
  });
});

DeploymentStrategyRegistry.registerProvider('openstack', ['redblack']);
