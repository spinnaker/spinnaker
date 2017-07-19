import { module } from 'angular';

import { CLOUD_PROVIDER_REGISTRY, DeploymentStrategyRegistry, CloudProviderRegistry } from '@spinnaker/core';

import { GCE_LOAD_BALANCER_CHOICE_MODAL } from './loadBalancer/configure/choice/gceLoadBalancerChoice.modal';
import { GCE_INTERNAL_LOAD_BALANCER_CTRL } from './loadBalancer/configure/internal/gceCreateInternalLoadBalancer.controller';
import { GCE_SSL_LOAD_BALANCER_CTRL } from './loadBalancer/configure/ssl/gceCreateSslLoadBalancer.controller';
import { GCE_TCP_LOAD_BALANCER_CTRL } from './loadBalancer/configure/tcp/gceCreateTcpLoadBalancer.controller';
import { LOAD_BALANCER_SET_TRANSFORMER } from './loadBalancer/loadBalancer.setTransformer';
import { GCE_HELP } from './help/gce.help';

import './logo/gce.logo.less';

// load all templates into the $templateCache
const templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

export const GOOGLE_MODULE = 'spinnaker.gce';
module(GOOGLE_MODULE, [
  CLOUD_PROVIDER_REGISTRY,
  LOAD_BALANCER_SET_TRANSFORMER,
  GCE_INTERNAL_LOAD_BALANCER_CTRL,
  GCE_LOAD_BALANCER_CHOICE_MODAL,
  GCE_SSL_LOAD_BALANCER_CTRL,
  GCE_TCP_LOAD_BALANCER_CTRL,
  GCE_HELP,
  require('./serverGroup/details/serverGroup.details.gce.module.js'),
  require('./serverGroup/configure/serverGroupCommandBuilder.service.js'),
  require('./serverGroup/configure/wizard/cloneServerGroup.gce.controller.js'),
  require('./serverGroup/configure/serverGroup.configure.gce.module.js'),
  require('./serverGroup/serverGroup.transformer.js'),
  require('./pipeline/stages/bake/gceBakeStage.js'),
  require('./pipeline/stages/cloneServerGroup/gceCloneServerGroupStage.js'),
  require('./pipeline/stages/destroyAsg/gceDestroyAsgStage.js'),
  require('./pipeline/stages/disableAsg/gceDisableAsgStage.js'),
  require('./pipeline/stages/disableCluster/gceDisableClusterStage.js'),
  require('./pipeline/stages/enableAsg/gceEnableAsgStage.js'),
  require('./pipeline/stages/findAmi/gceFindAmiStage.js'),
  require('./pipeline/stages/findImageFromTags/gceFindImageFromTagsStage.js'),
  require('./pipeline/stages/resizeAsg/gceResizeAsgStage.js'),
  require('./pipeline/stages/scaleDownCluster/gceScaleDownClusterStage.js'),
  require('./pipeline/stages/shrinkCluster/gceShrinkClusterStage.js'),
  require('./pipeline/stages/tagImage/gceTagImageStage.js'),
  require('./instance/gceInstanceType.service.js'),
  require('./instance/gceMultiInstanceTask.transformer.js'),
  require('./instance/custom/customInstance.filter.js'),
  require('./loadBalancer/loadBalancer.transformer.js'),
  require('./loadBalancer/details/loadBalancerDetail.controller.js'),
  require('./loadBalancer/configure/network/createLoadBalancer.controller.js'),
  require('./loadBalancer/configure/http/createHttpLoadBalancer.controller.js'),
  require('./instance/details/instance.details.controller.js'),
  require('./securityGroup/details/securityGroupDetail.controller.js'),
  require('./securityGroup/configure/createSecurityGroup.controller.js'),
  require('./securityGroup/configure/editSecurityGroup.controller.js'),
  require('./securityGroup/securityGroup.transformer.js'),
  require('./securityGroup/securityGroup.reader.js'),
  require('./subnet/subnet.renderer.js'),
  require('./validation/applicationName.validator.js'),
  require('./image/image.reader.js'),
  require('./cache/cacheConfigurer.service.js'),
  require('./common/xpnNaming.gce.service.js'),
])
  .config((cloudProviderRegistryProvider: CloudProviderRegistry) => {
    cloudProviderRegistryProvider.registerProvider('gce', {
      name: 'Google',
      logo: {
        path: require('./logo/gce.logo.png'),
      },
      cache: {
        configurer: 'gceCacheConfigurer',
      },
      image: {
        reader: 'gceImageReader',
      },
      serverGroup: {
        transformer: 'gceServerGroupTransformer',
        detailsTemplateUrl: require('./serverGroup/details/serverGroupDetails.html'),
        detailsController: 'gceServerGroupDetailsCtrl',
        cloneServerGroupTemplateUrl: require('./serverGroup/configure/wizard/serverGroupWizard.html'),
        cloneServerGroupController: 'gceCloneServerGroupCtrl',
        commandBuilder: 'gceServerGroupCommandBuilder',
        configurationService: 'gceServerGroupConfigurationService',
      },
      instance: {
        instanceTypeService: 'gceInstanceTypeService',
        detailsTemplateUrl: require('./instance/details/instanceDetails.html'),
        detailsController: 'gceInstanceDetailsCtrl',
        multiInstanceTaskTransformer: 'gceMultiInstanceTaskTransformer',
        customInstanceBuilderTemplateUrl: require('./serverGroup/configure/wizard/customInstance/customInstanceBuilder.html'),
      },
      loadBalancer: {
        transformer: 'gceLoadBalancerTransformer',
        setTransformer: 'gceLoadBalancerSetTransformer',
        detailsTemplateUrl: require('./loadBalancer/details/loadBalancerDetails.html'),
        detailsController: 'gceLoadBalancerDetailsCtrl',
        createLoadBalancerTemplateUrl: require('./loadBalancer/configure/choice/gceLoadBalancerChoice.modal.html'),
        createLoadBalancerController: 'gceLoadBalancerChoiceCtrl',
      },
      securityGroup: {
        transformer: 'gceSecurityGroupTransformer',
        reader: 'gceSecurityGroupReader',
        detailsTemplateUrl: require('./securityGroup/details/securityGroupDetail.html'),
        detailsController: 'gceSecurityGroupDetailsCtrl',
        createSecurityGroupTemplateUrl: require('./securityGroup/configure/createSecurityGroup.html'),
        createSecurityGroupController: 'gceCreateSecurityGroupCtrl',
      },
      subnet: {
        renderer: 'gceSubnetRenderer',
      },
      snapshotsEnabled: true,
      applicationProviderFields: {
        templateUrl: require('./applicationProviderFields/gceFields.html'),
      },
    });
  });

DeploymentStrategyRegistry.registerProvider('gce', ['custom', 'redblack']);
