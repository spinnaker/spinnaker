'use strict';

import { module } from 'angular';

import { CloudProviderRegistry, DeploymentStrategyRegistry } from '@spinnaker/core';
import './help/openstack.help';

import './logo/openstack.logo.less';

// load all templates into the $templateCache
const templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

export const OPENSTACK_MODULE = 'spinnaker.openstack';
module(OPENSTACK_MODULE, [
  require('./instance/openstackInstanceType.service').name,
  require('./serverGroup/configure/ServerGroupCommandBuilder').name,
  require('./serverGroup/configure/serverGroup.configure.openstack.module').name,
  require('./serverGroup/configure/wizard/Clone.controller').name,
  require('./serverGroup/serverGroup.transformer').name,
  require('./serverGroup/details/serverGroup.details.module').name,
  require('./securityGroup/securityGroup.reader').name,
  require('./securityGroup/configure/configure.openstack.module').name,
  require('./securityGroup/details/details.controller').name,
  require('./securityGroup/transformer').name,
  require('./validation/applicationName.validator').name,
  require('./loadBalancer/configure/configure.openstack.module').name,
  require('./loadBalancer/details/details.openstack.module').name,
  require('./loadBalancer/transformer').name,
  require('./instance/details/instance.details.controller').name,
  require('./common/selectField.component').name,
  require('./search/resultFormatter').name,
  require('./pipeline/stages/bake/openstackBakeStage').name,
  require('./pipeline/stages/findAmi/openstackFindAmiStage').name,
  require('./pipeline/stages/resizeAsg/openstackResizeAsgStage').name,
  require('./pipeline/stages/destroyAsg/openstackDestroyAsgStage').name,
  require('./pipeline/stages/disableAsg/openstackDisableAsgStage').name,
  require('./pipeline/stages/enableAsg/openstackEnableAsgStage').name,
  require('./pipeline/stages/disableCluster/openstackDisableClusterStage').name,
  require('./pipeline/stages/scaleDownCluster/openstackScaleDownClusterStage').name,
  require('./pipeline/stages/shrinkCluster/openstackShrinkClusterStage').name,
  require('./pipeline/stages/cloneServerGroup/openstackCloneServerGroupStage').name,
  require('./subnet/subnet.renderer').name,
]).config(() => {
  CloudProviderRegistry.registerProvider('openstack', {
    name: 'openstack',
    logo: {
      path: require('./logo/openstack.logo.png'),
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
