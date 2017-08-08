'use strict';

const angular = require('angular');

import { CLOUD_PROVIDER_REGISTRY } from '@spinnaker/core';
import { DCOS_KEY_VALUE_DETAILS } from './common/keyValueDetails.component';
import { DCOS_HELP } from './help/dcos.help';

require('./logo/dcos.logo.less');

// load all templates into the $templateCache
var templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

module.exports = angular.module('spinnaker.dcos', [
  CLOUD_PROVIDER_REGISTRY,
  DCOS_KEY_VALUE_DETAILS,
  DCOS_HELP,
  require('./instance/details/details.dcos.module.js'),
  require('./loadBalancer/configure/configure.dcos.module.js'),
  require('./loadBalancer/details/details.dcos.module.js'),
  require('./loadBalancer/transformer.js'),
  require('./pipeline/stages/destroyAsg/dcosDestroyAsgStage.js'),
  require('./pipeline/stages/disableAsg/dcosDisableAsgStage.js'),
  require('./pipeline/stages/disableCluster/dcosDisableClusterStage.js'),
  require('./pipeline/stages/findAmi/dcosFindAmiStage.js'),
  require('./pipeline/stages/resizeAsg/dcosResizeAsgStage.js'),
  require('./pipeline/stages/runJob/runJobStage.js'),
  require('./pipeline/stages/scaleDownCluster/dcosScaleDownClusterStage.js'),
  require('./pipeline/stages/shrinkCluster/dcosShrinkClusterStage.js'),
  require('./proxy/ui.service.js'),
  require('./serverGroup/configure/CommandBuilder.js'),
  require('./serverGroup/configure/configure.dcos.module.js'),
  require('./serverGroup/details/details.dcos.module.js'),
  require('./serverGroup/transformer.js'),
  require('./validation/applicationName.validator.js'),
  require('./common/selectField.directive.js')
])
  .config(function(cloudProviderRegistryProvider) {
    cloudProviderRegistryProvider.registerProvider('dcos', {
      name: 'DC/OS',
      logo: {
        path: require('./logo/dcos.logo.png')
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
