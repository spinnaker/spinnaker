import { module } from 'angular';

import { CloudProviderRegistry, DeploymentStrategyRegistry } from '@spinnaker/core';

import { GCE_LOAD_BALANCER_CHOICE_MODAL } from './loadBalancer/configure/choice/gceLoadBalancerChoice.modal';
import { GCE_INTERNAL_LOAD_BALANCER_CTRL } from './loadBalancer/configure/internal/gceCreateInternalLoadBalancer.controller';
import { GCE_SSL_LOAD_BALANCER_CTRL } from './loadBalancer/configure/ssl/gceCreateSslLoadBalancer.controller';
import { GCE_TCP_LOAD_BALANCER_CTRL } from './loadBalancer/configure/tcp/gceCreateTcpLoadBalancer.controller';
import { IAP_INTERCEPTOR } from 'google/interceptors/iap.interceptor';
import { LOAD_BALANCER_SET_TRANSFORMER } from './loadBalancer/loadBalancer.setTransformer';
import './help/gce.help';

import './logo/gce.logo.less';

// load all templates into the $templateCache
const templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

export const GOOGLE_MODULE = 'spinnaker.gce';
module(GOOGLE_MODULE, [
  LOAD_BALANCER_SET_TRANSFORMER,
  GCE_INTERNAL_LOAD_BALANCER_CTRL,
  GCE_LOAD_BALANCER_CHOICE_MODAL,
  GCE_SSL_LOAD_BALANCER_CTRL,
  GCE_TCP_LOAD_BALANCER_CTRL,
  IAP_INTERCEPTOR,
  require('./serverGroup/details/serverGroup.details.gce.module').name,
  require('./serverGroup/configure/serverGroupCommandBuilder.service').name,
  require('./serverGroup/configure/wizard/cloneServerGroup.gce.controller').name,
  require('./serverGroup/configure/serverGroup.configure.gce.module').name,
  require('./serverGroup/serverGroup.transformer').name,
  require('./pipeline/stages/bake/gceBakeStage').name,
  require('./pipeline/stages/cloneServerGroup/gceCloneServerGroupStage').name,
  require('./pipeline/stages/destroyAsg/gceDestroyAsgStage').name,
  require('./pipeline/stages/disableAsg/gceDisableAsgStage').name,
  require('./pipeline/stages/disableCluster/gceDisableClusterStage').name,
  require('./pipeline/stages/enableAsg/gceEnableAsgStage').name,
  require('./pipeline/stages/findAmi/gceFindAmiStage').name,
  require('./pipeline/stages/findImageFromTags/gceFindImageFromTagsStage').name,
  require('./pipeline/stages/resizeAsg/gceResizeAsgStage').name,
  require('./pipeline/stages/scaleDownCluster/gceScaleDownClusterStage').name,
  require('./pipeline/stages/shrinkCluster/gceShrinkClusterStage').name,
  require('./pipeline/stages/tagImage/gceTagImageStage').name,
  require('./instance/gceInstanceType.service').name,
  require('./instance/gceMultiInstanceTask.transformer').name,
  require('./instance/custom/customInstance.filter').name,
  require('./loadBalancer/loadBalancer.transformer').name,
  require('./loadBalancer/details/loadBalancerDetail.controller').name,
  require('./loadBalancer/configure/network/createLoadBalancer.controller').name,
  require('./loadBalancer/configure/http/createHttpLoadBalancer.controller').name,
  require('./instance/details/instance.details.controller').name,
  require('./securityGroup/details/securityGroupDetail.controller').name,
  require('./securityGroup/configure/createSecurityGroup.controller').name,
  require('./securityGroup/configure/editSecurityGroup.controller').name,
  require('./securityGroup/securityGroup.transformer').name,
  require('./securityGroup/securityGroup.reader').name,
  require('./subnet/subnet.renderer').name,
  require('./validation/applicationName.validator').name,
  require('./image/image.reader').name,
  require('./cache/cacheConfigurer.service').name,
  require('./common/xpnNaming.gce.service').name,
]).config(() => {
  CloudProviderRegistry.registerProvider('gce', {
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
