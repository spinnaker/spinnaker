'use strict';

const angular = require('angular');

import { CLOUD_PROVIDER_REGISTRY } from '@spinnaker/core';
import { DCOS_KEY_VALUE_DETAILS } from './common/keyValueDetails.component';
import './help/dcos.help';

require('./logo/dcos.logo.less');

// load all templates into the $templateCache
var templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

module.exports = angular
  .module('spinnaker.dcos', [
    CLOUD_PROVIDER_REGISTRY,
    DCOS_KEY_VALUE_DETAILS,
    require('./instance/details/details.dcos.module.js').name,
    require('./loadBalancer/configure/configure.dcos.module.js').name,
    require('./loadBalancer/details/details.dcos.module.js').name,
    require('./loadBalancer/transformer.js').name,
    require('./pipeline/stages/destroyAsg/dcosDestroyAsgStage.js').name,
    require('./pipeline/stages/disableAsg/dcosDisableAsgStage.js').name,
    require('./pipeline/stages/disableCluster/dcosDisableClusterStage.js').name,
    require('./pipeline/stages/findAmi/dcosFindAmiStage.js').name,
    require('./pipeline/stages/resizeAsg/dcosResizeAsgStage.js').name,
    require('./pipeline/stages/runJob/runJobStage.js').name,
    require('./pipeline/stages/scaleDownCluster/dcosScaleDownClusterStage.js').name,
    require('./pipeline/stages/shrinkCluster/dcosShrinkClusterStage.js').name,
    require('./proxy/ui.service.js').name,
    require('./serverGroup/configure/CommandBuilder.js').name,
    require('./serverGroup/configure/configure.dcos.module.js').name,
    require('./serverGroup/details/details.dcos.module.js').name,
    require('./serverGroup/transformer.js').name,
    require('./validation/applicationName.validator.js').name,
    require('./common/selectField.directive.js').name,
  ])
  .config(function(cloudProviderRegistryProvider) {
    cloudProviderRegistryProvider.registerProvider('dcos', {
      name: 'DC/OS',
      logo: {
        path: require('./logo/dcos.logo.png'),
      },
      instance: {
        detailsTemplateUrl: require('./instance/details/details.html'),
        detailsController: 'dcosInstanceDetailsController',
      },
      loadBalancer: {
        transformer: 'dcosLoadBalancerTransformer',
        detailsTemplateUrl: require('./loadBalancer/details/details.html'),
        detailsController: 'dcosLoadBalancerDetailsController',
        createLoadBalancerTemplateUrl: require('./loadBalancer/configure/wizard/createWizard.html'),
        createLoadBalancerController: 'dcosUpsertLoadBalancerController',
      },
      image: {
        reader: 'dcosImageReader',
      },
      serverGroup: {
        skipUpstreamStageCheck: true,
        transformer: 'dcosServerGroupTransformer',
        detailsTemplateUrl: require('./serverGroup/details/details.html'),
        detailsController: 'dcosServerGroupDetailsController',
        cloneServerGroupController: 'dcosCloneServerGroupController',
        cloneServerGroupTemplateUrl: require('./serverGroup/configure/wizard/wizard.html'),
        commandBuilder: 'dcosServerGroupCommandBuilder',
        configurationService: 'dcosServerGroupConfigurationService',
      },
    });
  });
